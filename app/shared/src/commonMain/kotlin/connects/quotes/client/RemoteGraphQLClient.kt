package connects.quotes.client

import connects.quotes.config.ServerEndpointConfig
import connects.quotes.config.ServerEndpointManager
import connects.quotes.crypto.PlatformCrypto
import connects.quotes.model.*
import connects.quotes.model.ChannelHandshakeResponse
import connects.quotes.model.ChannelSecurityMode
import connects.quotes.model.EncryptedChannelPayload
import connects.quotes.model.EncryptedChannelResponse
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class GraphQLWireRequest(
    val query: String,
    val variables: Map<String, JsonElement>? = null
)

@Serializable
data class GraphQLWireResponse(
    val data: JsonElement? = null,
    val errors: List<GraphQLWireError>? = null
)

@Serializable
data class GraphQLWireError(
    val message: String
)

/**
 * Remote GraphQL Network Client for Quotes Connect.
 * Connects to any remote Quotes Connect server over HTTP/HTTPS GraphQL gateway.
 * Automatically enforces Asymmetric Channel Encryption (RSA-2048 / AES-256-GCM)
 * when operating on plain HTTP networks to protect against Man-In-The-Middle (MITM) attacks.
 */
class RemoteGraphQLClient(
    private val httpClient: HttpClient = createPlatformHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var channelSecurityMode: ChannelSecurityMode = ChannelSecurityMode.ASYM_ENCRYPTED
        private set

    private var cachedHandshake: ChannelHandshakeResponse? = null
    private var cachedHandshakeEndpoint: String? = null

    /**
     * Retrieves the ephemeral asymmetric handshake key from Ktor server.
     */
    suspend fun ensureHandshake(endpoint: ServerEndpointConfig = ServerEndpointManager.get()): ChannelHandshakeResponse {
        val currentBase = endpoint.baseUrl
        val existing = cachedHandshake
        if (existing != null && cachedHandshakeEndpoint == currentBase) {
            return existing
        }

        val handshakeUrl = "${endpoint.baseUrl}/api/channel/handshake"
        val res = httpClient.get(handshakeUrl)
        if (res.status != HttpStatusCode.OK) {
            throw IllegalStateException("Failed to obtain asymmetric channel handshake: HTTP ${res.status.value}")
        }
        val handshake = json.decodeFromString(ChannelHandshakeResponse.serializer(), res.bodyAsText())
        cachedHandshake = handshake
        cachedHandshakeEndpoint = currentBase
        return handshake
    }

    /**
     * Checks if the server at the given endpoint is online and responsive.
     */
    suspend fun checkServerAvailability(endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Boolean {
        val isHttps = endpoint.isSecureTransport
        return try {
            val response = httpClient.get(endpoint.statusUrl)
            val ok = response.status == HttpStatusCode.OK
            if (ok) {
                if (isHttps) {
                    channelSecurityMode = ChannelSecurityMode.TLS_SSL
                } else {
                    try {
                        ensureHandshake(endpoint)
                        channelSecurityMode = ChannelSecurityMode.ASYM_ENCRYPTED
                    } catch (_: Exception) {
                        channelSecurityMode = ChannelSecurityMode.ASYM_ENCRYPTED
                    }
                }
            } else {
                channelSecurityMode = ChannelSecurityMode.OFFLINE
            }
            ok
        } catch (_: Exception) {
            // Also attempt GraphQL health query as secondary check
            try {
                val gqlResponse = executeQuery(
                    query = "query { health }",
                    endpoint = endpoint
                )
                val ok = gqlResponse.data?.jsonObject?.get("health")?.jsonPrimitive?.contentOrNull == "OK"
                if (ok) {
                    channelSecurityMode = if (isHttps) ChannelSecurityMode.TLS_SSL else ChannelSecurityMode.ASYM_ENCRYPTED
                } else {
                    channelSecurityMode = ChannelSecurityMode.OFFLINE
                }
                ok
            } catch (_: Exception) {
                channelSecurityMode = ChannelSecurityMode.OFFLINE
                false
            }
        }
    }

    suspend fun executeQuery(
        query: String,
        variables: Map<String, JsonElement>? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): GraphQLWireResponse {
        val wireReq = GraphQLWireRequest(query = query, variables = variables)
        val payload = json.encodeToString(GraphQLWireRequest.serializer(), wireReq)

        val token = SessionManager.currentAccessToken

        // If channel is HTTPS / TLS secured (e.g. port 443 / SSL), use standard transport security
        if (endpoint.isSecureTransport) {
            channelSecurityMode = ChannelSecurityMode.TLS_SSL
            val response = httpClient.post(endpoint.graphqlUrl) {
                contentType(ContentType.Application.Json)
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(payload)
            }

            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText()}")
            }

            val body = response.bodyAsText()
            return json.decodeFromString(GraphQLWireResponse.serializer(), body)
        }

        // Insecure HTTP network detected -> encrypt the entire channel with Asymmetric Hybrid Encryption
        return try {
            val handshake = ensureHandshake(endpoint)
            val (aesKey, envelope) = PlatformCrypto.encryptChannel(
                plainText = payload,
                serverPublicKeyBase64 = handshake.serverPublicKey,
                keyId = handshake.keyId
            )
            val envelopeJson = json.encodeToString(EncryptedChannelPayload.serializer(), envelope)

            val response = httpClient.post(endpoint.graphqlUrl) {
                contentType(ContentType.Application.Json)
                header("X-Encrypted-Channel", "true")
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(envelopeJson)
            }

            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText()}")
            }

            val body = response.bodyAsText()
            val decryptedBody = if (response.headers["X-Encrypted-Channel"]?.equals("true", ignoreCase = true) == true ||
                body.contains("\"cipherText\"")) {
                val encryptedResp = json.decodeFromString(EncryptedChannelResponse.serializer(), body)
                PlatformCrypto.decryptChannel(
                    cipherTextBase64 = encryptedResp.cipherText,
                    ivBase64 = encryptedResp.iv,
                    aesKey = aesKey,
                    aad = "${handshake.keyId}:${encryptedResp.requestId}"
                )
            } else {
                body
            }

            channelSecurityMode = ChannelSecurityMode.ASYM_ENCRYPTED
            json.decodeFromString(GraphQLWireResponse.serializer(), decryptedBody)
        } catch (e: Exception) {
            throw IllegalStateException("Encrypted channel operation failed", e)
        }
    }

    suspend fun login(
        authorId: String,
        passphrase: String,
        deviceId: String? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): connects.quotes.security.AuthSession {
        val url = "${endpoint.baseUrl}/api/auth/login"
        val bodyMap = buildJsonObject {
            put("authorId", authorId)
            put("passphrase", passphrase)
            if (deviceId != null) put("deviceId", deviceId)
        }
        val res = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(bodyMap.toString())
        }
        if (res.status != HttpStatusCode.OK) {
            throw IllegalStateException("Login failed: HTTP ${res.status.value}: ${res.bodyAsText()}")
        }
        return json.decodeFromString(connects.quotes.security.AuthSession.serializer(), res.bodyAsText())
    }

    suspend fun logout(
        token: String? = SessionManager.currentAccessToken,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ) {
        val url = "${endpoint.baseUrl}/api/auth/logout"
        try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun getBillboard(tag: String? = null, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<BillboardItem> {
        val query = """
            query GetBillboard(${'$'}tag: String) {
                billboard(tag: ${'$'}tag) {
                    isOrphan
                    advertisedAt
                    passage {
                        id
                        type
                        content
                        tags
                        isRecordable
                        subscriberIds
                        subscribedToIds
                        creationTimestamp
                    }
                }
            }
        """.trimIndent()

        val vars = if (tag != null) mapOf("tag" to JsonPrimitive(tag)) else null
        val response = executeQuery(query, vars, endpoint)
        val billboardJson = response.data?.jsonObject?.get("billboard") ?: return emptyList()
        return json.decodeFromJsonElement<List<BillboardItem>>(billboardJson)
    }

    suspend fun getFeed(
        authorId: Id,
        depth: Int = 1,
        limit: Int = 50,
        offset: Int = 0,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): List<Passage> {
        val query = """
            query GetFeed(${'$'}authorId: String!, ${'$'}depth: Int, ${'$'}limit: Int, ${'$'}offset: Int) {
                feed(authorId: ${'$'}authorId, depth: ${'$'}depth, limit: ${'$'}limit, offset: ${'$'}offset) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf(
            "authorId" to JsonPrimitive(authorId.value),
            "depth" to JsonPrimitive(depth),
            "limit" to JsonPrimitive(limit),
            "offset" to JsonPrimitive(offset)
        )
        val response = executeQuery(query, vars, endpoint)
        val feedJson = response.data?.jsonObject?.get("feed") ?: return emptyList()
        return json.decodeFromJsonElement<List<Passage>>(feedJson)
    }

    suspend fun getPassage(id: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Passage? {
        val query = """
            query GetPassage(${'$'}id: String!) {
                passage(id: ${'$'}id) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf("id" to JsonPrimitive(id.value))
        val response = executeQuery(query, vars, endpoint)
        val passageJson = response.data?.jsonObject?.get("passage")
        if (passageJson == null || passageJson is JsonNull) return null
        return json.decodeFromJsonElement<Passage>(passageJson)
    }

    suspend fun getSubscribers(id: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Passage> {
        val query = """
            query GetSubscribers(${'$'}id: String!) {
                subscribers(id: ${'$'}id) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf("id" to JsonPrimitive(id.value))
        val response = executeQuery(query, vars, endpoint)
        val subJson = response.data?.jsonObject?.get("subscribers") ?: return emptyList()
        return json.decodeFromJsonElement<List<Passage>>(subJson)
    }

    suspend fun getSubscribedTo(id: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Passage> {
        val query = """
            query GetSubscribedTo(${'$'}id: String!) {
                subscribedTo(id: ${'$'}id) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf("id" to JsonPrimitive(id.value))
        val response = executeQuery(query, vars, endpoint)
        val subJson = response.data?.jsonObject?.get("subscribedTo") ?: return emptyList()
        return json.decodeFromJsonElement<List<Passage>>(subJson)
    }

    suspend fun getDescendantTree(rootId: Id, maxDepth: Int = 4, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): connects.quotes.graph.GraphEngine.TreeNode? {
        val query = """
            query GetDescendantTree(${'$'}rootId: String!, ${'$'}maxDepth: Int) {
                descendantTree(rootId: ${'$'}rootId, maxDepth: ${'$'}maxDepth)
            }
        """.trimIndent()

        val vars = mapOf(
            "rootId" to JsonPrimitive(rootId.value),
            "maxDepth" to JsonPrimitive(maxDepth)
        )
        return try {
            val response = executeQuery(query, vars, endpoint)
            val treeJson = response.data?.jsonObject?.get("descendantTree")
            if (treeJson == null || treeJson is JsonNull) null
            else json.decodeFromJsonElement<connects.quotes.graph.GraphEngine.TreeNode>(treeJson)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getTopTags(endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Tag> {
        val query = """
            query GetTags {
                tags(limit: 30) {
                    name
                    count
                }
            }
        """.trimIndent()

        val response = executeQuery(query, null, endpoint)
        val tagsJson = response.data?.jsonObject?.get("tags") ?: return emptyList()
        return json.decodeFromJsonElement<List<Tag>>(tagsJson)
    }

    suspend fun suggestTags(search: String, limit: Int = 10, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Tag> {
        val query = """
            query SuggestTags(${'$'}query: String, ${'$'}limit: Int) {
                tags(query: ${'$'}query, limit: ${'$'}limit) {
                    name
                    count
                }
            }
        """.trimIndent()

        val vars = buildJsonObject {
            put("query", search)
            put("limit", limit)
        }

        val response = executeQuery(query, vars, endpoint)
        val tagsJson = response.data?.jsonObject?.get("tags") ?: return emptyList()
        return json.decodeFromJsonElement<List<Tag>>(tagsJson)
    }

    suspend fun getNotifications(authorId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Notification> {
        val query = """
            query GetNotifications(${'$'}authorId: String!) {
                notifications(authorId: ${'$'}authorId) {
                    id
                    targetPassageId
                    subscriberPassageId
                    subscriberType
                    summary
                    createdAt
                    isRead
                }
            }
        """.trimIndent()

        val vars = mapOf("authorId" to JsonPrimitive(authorId.value))
        val response = executeQuery(query, vars, endpoint)
        val notifsJson = response.data?.jsonObject?.get("notifications") ?: return emptyList()
        return json.decodeFromJsonElement<List<Notification>>(notifsJson)
    }

    suspend fun search(queryText: String, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Passage> {
        val query = """
            query SearchPassages(${'$'}query: String!) {
                search(query: ${'$'}query, limit: 30) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf("query" to JsonPrimitive(queryText))
        val response = executeQuery(query, vars, endpoint)
        val searchJson = response.data?.jsonObject?.get("search") ?: return emptyList()
        return json.decodeFromJsonElement<List<Passage>>(searchJson)
    }

    suspend fun getCreatedPassages(authorId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<Passage> {
        val query = """
            query GetCreatedPassages(${'$'}authorId: String!) {
                createdPassages(authorId: ${'$'}authorId) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()

        val vars = mapOf("authorId" to JsonPrimitive(authorId.value))
        val response = executeQuery(query, vars, endpoint)
        val createdJson = response.data?.jsonObject?.get("createdPassages") ?: return emptyList()
        return json.decodeFromJsonElement<List<Passage>>(createdJson)
    }

    suspend fun createIdentity(
        moniker: String,
        shouldAdvertise: Boolean = false,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Credentials {
        val mutation = """
            mutation CreateIdentity(${'$'}moniker: String, ${'$'}shouldAdvertise: Boolean) {
                createIdentity(moniker: ${'$'}moniker, shouldAdvertise: ${'$'}shouldAdvertise) {
                    passageId
                    passphrase
                }
            }
        """.trimIndent()

        val vars = mapOf(
            "moniker" to JsonPrimitive(moniker),
            "shouldAdvertise" to JsonPrimitive(shouldAdvertise)
        )
        val response = executeQuery(mutation, vars, endpoint)
        val identityJson = response.data?.jsonObject?.get("createIdentity")?.jsonObject
            ?: throw IllegalStateException("Failed to create identity: ${response.errors?.firstOrNull()?.message}")

        val passageId = identityJson["passageId"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing passageId")
        val passphrase = identityJson["passphrase"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing passphrase")
        return Credentials(Id(passageId), passphrase)
    }

    suspend fun createPassage(
        type: PassageType,
        content: String,
        tags: List<String>,
        parentId: Id?,
        isRecordable: Boolean,
        shouldAdvertise: Boolean,
        subscribeToId: Id?,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Passage> {
        return try {
            val mutation = """
                mutation CreatePassage(
                    ${'$'}type: String!,
                    ${'$'}content: String!,
                    ${'$'}tags: [String!],
                    ${'$'}parentId: String,
                    ${'$'}isRecordable: Boolean,
                    ${'$'}shouldAdvertise: Boolean,
                    ${'$'}subscribeToId: String
                ) {
                    createPassage(
                        type: ${'$'}type,
                        content: ${'$'}content,
                        tags: ${'$'}tags,
                        parentId: ${'$'}parentId,
                        isRecordable: ${'$'}isRecordable,
                        shouldAdvertise: ${'$'}shouldAdvertise,
                        subscribeToId: ${'$'}subscribeToId
                    ) {
                        id
                        type
                        content
                        tags
                        isRecordable
                        subscriberIds
                        subscribedToIds
                        creationTimestamp
                    }
                }
            """.trimIndent()

            val vars = mutableMapOf<String, JsonElement>(
                "type" to JsonPrimitive(type.name),
                "content" to JsonPrimitive(content),
                "tags" to JsonArray(tags.map { JsonPrimitive(it) }),
                "isRecordable" to JsonPrimitive(isRecordable),
                "shouldAdvertise" to JsonPrimitive(shouldAdvertise)
            )
            if (parentId != null) vars["parentId"] = JsonPrimitive(parentId.value)
            if (subscribeToId != null) vars["subscribeToId"] = JsonPrimitive(subscribeToId.value)

            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val passageJson = response.data?.jsonObject?.get("createPassage")
                    ?: return Result.failure(IllegalStateException("Empty createPassage response"))
                val passage = json.decodeFromJsonElement<Passage>(passageJson)
                Result.success(passage)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleAdvertisement(passageId: Id, authorId: Id?, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Result<Boolean> {
        return try {
            val mutation = """
                mutation ToggleBillboard(${'$'}passageId: String!, ${'$'}authorId: String) {
                    toggleBillboard(passageId: ${'$'}passageId, authorId: ${'$'}authorId)
                }
            """.trimIndent()

            val vars = mutableMapOf<String, JsonElement>("passageId" to JsonPrimitive(passageId.value))
            if (authorId != null) vars["authorId"] = JsonPrimitive(authorId.value)

            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val res = response.data?.jsonObject?.get("toggleBillboard")?.jsonPrimitive?.booleanOrNull ?: true
                Result.success(res)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isOrphan(id: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Boolean {
        return try {
            val query = """
                query IsOrphan(${'$'}id: String!) {
                    isOrphan(id: ${'$'}id)
                }
            """.trimIndent()
            val response = executeQuery(query, mapOf("id" to JsonPrimitive(id.value)), endpoint)
            response.data?.jsonObject?.get("isOrphan")?.jsonPrimitive?.booleanOrNull ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isAdvertised(id: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Boolean {
        return try {
            val query = """
                query IsAdvertised(${'$'}id: String!) {
                    isAdvertised(id: ${'$'}id)
                }
            """.trimIndent()
            val response = executeQuery(query, mapOf("id" to JsonPrimitive(id.value)), endpoint)
            response.data?.jsonObject?.get("isAdvertised")?.jsonPrimitive?.booleanOrNull ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAdvertisedPassageIds(authorId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Set<String> {
        return try {
            val query = """
                query GetAdvertisedPassageIds(${'$'}authorId: String!) {
                    advertisedPassageIds(authorId: ${'$'}authorId)
                }
            """.trimIndent()
            val response = executeQuery(query, mapOf("authorId" to JsonPrimitive(authorId.value)), endpoint)
            val jsonArr = response.data?.jsonObject?.get("advertisedPassageIds")?.jsonArray ?: return emptySet()
            jsonArr.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun setPassageTypeAdvertisement(
        authorId: Id,
        type: String?,
        advertise: Boolean,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Int> {
        return try {
            val mutation = """
                mutation SetPassageTypeAdv(${'$'}authorId: String!, ${'$'}type: String, ${'$'}advertise: Boolean!) {
                    setPassageTypeAdvertisement(authorId: ${'$'}authorId, type: ${'$'}type, advertise: ${'$'}advertise)
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>(
                "authorId" to JsonPrimitive(authorId.value),
                "advertise" to JsonPrimitive(advertise)
            )
            if (type != null) vars["type"] = JsonPrimitive(type)

            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val count = response.data?.jsonObject?.get("setPassageTypeAdvertisement")?.jsonPrimitive?.intOrNull ?: 0
                Result.success(count)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassphrase(
        authorId: Id,
        oldPassphrase: String,
        newPassphrase: String,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation ChangePassphrase(${'$'}authorId: String!, ${'$'}oldPassphrase: String!, ${'$'}newPassphrase: String!) {
                    changePassphrase(authorId: ${'$'}authorId, oldPassphrase: ${'$'}oldPassphrase, newPassphrase: ${'$'}newPassphrase)
                }
            """.trimIndent()
            val vars = mapOf(
                "authorId" to JsonPrimitive(authorId.value),
                "oldPassphrase" to JsonPrimitive(oldPassphrase),
                "newPassphrase" to JsonPrimitive(newPassphrase)
            )
            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val success = response.data?.jsonObject?.get("changePassphrase")?.jsonPrimitive?.booleanOrNull ?: false
                if (success) Result.success(true) else Result.failure(IllegalStateException("Invalid current passphrase or update failed."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChats(authorId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<ChatSession> {
        val query = """
            query GetChats(${'$'}authorId: String!) {
                chats(authorId: ${'$'}authorId) {
                    id
                    initiatorId
                    recipientId
                    type
                    status
                    initiatorPublicKey
                    recipientPublicKey
                    sharedAlias
                    proposedSharedAlias
                    aliasProposerId
                    createdAt
                    updatedAt
                }
            }
        """.trimIndent()
        val response = executeQuery(query, mapOf("authorId" to JsonPrimitive(authorId.value)), endpoint)
        val arr = response.data?.jsonObject?.get("chats")?.jsonArray ?: return emptyList()
        return arr.map { json.decodeFromJsonElement(ChatSession.serializer(), it) }
    }

    suspend fun getChat(chatId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): ChatSession? {
        val query = """
            query GetChat(${'$'}chatId: String!) {
                chat(chatId: ${'$'}chatId) {
                    id
                    initiatorId
                    recipientId
                    type
                    status
                    initiatorPublicKey
                    recipientPublicKey
                    sharedAlias
                    proposedSharedAlias
                    aliasProposerId
                    createdAt
                    updatedAt
                }
            }
        """.trimIndent()
        val response = executeQuery(query, mapOf("chatId" to JsonPrimitive(chatId.value)), endpoint)
        val obj = response.data?.jsonObject?.get("chat") ?: return null
        if (obj is JsonNull) return null
        return json.decodeFromJsonElement(ChatSession.serializer(), obj)
    }

    suspend fun getChatMessages(chatId: Id, authorId: Id, endpoint: ServerEndpointConfig = ServerEndpointManager.get()): List<ChatMessage> {
        val query = """
            query GetChatMessages(${'$'}chatId: String!, ${'$'}authorId: String!) {
                chatMessages(chatId: ${'$'}chatId, authorId: ${'$'}authorId) {
                    id
                    chatId
                    senderId
                    recipientId
                    content
                    isEncrypted
                    citedPassageId
                    timestamp
                }
            }
        """.trimIndent()
        val vars = mapOf("chatId" to JsonPrimitive(chatId.value), "authorId" to JsonPrimitive(authorId.value))
        val response = executeQuery(query, vars, endpoint)
        val arr = response.data?.jsonObject?.get("chatMessages")?.jsonArray ?: return emptyList()
        return arr.map { json.decodeFromJsonElement(ChatMessage.serializer(), it) }
    }

    suspend fun requestChat(
        initiatorId: Id,
        recipientId: Id,
        type: ChatType,
        initiatorPublicKey: String? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<ChatSession> {
        return try {
            val mutation = """
                mutation RequestChat(${'$'}initiatorId: String!, ${'$'}recipientId: String!, ${'$'}type: String, ${'$'}initiatorPublicKey: String) {
                    requestChat(initiatorId: ${'$'}initiatorId, recipientId: ${'$'}recipientId, type: ${'$'}type, initiatorPublicKey: ${'$'}initiatorPublicKey) {
                        id
                        initiatorId
                        recipientId
                        type
                        status
                        initiatorPublicKey
                        recipientPublicKey
                        sharedAlias
                        proposedSharedAlias
                        aliasProposerId
                        createdAt
                        updatedAt
                    }
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>(
                "initiatorId" to JsonPrimitive(initiatorId.value),
                "recipientId" to JsonPrimitive(recipientId.value),
                "type" to JsonPrimitive(type.name)
            )
            if (initiatorPublicKey != null) {
                vars["initiatorPublicKey"] = JsonPrimitive(initiatorPublicKey)
            }
            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val obj = response.data?.jsonObject?.get("requestChat")
                    ?: return Result.failure(IllegalStateException("No response from requestChat"))
                Result.success(json.decodeFromJsonElement(ChatSession.serializer(), obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondChat(
        chatId: Id,
        authorId: Id,
        accept: Boolean,
        recipientPublicKey: String? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<ChatSession> {
        return try {
            val mutation = """
                mutation RespondChat(${'$'}chatId: String!, ${'$'}authorId: String!, ${'$'}accept: Boolean!, ${'$'}recipientPublicKey: String) {
                    respondChat(chatId: ${'$'}chatId, authorId: ${'$'}authorId, accept: ${'$'}accept, recipientPublicKey: ${'$'}recipientPublicKey) {
                        id
                        initiatorId
                        recipientId
                        type
                        status
                        initiatorPublicKey
                        recipientPublicKey
                        sharedAlias
                        proposedSharedAlias
                        aliasProposerId
                        createdAt
                        updatedAt
                    }
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value),
                "accept" to JsonPrimitive(accept)
            )
            if (recipientPublicKey != null) {
                vars["recipientPublicKey"] = JsonPrimitive(recipientPublicKey)
            }
            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val obj = response.data?.jsonObject?.get("respondChat")
                    ?: return Result.failure(IllegalStateException("No response from respondChat"))
                Result.success(json.decodeFromJsonElement(ChatSession.serializer(), obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatMessage(
        chatId: Id,
        senderId: Id,
        recipientId: Id,
        content: String,
        isEncrypted: Boolean,
        citedPassageId: Id? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<ChatMessage> {
        return try {
            val mutation = """
                mutation SendChatMessage(${'$'}chatId: String!, ${'$'}senderId: String!, ${'$'}recipientId: String!, ${'$'}content: String!, ${'$'}isEncrypted: Boolean, ${'$'}citedPassageId: String) {
                    sendChatMessage(chatId: ${'$'}chatId, senderId: ${'$'}senderId, recipientId: ${'$'}recipientId, content: ${'$'}content, isEncrypted: ${'$'}isEncrypted, citedPassageId: ${'$'}citedPassageId) {
                        id
                        chatId
                        senderId
                        recipientId
                        content
                        isEncrypted
                        citedPassageId
                        timestamp
                    }
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>(
                "chatId" to JsonPrimitive(chatId.value),
                "senderId" to JsonPrimitive(senderId.value),
                "recipientId" to JsonPrimitive(recipientId.value),
                "content" to JsonPrimitive(content),
                "isEncrypted" to JsonPrimitive(isEncrypted)
            )
            if (citedPassageId != null) {
                vars["citedPassageId"] = JsonPrimitive(citedPassageId.value)
            }
            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val obj = response.data?.jsonObject?.get("sendChatMessage")
                    ?: return Result.failure(IllegalStateException("No response from sendChatMessage"))
                Result.success(json.decodeFromJsonElement(ChatMessage.serializer(), obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renewChatKey(
        chatId: Id,
        authorId: Id,
        newPublicKey: String,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<ChatSession> {
        return try {
            val mutation = """
                mutation RenewChatKey(${'$'}chatId: String!, ${'$'}authorId: String!, ${'$'}newPublicKey: String!) {
                    renewChatKey(chatId: ${'$'}chatId, authorId: ${'$'}authorId, newPublicKey: ${'$'}newPublicKey) {
                        id
                        initiatorId
                        recipientId
                        type
                        status
                        initiatorPublicKey
                        recipientPublicKey
                        sharedAlias
                        proposedSharedAlias
                        aliasProposerId
                        createdAt
                        updatedAt
                    }
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value),
                "newPublicKey" to JsonPrimitive(newPublicKey)
            )
            val response = executeQuery(mutation, vars, endpoint)
            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(IllegalStateException(response.errors.first().message))
            } else {
                val obj = response.data?.jsonObject?.get("renewChatKey")
                    ?: return Result.failure(IllegalStateException("No response from renewChatKey"))
                Result.success(json.decodeFromJsonElement(ChatSession.serializer(), obj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun proposeChatAlias(
        chatId: Id,
        authorId: Id,
        alias: String,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation ProposeChatAlias(${'$'}chatId: String!, ${'$'}authorId: String!, ${'$'}alias: String!) {
                    proposeChatAlias(chatId: ${'$'}chatId, authorId: ${'$'}authorId, alias: ${'$'}alias)
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value),
                "alias" to JsonPrimitive(alias)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("proposeChatAlias")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptChatAlias(
        chatId: Id,
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation AcceptChatAlias(${'$'}chatId: String!, ${'$'}authorId: String!) {
                    acceptChatAlias(chatId: ${'$'}chatId, authorId: ${'$'}authorId)
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("acceptChatAlias")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamLiveChat(
        chatId: Id,
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Flow<LiveChatPacket> = flow {
        val url = "${endpoint.baseUrl}/api/chat/live?chatId=${chatId.value}&authorId=${authorId.value}"
        try {
            httpClient.prepareGet(url) {
                headers.append(HttpHeaders.Accept, "text/event-stream")
                val token = SessionManager.currentAccessToken
                if (!token.isNullOrBlank()) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
                timeout {
                    requestTimeoutMillis = Long.MAX_VALUE
                    socketTimeoutMillis = Long.MAX_VALUE
                }
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data:")) {
                        val jsonStr = line.substring(5).trim()
                        if (jsonStr.isNotBlank()) {
                            try {
                                val packet = json.decodeFromString<LiveChatPacket>(jsonStr)
                                emit(packet)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Stream disconnected
        }
    }

    fun streamNotifications(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Flow<LiveChatPacket> = flow {
        val url = "${endpoint.baseUrl}/api/chat/notifications?authorId=${authorId.value}"
        try {
            httpClient.prepareGet(url) {
                headers.append(HttpHeaders.Accept, "text/event-stream")
                val token = SessionManager.currentAccessToken
                if (!token.isNullOrBlank()) {
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
                timeout {
                    requestTimeoutMillis = Long.MAX_VALUE
                    socketTimeoutMillis = Long.MAX_VALUE
                }
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data:")) {
                        val jsonStr = line.substring(5).trim()
                        if (jsonStr.isNotBlank()) {
                            try {
                                val packet = json.decodeFromString<LiveChatPacket>(jsonStr)
                                emit(packet)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Stream disconnected
        }
    }

    suspend fun getKeyStore(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<KeyStorePayload?> {
        return try {
            val query = """
                query GetKeyStore(${'$'}authorId: String!) {
                    keystore(authorId: ${'$'}authorId) {
                        authorId
                        encryptedPayload
                        salt
                        iv
                        version
                        updatedAt
                    }
                }
            """.trimIndent()
            val vars = mapOf("authorId" to JsonPrimitive(authorId.value))
            val response = executeQuery(query, vars, endpoint)
            val ksElem = response.data?.jsonObject?.get("keystore")
            if (ksElem != null && ksElem !is JsonNull) {
                Result.success(json.decodeFromJsonElement(KeyStorePayload.serializer(), ksElem))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun backupKeyStore(
        keyStore: KeyStorePayload,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation BackupKeyStore(${'$'}authorId: String!, ${'$'}encryptedPayload: String!, ${'$'}salt: String!, ${'$'}iv: String!, ${'$'}version: Int) {
                    backupKeyStore(authorId: ${'$'}authorId, encryptedPayload: ${'$'}encryptedPayload, salt: ${'$'}salt, iv: ${'$'}iv, version: ${'$'}version)
                }
            """.trimIndent()
            val vars = mapOf(
                "authorId" to JsonPrimitive(keyStore.authorId.value),
                "encryptedPayload" to JsonPrimitive(keyStore.encryptedPayload),
                "salt" to JsonPrimitive(keyStore.salt),
                "iv" to JsonPrimitive(keyStore.iv),
                "version" to JsonPrimitive(keyStore.version)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("backupKeyStore")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteKeyStore(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation DeleteKeyStore(${'$'}authorId: String!) {
                    deleteKeyStore(authorId: ${'$'}authorId)
                }
            """.trimIndent()
            val vars = mapOf("authorId" to JsonPrimitive(authorId.value))
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("deleteKeyStore")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    
    suspend fun sendKeyExchangePacket(
        packet: connects.quotes.model.KeyExchangePacket,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Unit> {
        return try {
            val relayUrl = "${endpoint.baseUrl}/api/chat/key-relay/packet"
            val token = SessionManager.currentAccessToken
                ?: throw IllegalStateException("No active session")
            val requestBody = json.encodeToString(packet)
            val response = httpClient.post(relayUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(requestBody)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to relay packet: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChat(
        chatId: Id,
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation DeleteChat(${'$'}chatId: String!, ${'$'}authorId: String!) {
                    deleteChat(chatId: ${'$'}chatId, authorId: ${'$'}authorId)
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("deleteChat")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTyping(
        chatId: Id,
        authorId: Id,
        isTyping: Boolean,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation SetTyping(${'$'}chatId: String!, ${'$'}authorId: String!, ${'$'}isTyping: Boolean!) {
                    setTyping(chatId: ${'$'}chatId, authorId: ${'$'}authorId, isTyping: ${'$'}isTyping)
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "authorId" to JsonPrimitive(authorId.value),
                "isTyping" to JsonPrimitive(isTyping)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("setTyping")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestChatResync(
        chatId: Id,
        requesterId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation RequestChatResync(${'$'}chatId: String!, ${'$'}requesterId: String!) {
                    requestChatResync(chatId: ${'$'}chatId, requesterId: ${'$'}requesterId)
                }
            """.trimIndent()
            val vars = mapOf(
                "chatId" to JsonPrimitive(chatId.value),
                "requesterId" to JsonPrimitive(requesterId.value)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("requestChatResync")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSubscribed(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): List<Passage> {
        val query = """
            query GetSubscribed(${'$'}authorId: String!) {
                subscribed(authorId: ${'$'}authorId) {
                    id
                    type
                    content
                    tags
                    isRecordable
                    subscriberIds
                    subscribedToIds
                    creationTimestamp
                }
            }
        """.trimIndent()
        val response = executeQuery(query, mapOf("authorId" to JsonPrimitive(authorId.value)), endpoint)
        val arr = response.data?.jsonObject?.get("subscribed")?.jsonArray ?: return emptyList()
        return arr.map { json.decodeFromJsonElement(Passage.serializer(), it) }
    }

    suspend fun subscribeAuthorPassage(
        authorId: Id,
        passageId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation SubscribeAuthorPassage(${'$'}authorId: String!, ${'$'}passageId: String!) {
                    subscribeAuthorPassage(authorId: ${'$'}authorId, passageId: ${'$'}passageId)
                }
            """.trimIndent()
            val vars = mapOf(
                "authorId" to JsonPrimitive(authorId.value),
                "passageId" to JsonPrimitive(passageId.value)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("subscribeAuthorPassage")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsubscribeAuthorPassage(
        authorId: Id,
        passageId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation UnsubscribeAuthorPassage(${'$'}authorId: String!, ${'$'}passageId: String!) {
                    unsubscribeAuthorPassage(authorId: ${'$'}authorId, passageId: ${'$'}passageId)
                }
            """.trimIndent()
            val vars = mapOf(
                "authorId" to JsonPrimitive(authorId.value),
                "passageId" to JsonPrimitive(passageId.value)
            )
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("unsubscribeAuthorPassage")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAuthorSettings(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<AuthorSettings?> {
        return try {
            val query = """
                query GetAuthorSettings(${'$'}authorId: String!) {
                    authorSettings(authorId: ${'$'}authorId) {
                        authorId
                        username
                        bio
                        aliasSearchEnabled
                        lastActiveAt
                        inactivityPeriodDays
                        discardAction
                        updatedAt
                    }
                }
            """.trimIndent()
            val response = executeQuery(query, mapOf("authorId" to JsonPrimitive(authorId.value)), endpoint)
            val obj = response.data?.jsonObject?.get("authorSettings") ?: return Result.success(null)
            if (obj is JsonNull) return Result.success(null)
            val settings = json.decodeFromJsonElement(AuthorSettings.serializer(), obj)
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAuthorSettings(
        authorId: Id,
        inactivityPeriodDays: Int,
        discardAction: AuthorDiscardAction,
        username: String? = null,
        bio: String? = null,
        aliasSearchEnabled: Boolean? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<AuthorSettings> {
        return try {
            val mutation = """
                mutation UpdateAuthorSettings(${'$'}authorId: String!, ${'$'}inactivityPeriodDays: Int!, ${'$'}discardAction: String!, ${'$'}username: String, ${'$'}bio: String, ${'$'}aliasSearchEnabled: Boolean) {
                    updateAuthorSettings(authorId: ${'$'}authorId, inactivityPeriodDays: ${'$'}inactivityPeriodDays, discardAction: ${'$'}discardAction, username: ${'$'}username, bio: ${'$'}bio, aliasSearchEnabled: ${'$'}aliasSearchEnabled) {
                        authorId
                        username
                        bio
                        aliasSearchEnabled
                        lastActiveAt
                        inactivityPeriodDays
                        discardAction
                        updatedAt
                    }
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>(
                "authorId" to JsonPrimitive(authorId.value),
                "inactivityPeriodDays" to JsonPrimitive(inactivityPeriodDays),
                "discardAction" to JsonPrimitive(discardAction.name)
            )
            if (username != null) vars["username"] = JsonPrimitive(username)
            if (bio != null) vars["bio"] = JsonPrimitive(bio)
            if (aliasSearchEnabled != null) vars["aliasSearchEnabled"] = JsonPrimitive(aliasSearchEnabled)
            val response = executeQuery(mutation, vars, endpoint)
            val obj = response.data?.jsonObject?.get("updateAuthorSettings") ?: throw Exception("Failed to update author settings")
            val settings = json.decodeFromJsonElement(AuthorSettings.serializer(), obj)
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pingAuthorActive(
        authorId: Id,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation PingAuthorActive(${'$'}authorId: String!) {
                    pingAuthorActive(authorId: ${'$'}authorId)
                }
            """.trimIndent()
            val response = executeQuery(mutation, mapOf("authorId" to JsonPrimitive(authorId.value)), endpoint)
            val ok = response.data?.jsonObject?.get("pingAuthorActive")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun performInactivityCheck(
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<InactivityCheckResult> {
        return try {
            val mutation = """
                mutation PerformInactivityCheck {
                    performInactivityCheck {
                        purgedAuthorsCount
                        deletedChatsCount
                        orphanedPassagesCount
                        deletedPassagesCount
                        processedAuthorIds
                        timestamp
                    }
                }
            """.trimIndent()
            val response = executeQuery(mutation, emptyMap(), endpoint)
            val obj = response.data?.jsonObject?.get("performInactivityCheck") ?: throw Exception("Failed to perform inactivity check")
            val result = json.decodeFromJsonElement(InactivityCheckResult.serializer(), obj)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun discardAuthor(
        authorId: Id,
        action: AuthorDiscardAction? = null,
        endpoint: ServerEndpointConfig = ServerEndpointManager.get()
    ): Result<Boolean> {
        return try {
            val mutation = """
                mutation DiscardAuthor(${'$'}authorId: String!, ${'$'}action: String) {
                    discardAuthor(authorId: ${'$'}authorId, action: ${'$'}action)
                }
            """.trimIndent()
            val vars = mutableMapOf<String, JsonElement>("authorId" to JsonPrimitive(authorId.value))
            if (action != null) vars["action"] = JsonPrimitive(action.name)
            val response = executeQuery(mutation, vars, endpoint)
            val ok = response.data?.jsonObject?.get("discardAuthor")?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
