package connects.quotes

import connects.quotes.config.ServerConfig
import connects.quotes.database.DatabaseConfig
import connects.quotes.database.DatabaseMode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ApplicationTest {

    private val testConfig = ServerConfig(
        port = 8080,
        host = "localhost",
        databaseConfig = DatabaseConfig(
            mode = DatabaseMode.DEMO,
            sqliteFilePath = "build/test_quotes_connect.db"
        )
    )

    private suspend fun io.ktor.client.HttpClient.createTestAuthor(moniker: String): Pair<String, String> {
        val res = post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"$moniker\") { passageId passphrase token } }"}""")
        }
        val data = Json.parseToJsonElement(res.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject
        val passageId = data["passageId"]!!.jsonPrimitive.content
        val token = data["token"]!!.jsonPrimitive.content
        return passageId to token
    }

    @Test
    fun testRootEndpoint() = testApplication {
        application {
            module(testConfig)
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("QUOTES CONNECT"))

        val statusResponse = client.get("/status")
        assertEquals(HttpStatusCode.OK, statusResponse.status)
        assertTrue(statusResponse.bodyAsText().contains("Quotes Connect Knowledge Network"))
    }

    @Test
    fun testGraphQLBillboardQuery() = testApplication {
        application {
            module(testConfig)
        }
        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { billboard { isOrphan passage { id type content tags } } }"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("billboard"))
        assertFalse(body.contains("parent_id")) // Privacy guarantee: parentId is never leaked
    }

    @Test
    fun testGraphQLCreatePassageMutation() = testApplication {
        application {
            module(testConfig)
        }
        val mutation = """
            mutation {
                createPassage(
                    type: "QUOTE",
                    content: "The unexamined life is not worth living.",
                    tags: ["socrates", "examination"]
                ) {
                    id
                    type
                    content
                    tags
                }
            }
        """.trimIndent()

        val requestBody = kotlinx.serialization.json.Json.encodeToString(
            connects.quotes.graphql.GraphQLRequest(query = mutation)
        )

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("The unexamined life is not worth living."))
        assertFalse(body.contains("parentId"))
    }

    @Test
    fun testGraphQLHealthAndIntrospection() = testApplication {
        application {
            module(testConfig)
        }
        // Test Health Query
        val healthResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { health }"}""")
        }
        assertEquals(HttpStatusCode.OK, healthResponse.status)
        val healthBody = healthResponse.bodyAsText()
        assertTrue(healthBody.contains("health") && healthBody.contains("OK"))

        // Test Introspection Query
        val introResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { __schema { queryType { name } } }"}""")
        }
        assertEquals(HttpStatusCode.OK, introResponse.status)
        assertTrue(introResponse.bodyAsText().contains("__schema"))
    }

    @Test
    fun testGraphQLVariablesResolution() = testApplication {
        application {
            module(testConfig)
        }
        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{
                "query": "query GetBillboard(${'$'}myTag: String, ${'$'}myLimit: Int) { billboard(tag: ${'$'}myTag, limit: ${'$'}myLimit) { isOrphan passage { id } } }",
                "variables": {
                    "myTag": "stoicism",
                    "myLimit": 5
                }
            }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("billboard"))
    }

    @Test
    fun testGraphQLChangePassphrase() = testApplication {
        application {
            module(testConfig)
        }
        // 1. Create Identity
        val createRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"Aristotle\") { passageId passphrase } }"}""")
        }
        assertEquals(HttpStatusCode.OK, createRes.status)
        val createBody = createRes.bodyAsText()
        assertTrue(createBody.contains("passageId"))
        assertTrue(createBody.contains("passphrase"))

        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<JsonObject>(createBody)
        val createIdentityObj = parsed["data"]!!.jsonObject["createIdentity"]!!.jsonObject
        val authorId = createIdentityObj["passageId"]!!.jsonPrimitive.content
        val initialPass = createIdentityObj["passphrase"]!!.jsonPrimitive.content

        // Verify initial passphrase is 32 alphanumeric chars for high security
        assertEquals(32, initialPass.length)
        assertTrue(initialPass.all { it.isLetterOrDigit() }, "Initial passphrase must be alphanumeric")

        // 2. Attempt change with incorrect old passphrase
        val failChangeRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { changePassphrase(authorId: \"$authorId\", oldPassphrase: \"wrong-pass\", newPassphrase: \"newStrongPass123\") }"}""")
        }
        assertEquals(HttpStatusCode.OK, failChangeRes.status)
        val failBody = failChangeRes.bodyAsText()
        assertTrue(failBody.contains("errors") || failBody.contains("Invalid current passphrase"))

        // 3. Successfully change passphrase with correct old passphrase
        val newSecret = "newSecretAlphaNumericPassphrase999"
        val successChangeRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { changePassphrase(authorId: \"$authorId\", oldPassphrase: \"$initialPass\", newPassphrase: \"$newSecret\") }"}""")
        }
        assertEquals(HttpStatusCode.OK, successChangeRes.status)
        val successBody = successChangeRes.bodyAsText()
        assertTrue(successBody.contains("\"changePassphrase\": true") || successBody.contains("\"changePassphrase\":true"))

        // 4. Old initial passphrase should no longer work
        val secondFailRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { changePassphrase(authorId: \"$authorId\", oldPassphrase: \"$initialPass\", newPassphrase: \"anotherPass123\") }"}""")
        }
        val secondFailBody = secondFailRes.bodyAsText()
        assertTrue(secondFailBody.contains("errors") || secondFailBody.contains("Invalid current passphrase"))
    }

    @Test
    fun testGraphQLChatFlows() = testApplication {
        application {
            module(testConfig)
        }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // 1. Create two author identities: Author A and Author B
        val (authorAId, tokenA) = client.createTestAuthor("AuthorAlpha")
        val (authorBId, tokenB) = client.createTestAuthor("AuthorBeta")

        // 2. Author A requests an unencrypted chat with Author B
        val reqUnencrypted = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorAId\", recipientId: \"$authorBId\", type: \"UNENCRYPTED\") { id status type } }"}""")
        }
        assertEquals(HttpStatusCode.OK, reqUnencrypted.status)
        val chat1Obj = json.parseToJsonElement(reqUnencrypted.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject
        val chat1Id = chat1Obj["id"]!!.jsonPrimitive.content
        assertEquals("PENDING", chat1Obj["status"]!!.jsonPrimitive.content)
        assertEquals("UNENCRYPTED", chat1Obj["type"]!!.jsonPrimitive.content)

        // 3. Author B accepts the chat
        val respondUnencrypted = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$chat1Id\", authorId: \"$authorBId\", accept: true) { id status } }"}""")
        }
        val acceptObj = json.parseToJsonElement(respondUnencrypted.bodyAsText()).jsonObject["data"]!!.jsonObject["respondChat"]!!.jsonObject
        assertEquals("ACCEPTED", acceptObj["status"]!!.jsonPrimitive.content)

        // 4. Author A sends a message citing a passage
        val sendRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$chat1Id\", senderId: \"$authorAId\", recipientId: \"$authorBId\", content: \"Hello Author B! Here is an axiom.\", isEncrypted: false, citedPassageId: \"pas-root\") { id content isEncrypted citedPassageId } }"}""")
        }
        assertEquals(HttpStatusCode.OK, sendRes.status)
        val msgObj = json.parseToJsonElement(sendRes.bodyAsText()).jsonObject["data"]!!.jsonObject["sendChatMessage"]!!.jsonObject
        assertEquals("Hello Author B! Here is an axiom.", msgObj["content"]!!.jsonPrimitive.content)
        assertEquals("pas-root", msgObj["citedPassageId"]?.jsonPrimitive?.content)

        // 5. Query chatMessages
        val queryMessages = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { chatMessages(chatId: \"$chat1Id\", authorId: \"$authorBId\") { id content citedPassageId isEncrypted } }"}""")
        }
        val messagesArr = json.parseToJsonElement(queryMessages.bodyAsText()).jsonObject["data"]!!.jsonObject["chatMessages"]!!.jsonArray
        assertEquals(1, messagesArr.size)
        assertEquals("Hello Author B! Here is an axiom.", messagesArr[0].jsonObject["content"]!!.jsonPrimitive.content)

        // 6. Test Encrypted Chat Handshake & Asymmetric Ephemeral Relay
        val reqEncrypted = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorAId\", recipientId: \"$authorBId\", type: \"ENCRYPTED\", initiatorPublicKey: \"MIIBIjANBgkqhkiG9w0BAQEFAAOAlice\") { id status type initiatorPublicKey } }"}""")
        }
        val encChatObj = json.parseToJsonElement(reqEncrypted.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject
        val encChatId = encChatObj["id"]!!.jsonPrimitive.content
        assertEquals("ENCRYPTED", encChatObj["type"]!!.jsonPrimitive.content)

        val acceptEnc = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$encChatId\", authorId: \"$authorBId\", accept: true, recipientPublicKey: \"MIIBIjANBgkqhkiG9w0BAQEFAAOABob\") { id status recipientPublicKey } }"}""")
        }
        val acceptEncObj = json.parseToJsonElement(acceptEnc.bodyAsText()).jsonObject["data"]!!.jsonObject["respondChat"]!!.jsonObject
        assertEquals("ACCEPTED", acceptEncObj["status"]!!.jsonPrimitive.content)
        assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOABob", acceptEncObj["recipientPublicKey"]!!.jsonPrimitive.content)

        // Send encrypted envelope message
        val sendEncMsg = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$encChatId\", senderId: \"$authorAId\", recipientId: \"$authorBId\", content: \"ASYM:EncryptedEnvelopeBodyKey:iv:payload\", isEncrypted: true) { id content isEncrypted } }"}""")
        }
        assertTrue(sendEncMsg.bodyAsText().contains("ASYM:EncryptedEnvelopeBodyKey"))
    }

    @Test
    fun testServerInfoAndStatusEndpointsReportProtocol() = testApplication {
        val httpConfig = testConfig.copy(protocol = connects.quotes.config.ServerProtocol.HTTP)
        application {
            module(httpConfig)
        }

        val statusRes = client.get("/status")
        assertEquals(HttpStatusCode.OK, statusRes.status)
        assertTrue(statusRes.bodyAsText().contains("Protocol: HTTP"))

        val infoRes = client.get("/server-info")
        assertEquals(HttpStatusCode.OK, infoRes.status)
        assertTrue(infoRes.bodyAsText().contains("\"protocol\": \"HTTP\""))
        assertTrue(infoRes.bodyAsText().contains("http://localhost:"))
    }

    @Test
    fun testSslConfigKeyStoreGeneration() {
        val ssl = connects.quotes.config.SslConfig.defaultDevConfig()
        val resolved = ssl.resolveKeyStore()
        assertNotNull(resolved.keyStore)
        assertEquals("quotesconnect", resolved.keyAlias)
        assertTrue(resolved.keyStore.containsAlias("quotesconnect"))
    }

    @Test
    fun testStaticIconEndpoints() = testApplication {
        application {
            module(testConfig)
        }

        val faviconSvg = client.get("/favicon.svg")
        assertEquals(HttpStatusCode.OK, faviconSvg.status)
        assertEquals("image/svg+xml", faviconSvg.contentType()?.toString())

        val faviconIco = client.get("/favicon.ico")
        assertEquals(HttpStatusCode.OK, faviconIco.status)

        val iconPng = client.get("/icon.png")
        assertEquals(HttpStatusCode.OK, iconPng.status)
        assertEquals("image/png", iconPng.contentType()?.toString())

        val manifest = client.get("/site.webmanifest")
        assertEquals(HttpStatusCode.OK, manifest.status)
    }

    @Test
    fun testLiveChatHubAndNotificationDispatch() {
        val chatId = "chat-live-test"
        val senderId = "author-alice"
        val recipientId = "author-bob"

        val bobNotifChannel = connects.quotes.chat.LiveChatHub.registerNotificationListener(recipientId)
        val aliceChatChannel = connects.quotes.chat.LiveChatHub.registerChatListener(chatId, senderId)

        val msg = connects.quotes.model.ChatMessage(
            id = connects.quotes.model.Id("msg-live-1"),
            chatId = connects.quotes.model.Id(chatId),
            senderId = connects.quotes.model.Id(senderId),
            recipientId = connects.quotes.model.Id(recipientId),
            content = "Live TCP chat message from Alice",
            isEncrypted = false,
            timestamp = System.currentTimeMillis()
        )
        connects.quotes.chat.LiveChatHub.broadcastMessage(msg)

        val alicePacket = aliceChatChannel.tryReceive().getOrNull()
        assertNotNull(alicePacket)
        assertEquals("MESSAGE", alicePacket.type)
        assertEquals(msg.id.value, alicePacket.message?.id?.value)

        val bobNotifPacket = bobNotifChannel.tryReceive().getOrNull()
        assertNotNull(bobNotifPacket)
        assertEquals("NOTIFICATION", bobNotifPacket.type)
        assertEquals(chatId, bobNotifPacket.chatId)
        assertEquals(senderId, bobNotifPacket.senderId)

        connects.quotes.chat.LiveChatHub.unregisterChatListener(chatId, aliceChatChannel)
        connects.quotes.chat.LiveChatHub.unregisterNotificationListener(recipientId, bobNotifChannel)
    }

    @Test
    fun testRenewChatKeyMutation() = testApplication {
        application {
            module(testConfig)
        }

        val (authorA, tokenA) = client.createTestAuthor("AliceRenew")
        val (authorB, tokenB) = client.createTestAuthor("BobRenew")

        val requestRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"ENCRYPTED\", initiatorPublicKey: \"INITIAL_ALICE_KEY\") { id status initiatorPublicKey } }"}""")
        }
        val reqBody = requestRes.bodyAsText()
        assertTrue(reqBody.contains("requestChat"))
        val chatId = Json.parseToJsonElement(reqBody).jsonObject["data"]?.jsonObject?.get("requestChat")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(chatId)

        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$chatId\", authorId: \"$authorB\", accept: true, recipientPublicKey: \"INITIAL_BOB_KEY\") { status recipientPublicKey } }"}""")
        }

        val renewRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { renewChatKey(chatId: \"$chatId\", authorId: \"$authorB\", newPublicKey: \"NEW_BOB_KEY_AFTER_REINSTALL\") { recipientPublicKey } }"}""")
        }
        val renewBody = renewRes.bodyAsText()
        assertTrue(renewBody.contains("NEW_BOB_KEY_AFTER_REINSTALL"))
    }

    @Test
    fun testEncryptedCloudChatAndKeyStoreManagement() = testApplication {
        application {
            module(testConfig)
        }

        val (authorA, tokenA) = client.createTestAuthor("CloudAlice")
        val (authorB, tokenB) = client.createTestAuthor("CloudBob")

        // 1. Create an ENCRYPTED_CLOUD chat
        val reqRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"ENCRYPTED_CLOUD\", initiatorPublicKey: \"ALICE_CLOUD_PUB\") { id type status } }"}""")
        }
        val reqBody = reqRes.bodyAsText()
        assertTrue(reqBody.contains("ENCRYPTED_CLOUD"))
        val chatId = Json.parseToJsonElement(reqBody).jsonObject["data"]?.jsonObject?.get("requestChat")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(chatId)

        // Accept chat
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$chatId\", authorId: \"$authorB\", accept: true, recipientPublicKey: \"BOB_CLOUD_PUB\") { status } }"}""")
        }

        // 2. Send ENCRYPTED_CLOUD ciphertext message (persisted on server)
        val sendRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$chatId\", senderId: \"$authorA\", recipientId: \"$authorB\", content: \"CIPHERTEXT_BLOB_XYZ\", isEncrypted: true) { id content isEncrypted } }"}""")
        }
        val sendBody = sendRes.bodyAsText()
        assertTrue(sendBody.contains("CIPHERTEXT_BLOB_XYZ"))
        val msgObj = Json.parseToJsonElement(sendBody).jsonObject["data"]?.jsonObject?.get("sendChatMessage")?.jsonObject
        assertNotNull(msgObj)
        assertEquals(true, msgObj["isEncrypted"]?.jsonPrimitive?.boolean)

        // Fetch messages for cloud-bob (verifies persisted ciphertext)
        val fetchRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { chatMessages(chatId: \"$chatId\", authorId: \"$authorB\") { id content isEncrypted } }"}""")
        }
        val fetchBody = fetchRes.bodyAsText()
        assertTrue(fetchBody.contains("CIPHERTEXT_BLOB_XYZ"))

        // 3. Keystore Backup & Query & Delete
        val backupRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { backupKeyStore(authorId: \"$authorA\", encryptedPayload: \"AAECAwQFBgcICQoLDA0ODw==\", salt: \"AQEBAQEBAQEBAQEBAQEBAQ==\", iv: \"AgICAgICAgICAgIC\", version: 3) }"}""")
        }
        val backupBody = backupRes.bodyAsText()
        assertTrue(backupBody.contains("true"))

        val queryKsRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { keystore(authorId: \"$authorA\") { authorId encryptedPayload salt iv version } }"}""")
        }
        val queryKsBody = queryKsRes.bodyAsText()
        assertTrue(queryKsBody.contains("AAECAwQFBgcICQoLDA0ODw=="))
        assertTrue(queryKsBody.contains("AQEBAQEBAQEBAQEBAQEBAQ=="))

        val delKsRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { deleteKeyStore(authorId: \"$authorA\") }"}""")
        }
        val delKsBody = delKsRes.bodyAsText()
        assertTrue(delKsBody.contains("true"))

        val queryAfterDelRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { keystore(authorId: \"$authorA\") { authorId } }"}""")
        }
        val queryAfterDelBody = queryAfterDelRes.bodyAsText()
        val ksObj = Json.parseToJsonElement(queryAfterDelBody).jsonObject["data"]?.jsonObject?.get("keystore")
        assertTrue(ksObj == null || ksObj is JsonNull)
    }

    @Test
    fun testTypingAndResyncBroadcast() = testApplication {
        application {
            module(testConfig)
        }

        val (authorTypist, tokenTypist) = client.createTestAuthor("Typist")
        val (authorListener, _) = client.createTestAuthor("Listener")

        // 1. Establish chat session
        val chatReqRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenTypist")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorTypist\", recipientId: \"$authorListener\", type: \"UNENCRYPTED\") { id } }"}""")
        }
        val chatId = Json.parseToJsonElement(chatReqRes.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val recipientChannel = connects.quotes.chat.LiveChatHub.registerChatListener(chatId, authorListener)

        // 1. Broadcast typing (quoting...)
        val typingRes = client.post("/api/chat/typing") {
            header(HttpHeaders.Authorization, "Bearer $tokenTypist")
            setBody(io.ktor.http.Parameters.build {
                append("chatId", chatId)
                append("authorId", authorTypist)
                append("isTyping", "true")
            }.formUrlEncode())
            contentType(ContentType.Application.FormUrlEncoded)
        }
        assertEquals(HttpStatusCode.OK, typingRes.status)

        val typingPacket = recipientChannel.tryReceive().getOrNull()
        assertNotNull(typingPacket)
        assertEquals("TYPING", typingPacket.type)
        assertEquals(authorTypist, typingPacket.senderId)
        assertEquals(true, typingPacket.isTyping)

        // 2. Broadcast resync request
        val resyncRes = client.post("/api/chat/resync") {
            header(HttpHeaders.Authorization, "Bearer $tokenTypist")
            setBody(io.ktor.http.Parameters.build {
                append("chatId", chatId)
                append("requesterId", authorTypist)
            }.formUrlEncode())
            contentType(ContentType.Application.FormUrlEncoded)
        }
        assertEquals(HttpStatusCode.OK, resyncRes.status)

        val resyncPacket = recipientChannel.tryReceive().getOrNull()
        assertNotNull(resyncPacket)
        assertEquals("RESYNC_REQUEST", resyncPacket.type)
        assertEquals(authorTypist, resyncPacket.senderId)

        connects.quotes.chat.LiveChatHub.unregisterChatListener(chatId, recipientChannel)
    }

    @Test
    fun testAuthorActivityLoggingAndInactivityPurge() = testApplication {
        application {
            module(testConfig)
        }

        // 1. Create Author 1 Identity
        val (author1Id, token1) = client.createTestAuthor("PurgeAuthor1")

        // Verify default inactivity tag is inactivity:30d
        val passQuery = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { passage(id: \"$author1Id\") { tags } }"}""")
        }
        val passBody = passQuery.bodyAsText()
        assertTrue(passBody.contains("inactivity:30d"))

        // 2. Active Ping & Activity Logging
        val pingRes = client.post("/api/author/ping") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody(Parameters.build {
                append("authorId", author1Id)
            }.formUrlEncode())
            contentType(ContentType.Application.FormUrlEncoded)
        }
        assertEquals(HttpStatusCode.OK, pingRes.status)

        val logRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { authorActivityLogs(authorId: \"$author1Id\") { activityType } }"}""")
        }
        val logBody = logRes.bodyAsText()
        assertTrue(logBody.contains("PING") || logBody.contains("IDENTITY_CREATED"))

        // 3. Update Settings to 1 day inactivity & ORPHAN discard action
        val updateRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { updateAuthorSettings(authorId: \"$author1Id\", inactivityPeriodDays: 1, discardAction: \"ORPHAN\") { inactivityPeriodDays discardAction } }"}""")
        }
        val updateBody = updateRes.bodyAsText()
        val updateObj = Json.parseToJsonElement(updateBody).jsonObject["data"]?.jsonObject?.get("updateAuthorSettings")?.jsonObject
        assertNotNull(updateObj)
        assertEquals(1, updateObj["inactivityPeriodDays"]?.jsonPrimitive?.int)
        assertEquals("ORPHAN", updateObj["discardAction"]?.jsonPrimitive?.content)

        // Verify tag is updated to inactivity:1d on author passage
        val updatedPassQuery = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { passage(id: \"$author1Id\") { tags } }"}""")
        }
        val updatedPassBody = updatedPassQuery.bodyAsText()
        assertTrue(updatedPassBody.contains("inactivity:1d"))
        assertFalse(updatedPassBody.contains("inactivity:30d"))

        // 4. Create Author 2 and Unadvertised Passage under Author 1
        val (author2Id, token2) = client.createTestAuthor("ChatPartnerBob")

        val createPassRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createPassage(type: \"PASSAGE\", content: \"Author 1 Private Thought\", tags: [\"ephemeral\"], parentId: \"$author1Id\") { id } }"}""")
        }
        val unadvPassageId = Json.parseToJsonElement(createPassRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("createPassage")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(unadvPassageId)

        // 5. Establish Chat and Send Messages
        val chatReqRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$author1Id\", recipientId: \"$author2Id\", type: \"UNENCRYPTED\") { id } }"}""")
        }
        val chatId = Json.parseToJsonElement(chatReqRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("requestChat")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(chatId)

        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$chatId\", authorId: \"$author2Id\", accept: true) { status } }"}""")
        }

        // Send message from Author 1
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$chatId\", senderId: \"$author1Id\", recipientId: \"$author2Id\", content: \"Ephemeral message from Author 1\", isEncrypted: false) { id } }"}""")
        }

        // Send message from Author 2
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$chatId\", senderId: \"$author2Id\", recipientId: \"$author1Id\", content: \"Persistent reply from Author 2\", isEncrypted: false) { id } }"}""")
        }

        // Verify both messages exist
        val beforePurgeMsgs = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { chatMessages(chatId: \"$chatId\", authorId: \"$author2Id\") { content senderId } }"}""")
        }
        assertTrue(beforePurgeMsgs.bodyAsText().contains("Ephemeral message from Author 1"))
        assertTrue(beforePurgeMsgs.bodyAsText().contains("Persistent reply from Author 2"))

        // 6. Execute Inactivity Sweep with simulated future timestamp (2 days later)
        val futureTime = System.currentTimeMillis() + (2L * 86400000L)
        val sweepRes = client.post("/api/author/inactivity-check") {
            setBody(Parameters.build {
                append("currentTimeMillis", futureTime.toString())
            }.formUrlEncode())
            contentType(ContentType.Application.FormUrlEncoded)
        }
        assertEquals(HttpStatusCode.OK, sweepRes.status)
        val sweepBody = sweepRes.bodyAsText()
        assertTrue(sweepBody.contains(""""purgedAuthorsCount":"""))

        // 7. Verify Chat from Author 1 side was deleted, while Author 2's message remains
        val afterPurgeMsgs = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { chatMessages(chatId: \"$chatId\", authorId: \"$author2Id\") { content senderId } }"}""")
        }
        val afterBody = afterPurgeMsgs.bodyAsText()
        assertFalse(afterBody.contains("Ephemeral message from Author 1"))
        assertTrue(afterBody.contains("Persistent reply from Author 2"))

        // 8. Verify Unadvertised Passage was converted to Orphan and published to Billboard
        val billboardRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { billboard { isOrphan passage { id content } } }"}""")
        }
        val bbBody = billboardRes.bodyAsText()
        assertTrue(bbBody.contains(unadvPassageId))
        assertTrue(bbBody.contains("Author 1 Private Thought"))
    }

    @Test
    fun testAuthorDiscardActionDelete() = testApplication {
        application {
            module(testConfig)
        }

        // 1. Create Author with DELETE discard action
        val (authorId, token) = client.createTestAuthor("DeleteAuthor3")

        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { updateAuthorSettings(authorId: \"$authorId\", inactivityPeriodDays: 1, discardAction: \"DELETE\") { discardAction } }"}""")
        }

        // 2. Create regular unadvertised passage (should be deleted)
        val normalPassRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createPassage(type: \"PASSAGE\", content: \"Disposable Passage To Delete\", tags: [\"temp\"], parentId: \"$authorId\") { id } }"}""")
        }
        val normalPassId = Json.parseToJsonElement(normalPassRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("createPassage")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(normalPassId)

        // 3. Create recordable unadvertised passage (ledger-immutable -> must be orphaned, not deleted)
        val recordablePassRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createPassage(type: \"RECORDABLE\", content: \"Immutable Recordable Passage\", tags: [\"ledger\"], parentId: \"$authorId\", isRecordable: true) { id } }"}""")
        }
        val recordablePassId = Json.parseToJsonElement(recordablePassRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("createPassage")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(recordablePassId)

        // 4. Trigger explicit discard
        val discardRes = client.post("/api/author/discard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(Parameters.build {
                append("authorId", authorId)
            }.formUrlEncode())
            contentType(ContentType.Application.FormUrlEncoded)
        }
        assertEquals(HttpStatusCode.OK, discardRes.status)

        // 5. Verify regular passage is permanently deleted
        val checkNormal = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { passage(id: \"$normalPassId\") { id content } }"}""")
        }
        val checkNormalBody = checkNormal.bodyAsText()
        val normalObj = Json.parseToJsonElement(checkNormalBody).jsonObject["data"]?.jsonObject?.get("passage")
        assertTrue(normalObj == null || normalObj is JsonNull)

        // 6. Verify recordable passage is preserved on billboard as orphan
        val checkRecordable = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { billboard { isOrphan passage { id content } } }"}""")
        }
        val bbBody = checkRecordable.bodyAsText()
        assertTrue(bbBody.contains(recordablePassId))
        assertTrue(bbBody.contains("Immutable Recordable Passage"))
    }

    @Test
    fun testDeleteChatMutation() = testApplication {
        application {
            module(testConfig)
        }

        val (authorA, tokenA) = client.createTestAuthor("DelAlice")
        val (authorB, _) = client.createTestAuthor("DelBob")

        val reqRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"UNENCRYPTED\") { id status } }"}""")
        }
        val chatId = Json.parseToJsonElement(reqRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("requestChat")?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertNotNull(chatId)

        val delRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { deleteChat(chatId: \"$chatId\", authorId: \"$authorA\") }"}""")
        }
        val delBody = delRes.bodyAsText()
        assertTrue(delBody.contains("\"deleteChat\":true") || delBody.contains("\"deleteChat\": true"))

        val checkRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { chat(chatId: \"$chatId\", authorId: \"$authorA\") { id } }"}""")
        }
        val checkObj = Json.parseToJsonElement(checkRes.bodyAsText()).jsonObject["data"]?.jsonObject?.get("chat")
        assertTrue(checkObj == null || checkObj is JsonNull)
    }
}