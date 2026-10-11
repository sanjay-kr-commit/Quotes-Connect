package connects.quotes.client

import connects.quotes.config.ServerEndpointConfig
import connects.quotes.config.ServerEndpointManager
import connects.quotes.model.*
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.*
import connects.quotes.util.currentTimeMillis
import kotlinx.coroutines.flow.Flow

/**
 * Universal client layer for Quotes Connect.
 * Connects to the remote Quotes Connect GraphQL server, with automatic
 * connectivity detection and offline-fallback capability.
 */
class QuotesApiClient(
    private val remoteClient: RemoteGraphQLClient = RemoteGraphQLClient()
) {
    private val localRepo = InMemoryKnowledgeRepository()
    private val createPassageUseCase = CreatePassageUseCase(localRepo, localRepo, localRepo, localRepo)
    private val subscribePassageUseCase = SubscribePassageUseCase(localRepo, localRepo)
    private val toggleAdvertisementUseCase = ToggleAdvertisementUseCase(localRepo, localRepo)
    private val deletePassageUseCase = DeletePassageUseCase(localRepo)
    private val traverseGraphUseCase = TraverseGraphUseCase(localRepo)

    var isServerAvailable: Boolean = true
        private set

    val channelSecurityMode: ChannelSecurityMode
        get() = if (!isServerAvailable) ChannelSecurityMode.OFFLINE else remoteClient.channelSecurityMode

    init {
        // Starts clean with zero template data - all knowledge is created by the user
    }

    suspend fun checkServerAvailability(endpoint: ServerEndpointConfig = ServerEndpointManager.get()): Boolean {
        val available = remoteClient.checkServerAvailability(endpoint)
        isServerAvailable = available
        return available
    }

    suspend fun getBillboard(tag: String? = null): List<BillboardItem> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val items = remoteClient.getBillboard(tag, endpoint)
            isServerAvailable = true
            items
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getAdvertised(limit = 100, tag = tag)
        }
    }

    suspend fun getFeed(authorId: Id, depth: Int = 1, limit: Int = 50, offset: Int = 0): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val feed = remoteClient.getFeed(authorId, depth = depth, limit = limit, offset = offset, endpoint = endpoint)
            isServerAvailable = true
            feed
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getFeed(authorId, depth = depth, limit = limit, offset = offset)
        }
    }

    suspend fun getPassage(id: Id): Passage? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val p = remoteClient.getPassage(id, endpoint)
            isServerAvailable = true
            p ?: localRepo.get(id)
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.get(id)
        }
    }

    suspend fun getSubscribers(id: Id): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val subs = remoteClient.getSubscribers(id, endpoint)
            isServerAvailable = true
            subs
        } catch (_: Exception) {
            isServerAvailable = false
            val subIds = localRepo.getSubscribers(id)
            subIds.mapNotNull { localRepo.get(it) }
        }
    }

    suspend fun getSubscribedTo(id: Id): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val subs = remoteClient.getSubscribedTo(id, endpoint)
            isServerAvailable = true
            subs
        } catch (_: Exception) {
            isServerAvailable = false
            val targetIds = localRepo.getSubscribedTo(id)
            targetIds.mapNotNull { localRepo.get(it) }
        }
    }

    suspend fun getTopTags(): List<Tag> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val tags = remoteClient.getTopTags(endpoint)
            isServerAvailable = true
            if (tags.isNotEmpty()) {
                ClientTagCache.updateTags(tags)
                tags
            } else {
                val cached = ClientTagCache.getTags()
                if (cached.isNotEmpty()) cached else localRepo.getTopTags(limit = 20)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val cached = ClientTagCache.getTags()
            if (cached.isNotEmpty()) cached else localRepo.getTopTags(limit = 20)
        }
    }

    suspend fun suggestTags(query: String, limit: Int = 10): List<Tag> {
        val cached = ClientTagCache.suggestTags(query, limit)
        val endpoint = ServerEndpointManager.get()
        return try {
            val remote = remoteClient.suggestTags(query, limit, endpoint)
            isServerAvailable = true
            if (remote.isNotEmpty()) {
                ClientTagCache.updateTags(remote)
                remote
            } else {
                cached
            }
        } catch (_: Exception) {
            isServerAvailable = false
            cached
        }
    }

    suspend fun getNotifications(authorId: Id): List<Notification> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val notifs = remoteClient.getNotifications(authorId, endpoint)
            isServerAvailable = true
            notifs
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getNotifications(authorId, limit = 50)
        }
    }

    suspend fun search(query: String): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val results = remoteClient.search(query, endpoint)
            isServerAvailable = true
            results
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.search(query, limit = 50)
        }
    }

    suspend fun getCreatedPassages(authorId: Id): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val list = remoteClient.getCreatedPassages(authorId, endpoint)
            isServerAvailable = true
            list
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getCreatedByAuthor(authorId)
        }
    }

    suspend fun createIdentity(moniker: String, shouldAdvertise: Boolean = false, rememberMe: Boolean = true): Credentials {
        val endpoint = ServerEndpointManager.get()
        return try {
            val creds = remoteClient.createIdentity(moniker, shouldAdvertise, endpoint)
            isServerAvailable = true
            try {
                val session = remoteClient.login(creds.passageId.value, creds.passphrase, endpoint = endpoint)
                SessionManager.setSession(session, creds, rememberMe)
            } catch (_: Exception) {
                SessionManager.setIdentity(creds, rememberMe)
            }
            creds
        } catch (_: Exception) {
            isServerAvailable = false
            val now = currentTimeEpoch()
            val author = createPassageUseCase(
                type = PassageType.AUTHOR,
                content = moniker,
                tags = listOf("identity", "author"),
                parentId = null,
                shouldAdvertise = shouldAdvertise,
                currentTimeMillis = now
            ).getOrThrow()

            val passphrase = Credentials.generatePassphrase()
            val creds = Credentials(author.id, passphrase)
            localRepo.saveCredentials(creds)
            SessionManager.setIdentity(creds, rememberMe)
            creds
        }
    }

    suspend fun login(authorId: String, passphrase: String, rememberMe: Boolean = true): connects.quotes.security.AuthSession {
        val endpoint = ServerEndpointManager.get()
        val session = remoteClient.login(authorId, passphrase, endpoint = endpoint)
        isServerAvailable = true
        val creds = Credentials(Id(authorId), passphrase)
        SessionManager.setSession(session, creds, rememberMe)
        return session
    }

    suspend fun logout() {
        val endpoint = ServerEndpointManager.get()
        try {
            remoteClient.logout(endpoint = endpoint)
        } catch (_: Exception) {}
        SessionManager.clearIdentity()
    }

    suspend fun createPassage(
        type: PassageType,
        content: String,
        tags: List<String> = emptyList(),
        parentId: Id? = SessionManager.currentAuthorId,
        isRecordable: Boolean = false,
        shouldAdvertise: Boolean = false,
        subscribeToId: Id? = null
    ): Result<Passage> {
        val endpoint = ServerEndpointManager.get()
        val remoteRes = remoteClient.createPassage(
            type = type,
            content = content,
            tags = tags,
            parentId = parentId,
            isRecordable = isRecordable,
            shouldAdvertise = shouldAdvertise,
            subscribeToId = subscribeToId,
            endpoint = endpoint
        )

        if (remoteRes.isSuccess) {
            isServerAvailable = true
            return remoteRes
        }

        // If remote failed, check if server is available
        val available = checkServerAvailability(endpoint)
        if (!available) {
            isServerAvailable = false
            // Execute locally so user does not lose progress
            val now = currentTimeEpoch()
            return createPassageUseCase(
                type = type,
                content = content,
                tags = tags,
                parentId = parentId,
                isRecordable = isRecordable,
                shouldAdvertise = shouldAdvertise,
                subscribeToId = subscribeToId,
                currentTimeMillis = now
            )
        }

        return remoteRes
    }

    suspend fun toggleAdvertisement(passageId: Id, authorId: Id?): Result<Boolean> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.toggleAdvertisement(passageId, authorId, endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                res
            } else {
                toggleAdvertisementUseCase(passageId, authorId)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            toggleAdvertisementUseCase(passageId, authorId)
        }
    }

    suspend fun getAdvertisedPassageIds(authorId: Id): Set<String> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val ids = remoteClient.getAdvertisedPassageIds(authorId, endpoint)
            isServerAvailable = true
            ids
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getAdvertisedPassageIds(authorId).map { it.value }.toSet()
        }
    }

    suspend fun setPassageTypeAdvertisement(authorId: Id, type: String?, advertise: Boolean): Result<Int> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.setPassageTypeAdvertisement(authorId, type, advertise, endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                res
            } else {
                val pt = if (!type.isNullOrBlank() && !type.equals("ALL", ignoreCase = true)) {
                    try { PassageType.valueOf(type.uppercase()) } catch (_: Exception) { null }
                } else null
                val count = localRepo.setPassageTypeAdvertisement(authorId, pt, advertise)
                Result.success(count)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val pt = if (!type.isNullOrBlank() && !type.equals("ALL", ignoreCase = true)) {
                try { PassageType.valueOf(type.uppercase()) } catch (_: Exception) { null }
            } else null
            val count = localRepo.setPassageTypeAdvertisement(authorId, pt, advertise)
            Result.success(count)
        }
    }

    suspend fun sendKeyExchangePacket(packet: KeyExchangePacket): Result<Unit> {
        return remoteClient.sendKeyExchangePacket(packet)
    }

    suspend fun deletePassage(passageId: Id, authorId: Id?): Result<Boolean> {
        return deletePassageUseCase(
            passageId = passageId,
            requesterAuthorId = authorId
        )
    }

    suspend fun isOrphan(passageId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val isOrph = remoteClient.isOrphan(passageId, endpoint)
            isServerAvailable = true
            isOrph
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.isOrphan(passageId)
        }
    }

    suspend fun isAdvertised(passageId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val isAdv = remoteClient.isAdvertised(passageId, endpoint)
            isServerAvailable = true
            isAdv
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.isAdvertised(passageId)
        }
    }

    suspend fun getDescendantTree(rootId: Id, maxDepth: Int = 4): connects.quotes.graph.GraphEngine.TreeNode? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val tree = remoteClient.getDescendantTree(rootId, maxDepth, endpoint)
            isServerAvailable = true
            tree ?: traverseGraphUseCase.getDescendantTree(rootId, maxDepth)
        } catch (_: Exception) {
            isServerAvailable = false
            traverseGraphUseCase.getDescendantTree(rootId, maxDepth)
        }
    }

    suspend fun changePassphrase(
        authorId: Id,
        oldPassphrase: String,
        newPassphrase: String
    ): Result<Boolean> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val result = remoteClient.changePassphrase(authorId, oldPassphrase, newPassphrase, endpoint)
            if (result.isSuccess) {
                isServerAvailable = true
                val updatedCreds = Credentials(authorId, newPassphrase)
                SessionManager.setIdentity(updatedCreds, SessionManager.isRememberMe.value)
                return result
            }

            val available = checkServerAvailability(endpoint)
            if (!available) {
                isServerAvailable = false
                val success = localRepo.updatePassphrase(authorId, oldPassphrase, newPassphrase)
                if (success) {
                    val updatedCreds = Credentials(authorId, newPassphrase)
                    SessionManager.setIdentity(updatedCreds, SessionManager.isRememberMe.value)
                    Result.success(true)
                } else {
                    Result.failure(IllegalStateException("Failed to update passphrase. Current passphrase incorrect."))
                }
            } else {
                result
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val success = localRepo.updatePassphrase(authorId, oldPassphrase, newPassphrase)
            if (success) {
                val updatedCreds = Credentials(authorId, newPassphrase)
                SessionManager.setIdentity(updatedCreds, SessionManager.isRememberMe.value)
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Failed to update passphrase. Current passphrase incorrect."))
            }
        }
    }

    suspend fun getChats(authorId: Id): List<ChatSession> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val chats = remoteClient.getChats(authorId, endpoint)
            isServerAvailable = true
            chats
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getChatSessionsForAuthor(authorId)
        }
    }

    suspend fun getChat(chatId: Id): ChatSession? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val chat = remoteClient.getChat(chatId, endpoint)
            isServerAvailable = true
            chat ?: localRepo.getChatSession(chatId)
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getChatSession(chatId)
        }
    }

    suspend fun getChatMessages(chatId: Id, authorId: Id): List<ChatMessage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val messages = remoteClient.getChatMessages(chatId, authorId, endpoint)
            isServerAvailable = true
            messages
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getChatMessages(chatId, authorId)
        }
    }

    suspend fun requestChat(
        initiatorId: Id,
        recipientId: Id,
        type: ChatType,
        initiatorPublicKey: String? = null
    ): Result<ChatSession> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val result = remoteClient.requestChat(initiatorId, recipientId, type, initiatorPublicKey, endpoint)
            if (result.isSuccess) {
                isServerAvailable = true
                result
            } else {
                val available = checkServerAvailability(endpoint)
                if (!available) {
                    isServerAvailable = false
                    val session = localRepo.createChatSession(initiatorId, recipientId, type, initiatorPublicKey)
                    Result.success(session)
                } else {
                    result
                }
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val session = localRepo.createChatSession(initiatorId, recipientId, type, initiatorPublicKey)
            Result.success(session)
        }
    }

    suspend fun respondChat(
        chatId: Id,
        authorId: Id,
        accept: Boolean,
        recipientPublicKey: String? = null
    ): Result<ChatSession> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val result = remoteClient.respondChat(chatId, authorId, accept, recipientPublicKey, endpoint)
            if (result.isSuccess) {
                isServerAvailable = true
                result
            } else {
                val available = checkServerAvailability(endpoint)
                if (!available) {
                    isServerAvailable = false
                    val status = if (accept) ChatStatus.ACCEPTED else ChatStatus.REJECTED
                    localRepo.updateChatStatus(chatId, status, recipientPublicKey)
                    val s = localRepo.getChatSession(chatId)
                    if (s != null) Result.success(s) else Result.failure(IllegalStateException("Chat not found"))
                } else {
                    result
                }
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val status = if (accept) ChatStatus.ACCEPTED else ChatStatus.REJECTED
            localRepo.updateChatStatus(chatId, status, recipientPublicKey)
            val s = localRepo.getChatSession(chatId)
            if (s != null) Result.success(s) else Result.failure(IllegalStateException("Chat not found"))
        }
    }

    suspend fun sendChatMessage(
        chatId: Id,
        senderId: Id,
        recipientId: Id,
        content: String,
        isEncrypted: Boolean,
        citedPassageId: Id? = null
    ): Result<ChatMessage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val result = remoteClient.sendChatMessage(chatId, senderId, recipientId, content, isEncrypted, citedPassageId, endpoint)
            if (result.isSuccess) {
                isServerAvailable = true
                result
            } else {
                val available = checkServerAvailability(endpoint)
                if (!available) {
                    isServerAvailable = false
                    val msg = ChatMessage(
                        id = Id("msg-" + currentTimeEpoch()),
                        chatId = chatId,
                        senderId = senderId,
                        recipientId = recipientId,
                        content = content,
                        isEncrypted = isEncrypted,
                        citedPassageId = citedPassageId,
                        timestamp = currentTimeEpoch()
                    )
                    val sent = localRepo.sendChatMessage(msg)
                    if (sent) Result.success(msg) else Result.failure(IllegalStateException("Failed to send message locally"))
                } else {
                    result
                }
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val msg = ChatMessage(
                id = Id("msg-" + currentTimeEpoch()),
                chatId = chatId,
                senderId = senderId,
                recipientId = recipientId,
                content = content,
                isEncrypted = isEncrypted,
                citedPassageId = citedPassageId,
                timestamp = currentTimeEpoch()
            )
            val sent = localRepo.sendChatMessage(msg)
            if (sent) Result.success(msg) else Result.failure(IllegalStateException("Failed to send message locally"))
        }
    }

    suspend fun renewChatKey(chatId: Id, authorId: Id, newPublicKey: String): Result<ChatSession> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val result = remoteClient.renewChatKey(chatId, authorId, newPublicKey, endpoint)
            if (result.isSuccess) {
                isServerAvailable = true
                result
            } else {
                val available = checkServerAvailability(endpoint)
                if (!available) {
                    isServerAvailable = false
                    val s = localRepo.renewChatKey(chatId, authorId, newPublicKey)
                    if (s != null) Result.success(s) else Result.failure(IllegalStateException("Failed to renew key locally"))
                } else {
                    result
                }
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val s = localRepo.renewChatKey(chatId, authorId, newPublicKey)
            if (s != null) Result.success(s) else Result.failure(IllegalStateException("Failed to renew key locally"))
        }
    }

    fun streamLiveChat(chatId: Id, authorId: Id): Flow<LiveChatPacket> {
        return remoteClient.streamLiveChat(chatId, authorId, ServerEndpointManager.get())
    }

    fun streamNotifications(authorId: Id): Flow<LiveChatPacket> {
        return remoteClient.streamNotifications(authorId, ServerEndpointManager.get())
    }

    suspend fun getKeyStore(authorId: Id): KeyStorePayload? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.getKeyStore(authorId, endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                res.getOrNull()
            } else {
                localRepo.getKeyStore(authorId)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getKeyStore(authorId)
        }
    }

    suspend fun backupKeyStore(keyStore: KeyStorePayload): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.backupKeyStore(keyStore, endpoint)
            if (res.isSuccess && res.getOrDefault(false)) {
                isServerAvailable = true
                localRepo.backupKeyStore(keyStore)
                true
            } else {
                localRepo.backupKeyStore(keyStore)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.backupKeyStore(keyStore)
        }
    }

    suspend fun deleteKeyStore(authorId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.deleteKeyStore(authorId, endpoint)
            if (res.isSuccess && res.getOrDefault(false)) {
                isServerAvailable = true
                localRepo.deleteKeyStore(authorId)
                true
            } else {
                localRepo.deleteKeyStore(authorId)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.deleteKeyStore(authorId)
        }
    }

    suspend fun deleteChat(chatId: Id, authorId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.deleteChat(chatId, authorId, endpoint)
            if (res.isSuccess && res.getOrDefault(false)) {
                isServerAvailable = true
                localRepo.deleteChatSession(chatId, authorId)
                true
            } else {
                localRepo.deleteChatSession(chatId, authorId)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.deleteChatSession(chatId, authorId)
        }
    }

    suspend fun setTyping(chatId: Id, authorId: Id, isTyping: Boolean): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.setTyping(chatId, authorId, isTyping, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun requestChatResync(chatId: Id, requesterId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.requestChatResync(chatId, requesterId, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getSubscribed(authorId: Id): List<Passage> {
        val endpoint = ServerEndpointManager.get()
        return try {
            val list = remoteClient.getSubscribed(authorId, endpoint)
            isServerAvailable = true
            list
        } catch (_: Exception) {
            isServerAvailable = false
            emptyList()
        }
    }

    suspend fun subscribeAuthorPassage(authorId: Id, passageId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.subscribeAuthorPassage(authorId, passageId, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unsubscribeAuthorPassage(authorId: Id, passageId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.unsubscribeAuthorPassage(authorId, passageId, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun proposeChatAlias(chatId: Id, authorId: Id, alias: String): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.proposeChatAlias(chatId, authorId, alias, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun acceptChatAlias(chatId: Id, authorId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.acceptChatAlias(chatId, authorId, endpoint)
            res.getOrDefault(false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAuthorSettings(authorId: Id): AuthorSettings? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.getAuthorSettings(authorId, endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                res.getOrNull()
            } else {
                localRepo.getAuthorSettings(authorId)
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.getAuthorSettings(authorId)
        }
    }

    suspend fun updateAuthorSettings(
        authorId: Id,
        inactivityPeriodDays: Int,
        discardAction: AuthorDiscardAction,
        username: String? = null,
        bio: String? = null,
        aliasSearchEnabled: Boolean? = null
    ): AuthorSettings? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.updateAuthorSettings(authorId, inactivityPeriodDays, discardAction, username, bio, aliasSearchEnabled, endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                val updated = res.getOrThrow()
                localRepo.saveAuthorSettings(updated)
                updated
            } else {
                val current = localRepo.getAuthorSettings(authorId)
                val fallback = AuthorSettings(
                    authorId = authorId,
                    lastActiveAt = current?.lastActiveAt ?: currentTimeMillis(),
                    inactivityPeriodDays = inactivityPeriodDays,
                    discardAction = discardAction,
                    username = username,
                    bio = bio,
                    aliasSearchEnabled = aliasSearchEnabled ?: current?.aliasSearchEnabled ?: true
                )
                localRepo.saveAuthorSettings(fallback)
                fallback
            }
        } catch (_: Exception) {
            isServerAvailable = false
            val current = localRepo.getAuthorSettings(authorId)
            val fallback = AuthorSettings(
                authorId = authorId,
                lastActiveAt = current?.lastActiveAt ?: currentTimeMillis(),
                inactivityPeriodDays = inactivityPeriodDays,
                discardAction = discardAction,
                username = username,
                bio = bio,
                aliasSearchEnabled = aliasSearchEnabled ?: current?.aliasSearchEnabled ?: true
            )
            localRepo.saveAuthorSettings(fallback)
            fallback
        }
    }

    suspend fun pingAuthorActive(authorId: Id): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.pingAuthorActive(authorId, endpoint)
            localRepo.logAuthorActivity(authorId, "PING")
            res.getOrDefault(true)
        } catch (_: Exception) {
            localRepo.logAuthorActivity(authorId, "PING")
            true
        }
    }

    suspend fun performInactivityCheck(): InactivityCheckResult? {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.performInactivityCheck(endpoint)
            if (res.isSuccess) {
                isServerAvailable = true
                res.getOrNull()
            } else {
                localRepo.performInactivityCheck()
            }
        } catch (_: Exception) {
            isServerAvailable = false
            localRepo.performInactivityCheck()
        }
    }

    suspend fun discardAuthor(authorId: Id, action: AuthorDiscardAction? = null): Boolean {
        val endpoint = ServerEndpointManager.get()
        return try {
            val res = remoteClient.discardAuthor(authorId, action, endpoint)
            localRepo.executeAuthorDiscard(authorId, action)
            res.getOrDefault(true)
        } catch (_: Exception) {
            localRepo.executeAuthorDiscard(authorId, action)
            true
        }
    }

    private fun currentTimeEpoch(): Long = 1774343000000L + (counter++ * 1000L)
    private var counter = 1L
}
