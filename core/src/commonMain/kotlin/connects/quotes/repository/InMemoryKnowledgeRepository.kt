package connects.quotes.repository

import connects.quotes.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe multiplatform in-memory repository implementing all repository interfaces.
 * Provides the storage foundation for testing, client-side offline storage, and fast queries.
 */
class InMemoryKnowledgeRepository : PassageRepository, BillboardRepository, TagRepository, NotificationRepository, ChatRepository, AuthorRepository {

    private val mutex = Mutex()

    // Passages store: id -> Passage
    private val passages = mutableMapOf<Id, Passage>()

    // Server-private lineage store: id -> parentId
    private val serverParentMap = mutableMapOf<Id, Id>()

    // Subscriptions store: sourceId -> list of targetIds
    private val outgoingSubscriptions = mutableMapOf<Id, MutableList<Id>>()

    // Subscriptions store: targetId -> list of sourceIds
    private val incomingSubscribers = mutableMapOf<Id, MutableList<Id>>()

    // Billboard registry: passageId -> BillboardItem
    private val billboard = mutableMapOf<Id, BillboardItem>()

    // Global tags: tagName -> Tag
    private val globalTags = mutableMapOf<String, Tag>()

    // Notifications: authorId -> list of Notification
    private val notifications = mutableMapOf<Id, MutableList<Notification>>()

    // Direct author subscriptions: authorId -> list of targetPassageIds
    private val authorSubscriptions = mutableMapOf<Id, MutableList<Id>>()

    // Credentials: passageId -> passphrase
    private val credentials = mutableMapOf<Id, String>()

    // Chat sessions: chatId -> ChatSession
    private val chatSessions = mutableMapOf<Id, ChatSession>()

    // Unencrypted persistent chat messages: chatId -> list of ChatMessage
    private val unencryptedChatMessages = mutableMapOf<Id, MutableList<ChatMessage>>()

    // Encrypted ephemeral chat messages (NEVER persisted to disk): chatId -> list of ChatMessage
    private val ephemeralEncryptedMailbox = mutableMapOf<Id, MutableList<ChatMessage>>()

    // Encrypted cloud chat messages (persisted in cloud database): chatId -> list of ChatMessage
    private val encryptedCloudChatMessages = mutableMapOf<Id, MutableList<ChatMessage>>()

    // Keystore payloads: authorId -> KeyStorePayload
    private val authorKeyStores = mutableMapOf<Id, KeyStorePayload>()

    // Author settings & inactivity configuration: authorId -> AuthorSettings
    private val authorSettingsMap = mutableMapOf<Id, AuthorSettings>()

    // Author activity logs
    private val authorActivityLogs = mutableListOf<AuthorActivityLog>()

    init {
        val defaultAestheticTags = listOf(
            "stoicism", "philosophy", "wisdom", "sovereign", "knowledge",
            "dag", "citation", "epistemology", "aphorism", "truth",
            "ethics", "logic", "reason", "virtue", "mind", "literature"
        )
        for (tag in defaultAestheticTags) {
            globalTags[tag] = Tag(name = tag, count = 1)
        }
    }

    private fun recordTagsInternal(tags: List<String>) {
        for (raw in tags) {
            val norm = Tag.normalize(raw)
            if (norm.isNotBlank()) {
                val existing = globalTags[norm]
                if (existing != null) {
                    globalTags[norm] = existing.copy(count = existing.count + 1)
                } else {
                    globalTags[norm] = Tag(name = norm, count = 1)
                }
            }
        }
    }

    // ================= PassageRepository =================

    override suspend fun get(id: Id): Passage? = mutex.withLock {
        passages[id]
    }

    override suspend fun getAll(limit: Int, offset: Int): List<Passage> = mutex.withLock {
        passages.values.toList().drop(offset).take(limit)
    }

    override suspend fun save(passage: Passage, parentId: Id?) = mutex.withLock {
        passages[passage.id] = passage
        if (parentId != null) {
            serverParentMap[passage.id] = parentId
        }

        // Initialize edge sets if absent
        outgoingSubscriptions.getOrPut(passage.id) { mutableListOf() }
        incomingSubscribers.getOrPut(passage.id) { mutableListOf() }

        // Sync subscriptions
        for (targetId in passage.subscribedToIds) {
            val targets = outgoingSubscriptions.getOrPut(passage.id) { mutableListOf() }
            if (!targets.contains(targetId)) targets.add(targetId)

            val sources = incomingSubscribers.getOrPut(targetId) { mutableListOf() }
            if (!sources.contains(passage.id)) sources.add(passage.id)
        }
    }

    override suspend fun delete(id: Id): Boolean = mutex.withLock {
        deleteInternal(id)
    }

    private fun deleteInternal(id: Id): Boolean {
        val passage = passages[id] ?: return false
        if (passage.isRecordable || passage.type == PassageType.RECORDABLE) {
            return false
        }

        passages.remove(id)
        serverParentMap.remove(id)
        billboard.remove(id)

        // Remove from outgoing edges
        val targets = outgoingSubscriptions.remove(id) ?: emptyList()
        for (target in targets) {
            incomingSubscribers[target]?.remove(id)
        }

        // Remove from incoming edges
        val sources = incomingSubscribers.remove(id) ?: emptyList()
        for (source in sources) {
            outgoingSubscriptions[source]?.remove(id)
        }

        return true
    }

    override suspend fun isRecordable(id: Id): Boolean = mutex.withLock {
        val p = passages[id] ?: return false
        p.isRecordable || p.type == PassageType.RECORDABLE
    }

    override suspend fun getParentId(id: Id): Id? = mutex.withLock {
        serverParentMap[id]
    }

    override suspend fun search(query: String, limit: Int): List<Passage> = mutex.withLock {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()

        passages.values.filter { passage ->
            passage.content.lowercase().contains(q) ||
                    passage.tags.any { it.lowercase().contains(q) } ||
                    passage.id.value.lowercase().contains(q)
        }.take(limit)
    }

    override suspend fun getByTag(tag: String, limit: Int): List<Passage> = mutex.withLock {
        val normalized = Tag.normalize(tag)
        passages.values.filter { p ->
            p.tags.any { Tag.normalize(it) == normalized }
        }.take(limit)
    }

    override suspend fun addSubscription(sourceId: Id, targetId: Id): Boolean = mutex.withLock {
        val source = passages[sourceId] ?: return false
        val target = passages[targetId] ?: return false

        val outgoing = outgoingSubscriptions.getOrPut(sourceId) { mutableListOf() }
        if (!outgoing.contains(targetId)) outgoing.add(targetId)

        val incoming = incomingSubscribers.getOrPut(targetId) { mutableListOf() }
        if (!incoming.contains(sourceId)) incoming.add(sourceId)

        // Update passage models with updated lists
        val updatedSource = when (source) {
            is StandardPassage -> source.copy(subscribedToIds = outgoing.toList())
            is AuthorPassage -> source.copy(subscribedToIds = outgoing.toList())
            is QuotePassage -> source.copy(subscribedToIds = outgoing.toList())
            is CommentPassage -> source.copy(subscribedToIds = outgoing.toList())
            is RecordablePassage -> source.copy(subscribedToIds = outgoing.toList())
            is SourcePassage -> source.copy(subscribedToIds = outgoing.toList())
        }
        passages[sourceId] = updatedSource

        val updatedTarget = when (target) {
            is StandardPassage -> target.copy(subscriberIds = incoming.toList())
            is AuthorPassage -> target.copy(subscriberIds = incoming.toList())
            is QuotePassage -> target.copy(subscriberIds = incoming.toList())
            is CommentPassage -> target.copy(subscriberIds = incoming.toList())
            is RecordablePassage -> target.copy(subscriberIds = incoming.toList())
            is SourcePassage -> target.copy(subscriberIds = incoming.toList())
        }
        passages[targetId] = updatedTarget

        true
    }

    override suspend fun removeSubscription(sourceId: Id, targetId: Id): Boolean = mutex.withLock {
        // If target or source is recordable, cannot detach
        val target = passages[targetId]
        if (target != null && (target.isRecordable || target.type == PassageType.RECORDABLE)) {
            return false
        }

        outgoingSubscriptions[sourceId]?.remove(targetId)
        incomingSubscribers[targetId]?.remove(sourceId)

        val source = passages[sourceId]
        if (source != null) {
            val updated = when (source) {
                is StandardPassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
                is AuthorPassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
                is QuotePassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
                is CommentPassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
                is RecordablePassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
                is SourcePassage -> source.copy(subscribedToIds = outgoingSubscriptions[sourceId]?.toList() ?: emptyList())
            }
            passages[sourceId] = updated
        }

        if (target != null) {
            val updated = when (target) {
                is StandardPassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
                is AuthorPassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
                is QuotePassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
                is CommentPassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
                is RecordablePassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
                is SourcePassage -> target.copy(subscriberIds = incomingSubscribers[targetId]?.toList() ?: emptyList())
            }
            passages[targetId] = updated
        }

        true
    }

    override suspend fun getSubscribedTo(id: Id): List<Id> = mutex.withLock {
        outgoingSubscriptions[id]?.toList() ?: emptyList()
    }

    override suspend fun getSubscribers(id: Id): List<Id> = mutex.withLock {
        incomingSubscribers[id]?.toList() ?: emptyList()
    }

    // ================= BillboardRepository =================

    override suspend fun getAdvertised(limit: Int, offset: Int, tag: String?): List<BillboardItem> = mutex.withLock {
        var items = billboard.values.toList().sortedByDescending { it.advertisedAt }
        if (tag != null && tag.isNotBlank()) {
            val norm = Tag.normalize(tag)
            items = items.filter { item -> item.passage.tags.any { Tag.normalize(it) == norm } }
        }
        items.drop(offset).take(limit)
    }

    override suspend fun advertise(passageId: Id, isOrphan: Boolean): Boolean = mutex.withLock {
        val passage = passages[passageId] ?: return false
        billboard[passageId] = BillboardItem(
            passage = passage,
            isOrphan = isOrphan,
            advertisedAt = passage.createdAt
        )
        true
    }

    override suspend fun unadvertise(passageId: Id): Boolean = mutex.withLock {
        val item = billboard[passageId] ?: return false
        if (item.isOrphan) {
            return false // Orphans can NEVER be unadvertised
        }
        billboard.remove(passageId)
        true
    }

    override suspend fun isAdvertised(passageId: Id): Boolean = mutex.withLock {
        billboard.containsKey(passageId)
    }

    override suspend fun isOrphan(passageId: Id): Boolean = mutex.withLock {
        val p = passages[passageId]
        if (p?.type == PassageType.AUTHOR) return@withLock false
        billboard[passageId]?.isOrphan == true || serverParentMap[passageId] == null
    }

    override suspend fun getAdvertisedPassageIds(authorId: Id): Set<Id> = mutex.withLock {
        val ownedIds = serverParentMap.filter { it.value == authorId }.keys + authorId
        billboard.keys.filter { it in ownedIds }.toSet()
    }

    override suspend fun setPassageTypeAdvertisement(authorId: Id, type: PassageType?, advertise: Boolean): Int = mutex.withLock {
        val ownedIds = serverParentMap.filter { it.value == authorId }.keys + authorId
        val candidates = ownedIds.mapNotNull { passages[it] }.filter { type == null || it.type == type }
        var count = 0
        for (p in candidates) {
            if (advertise) {
                if (!billboard.containsKey(p.id)) {
                    billboard[p.id] = BillboardItem(passage = p, isOrphan = false, advertisedAt = currentTime())
                    count++
                }
            } else {
                val item = billboard[p.id]
                if (item != null && !item.isOrphan) {
                    billboard.remove(p.id)
                    count++
                }
            }
        }
        count
    }

    override suspend fun getCreatedByAuthor(authorId: Id): List<Passage> = mutex.withLock {
        passages.values.filter { passage ->
            passage.id == authorId || serverParentMap[passage.id] == authorId
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun getFeed(authorId: Id, depth: Int, limit: Int, offset: Int): List<Passage> = mutex.withLock {
        val ownedIds = serverParentMap.filter { it.value == authorId }.keys.toSet()
        val directSubscribed = (authorSubscriptions[authorId] ?: emptyList()).toSet()
        val graphSubscribed = (ownedIds + authorId).flatMap { outgoingSubscriptions[it] ?: emptyList() }.toSet()

        // Initial targets S0 (excluding authorId itself)
        var currentTargets = (directSubscribed + graphSubscribed).filter { it != authorId }.toSet()
        val allDiscoveredIds = currentTargets.toMutableSet()

        val safeDepth = depth.coerceIn(1, 5)
        for (d in 1..safeDepth) {
            val nextTargets = mutableSetOf<Id>()
            for (target in currentTargets) {
                // 1. Passages created by this target (if target is an author or parent)
                val createdChildren = serverParentMap.filter { it.value == target }.keys
                // 2. Passages attaching to / citing this target
                val citingChildren = incomingSubscribers[target] ?: emptyList()

                for (child in (createdChildren + citingChildren)) {
                    if (child != authorId && allDiscoveredIds.add(child)) {
                        nextTargets.add(child)
                    }
                }
            }
            currentTargets = nextTargets
            if (currentTargets.isEmpty()) break
        }

        allDiscoveredIds.mapNotNull { passages[it] }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
    }

    suspend fun addAuthorSubscription(authorId: Id, targetPassageId: Id): Boolean = mutex.withLock {
        val list = authorSubscriptions.getOrPut(authorId) { mutableListOf() }
        if (!list.contains(targetPassageId)) {
            list.add(targetPassageId)
            true
        } else false
    }

    suspend fun removeAuthorSubscription(authorId: Id, targetPassageId: Id): Boolean = mutex.withLock {
        authorSubscriptions[authorId]?.remove(targetPassageId) ?: false
    }

    suspend fun getAuthorSubscriptions(authorId: Id): List<Id> = mutex.withLock {
        authorSubscriptions[authorId]?.toList() ?: emptyList()
    }

    suspend fun isAuthorSubscribed(authorId: Id, targetPassageId: Id): Boolean = mutex.withLock {
        authorSubscriptions[authorId]?.contains(targetPassageId) ?: false
    }

    // ================= TagRepository =================

    override suspend fun recordTags(tags: List<String>) = mutex.withLock {
        recordTagsInternal(tags)
    }

    override suspend fun getTopTags(limit: Int): List<Tag> = mutex.withLock {
        globalTags.values.sortedByDescending { it.count }.take(limit)
    }

    override suspend fun suggestTags(prefix: String, limit: Int): List<Tag> = mutex.withLock {
        val p = Tag.normalize(prefix)
        if (p.isBlank()) return@withLock globalTags.values.sortedByDescending { it.count }.take(limit)
        val prefixMatches = globalTags.values.filter { it.name.startsWith(p) }.sortedByDescending { it.count }
        val containsMatches = globalTags.values.filter { !it.name.startsWith(p) && it.name.contains(p) }.sortedByDescending { it.count }
        (prefixMatches + containsMatches).take(limit)
    }

    // ================= NotificationRepository =================

    override suspend fun getNotifications(authorId: Id, limit: Int): List<Notification> = mutex.withLock {
        val ownedIds = serverParentMap.filter { it.value == authorId }.keys + authorId
        val allNotifs = ownedIds.flatMap { notifications[it] ?: emptyList() }
        allNotifs.sortedByDescending { it.createdAt }.take(limit)
    }

    override suspend fun addNotification(notification: Notification) {
        mutex.withLock {
            val list = notifications.getOrPut(notification.targetPassageId) { mutableListOf() }
            list.add(notification)
        }
    }

    override suspend fun markAsRead(notificationId: Id) = mutex.withLock {
        for (list in notifications.values) {
            val idx = list.indexOfFirst { it.id == notificationId }
            if (idx != -1) {
                list[idx] = list[idx].copy(isRead = true)
                break
            }
        }
    }

    // ================= Credentials =================

    suspend fun saveCredentials(credentials: Credentials) = mutex.withLock {
        this.credentials[credentials.passageId] = credentials.passphrase
    }

    suspend fun verifyCredentials(passageId: Id, passphrase: String): Boolean = mutex.withLock {
        this.credentials[passageId] == passphrase
    }

    suspend fun updatePassphrase(passageId: Id, oldPassphrase: String, newPassphrase: String): Boolean = mutex.withLock {
        if (this.credentials[passageId] == oldPassphrase) {
            this.credentials[passageId] = newPassphrase
            true
        } else {
            false
        }
    }

    // ================= ChatRepository =================

    override suspend fun createChatSession(
        initiatorId: Id,
        recipientId: Id,
        type: ChatType,
        initiatorPublicKey: String?
    ): ChatSession = mutex.withLock {
        // Enforce: only 3 types and only one instance of each type between two authors
        val existing = chatSessions.values.firstOrNull { session ->
            ((session.initiatorId == initiatorId && session.recipientId == recipientId) ||
             (session.initiatorId == recipientId && session.recipientId == initiatorId)) &&
            session.type == type
        }
        if (existing != null) {
            if (initiatorPublicKey != null && existing.initiatorId == initiatorId && existing.initiatorPublicKey != initiatorPublicKey) {
                val updated = existing.copy(initiatorPublicKey = initiatorPublicKey, updatedAt = currentTime())
                chatSessions[existing.id] = updated
                return@withLock updated
            }
            return@withLock existing
        }

        val now = currentTime()
        val chatId = Id("chat-" + connects.quotes.security.PlatformSecureRandom.nextLong().toString(16).removePrefix("-"))
        val session = ChatSession(
            id = chatId,
            initiatorId = initiatorId,
            recipientId = recipientId,
            type = type,
            status = ChatStatus.PENDING,
            initiatorPublicKey = initiatorPublicKey,
            recipientPublicKey = null,
            createdAt = now,
            updatedAt = now
        )
        chatSessions[chatId] = session
        session
    }

    override suspend fun getChatSession(chatId: Id): ChatSession? = mutex.withLock {
        chatSessions[chatId]
    }

    override suspend fun getChatSessionsForAuthor(authorId: Id): List<ChatSession> = mutex.withLock {
        chatSessions.values
            .filter { it.initiatorId == authorId || it.recipientId == authorId }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun updateChatStatus(
        chatId: Id,
        status: ChatStatus,
        recipientPublicKey: String?
    ): Boolean = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock false
        val now = currentTime()
        val updated = session.copy(
            status = status,
            recipientPublicKey = recipientPublicKey ?: session.recipientPublicKey,
            updatedAt = now
        )
        chatSessions[chatId] = updated
        true
    }

    override suspend fun sendChatMessage(message: ChatMessage): Boolean = mutex.withLock {
        val session = chatSessions[message.chatId] ?: return@withLock false
        if (session.status != ChatStatus.ACCEPTED) return@withLock false

        val decision = connects.quotes.security.ChatSecurityPolicy.validateSend(session, message)
        if (decision is connects.quotes.security.SecurityDecision.Rejected) {
            return@withLock false
        }

        chatSessions[message.chatId] = session.copy(updatedAt = currentTime())

        if (session.type == ChatType.ENCRYPTED) {
            // Strictly held ONLY in memory (ephemeral relay)
            val list = ephemeralEncryptedMailbox.getOrPut(message.chatId) { mutableListOf() }
            list.add(message)
        } else if (session.type == ChatType.ENCRYPTED_CLOUD) {
            // Stored in cloud database (ciphertext only)
            val list = encryptedCloudChatMessages.getOrPut(message.chatId) { mutableListOf() }
            list.add(message)
        } else {
            // Stored in persistent repository
            val list = unencryptedChatMessages.getOrPut(message.chatId) { mutableListOf() }
            list.add(message)
        }
        true
    }

    override suspend fun getChatMessages(chatId: Id, authorId: Id): List<ChatMessage> = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock emptyList()
        if (session.initiatorId != authorId && session.recipientId != authorId) {
            return@withLock emptyList()
        }

        when (session.type) {
            ChatType.ENCRYPTED -> ephemeralEncryptedMailbox[chatId]?.toList() ?: emptyList()
            ChatType.ENCRYPTED_CLOUD -> encryptedCloudChatMessages[chatId]?.toList() ?: emptyList()
            ChatType.UNENCRYPTED -> unencryptedChatMessages[chatId]?.toList() ?: emptyList()
        }
    }

    override suspend fun renewChatKey(chatId: Id, authorId: Id, newPublicKey: String): ChatSession? = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock null
        val now = currentTime()
        val updated = if (session.initiatorId == authorId) {
            session.copy(initiatorPublicKey = newPublicKey, updatedAt = now)
        } else if (session.recipientId == authorId) {
            session.copy(recipientPublicKey = newPublicKey, updatedAt = now)
        } else {
            return@withLock null
        }
        chatSessions[chatId] = updated
        updated
    }

    override suspend fun backupKeyStore(keyStore: KeyStorePayload): Boolean = mutex.withLock {
        authorKeyStores[keyStore.authorId] = keyStore
        true
    }

    override suspend fun getKeyStore(authorId: Id): KeyStorePayload? = mutex.withLock {
        authorKeyStores[authorId]
    }

    override suspend fun deleteKeyStore(authorId: Id): Boolean = mutex.withLock {
        authorKeyStores.remove(authorId) != null
    }

    override suspend fun proposeSharedAlias(chatId: Id, authorId: Id, alias: String): ChatSession? = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock null
        if (session.initiatorId != authorId && session.recipientId != authorId) return@withLock null
        val updated = session.copy(
            proposedSharedAlias = alias,
            aliasProposerId = authorId,
            updatedAt = currentTime()
        )
        chatSessions[chatId] = updated
        updated
    }

    override suspend fun acceptSharedAlias(chatId: Id, authorId: Id): ChatSession? = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock null
        if (session.initiatorId != authorId && session.recipientId != authorId) return@withLock null
        if (session.aliasProposerId == authorId || session.proposedSharedAlias == null) return@withLock null
        val updated = session.copy(
            sharedAlias = session.proposedSharedAlias,
            proposedSharedAlias = null,
            aliasProposerId = null,
            updatedAt = currentTime()
        )
        chatSessions[chatId] = updated
        updated
    }

    override suspend fun deleteChatSession(chatId: Id, authorId: Id): Boolean = mutex.withLock {
        val session = chatSessions[chatId] ?: return@withLock false
        if (session.initiatorId != authorId && session.recipientId != authorId) return@withLock false
        chatSessions.remove(chatId)
        ephemeralEncryptedMailbox.remove(chatId)
        encryptedCloudChatMessages.remove(chatId)
        unencryptedChatMessages.remove(chatId)
        true
    }

    // ================= AuthorRepository =================

    override suspend fun getAuthorSettings(authorId: Id): AuthorSettings? = mutex.withLock {
        authorSettingsMap[authorId] ?: AuthorSettings(
            authorId = authorId,
            lastActiveAt = currentTime(),
            inactivityPeriodDays = 30,
            discardAction = AuthorDiscardAction.ORPHAN
        )
    }

    override suspend fun saveAuthorSettings(settings: AuthorSettings): Boolean = mutex.withLock {
        authorSettingsMap[settings.authorId] = settings
        // Synchronize inactivity tag on Author passage if present
        val p = passages[settings.authorId]
        if (p is AuthorPassage) {
            val updatedTags = p.tags.filterNot { it.startsWith("inactivity:") }.toMutableList()
            updatedTags.add(settings.inactivityTag)
            passages[settings.authorId] = p.copy(tags = updatedTags)
        }
        true
    }

    override suspend fun logAuthorActivity(authorId: Id, activityType: String): Boolean = mutex.withLock {
        val now = currentTime()
        val current = authorSettingsMap[authorId] ?: AuthorSettings(
            authorId = authorId,
            lastActiveAt = now,
            inactivityPeriodDays = 30,
            discardAction = AuthorDiscardAction.ORPHAN
        )
        authorSettingsMap[authorId] = current.copy(lastActiveAt = now)

        val logId = "act-" + authorActivityLogs.size + "-" + now
        authorActivityLogs.add(AuthorActivityLog(logId, authorId, activityType, now))
        true
    }

    override suspend fun getAuthorActivityLogs(authorId: Id, limit: Int): List<AuthorActivityLog> = mutex.withLock {
        authorActivityLogs
            .filter { it.authorId == authorId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun performInactivityCheck(now: Long): InactivityCheckResult = mutex.withLock {
        var purgedAuthors = 0
        var deletedChats = 0
        var orphanedPassages = 0
        var deletedPassages = 0
        val processedIds = mutableListOf<String>()

        val inactiveAuthors = authorSettingsMap.values.filter { it.isExpired(now) }

        for (settings in inactiveAuthors) {
            val authorId = settings.authorId
            processedIds.add(authorId.value)
            purgedAuthors++

            // 1. Delete chats from his side
            val unencCount = unencryptedChatMessages.values.sumOf { msgs ->
                val before = msgs.size
                msgs.removeAll { it.senderId == authorId }
                before - msgs.size
            }
            val cloudCount = encryptedCloudChatMessages.values.sumOf { msgs ->
                val before = msgs.size
                msgs.removeAll { it.senderId == authorId }
                before - msgs.size
            }
            val ephemCount = ephemeralEncryptedMailbox.values.sumOf { msgs ->
                val before = msgs.size
                msgs.removeAll { it.senderId == authorId }
                before - msgs.size
            }
            deletedChats += (unencCount + cloudCount + ephemCount)

            // 2. Passages discard action
            val ownedPassageIds = serverParentMap.entries
                .filter { it.value == authorId && it.key != authorId }
                .map { it.key }

            for (pId in ownedPassageIds) {
                val p = passages[pId] ?: continue
                val isAdv = billboard.containsKey(pId)

                if (!isAdv) {
                    // Unadvertised passages
                    if (settings.discardAction == AuthorDiscardAction.ORPHAN || p.isRecordable) {
                        serverParentMap.remove(pId)
                        billboard[pId] = BillboardItem(p, isOrphan = true, advertisedAt = now)
                        orphanedPassages++
                    } else {
                        // DELETE
                        deleteInternal(pId)
                        deletedPassages++
                    }
                } else {
                    // Already advertised passages
                    if (settings.discardAction == AuthorDiscardAction.ORPHAN || p.isRecordable) {
                        serverParentMap.remove(pId)
                        val currB = billboard[pId]
                        if (currB != null) {
                            billboard[pId] = currB.copy(isOrphan = true)
                        }
                        orphanedPassages++
                    } else {
                        deleteInternal(pId)
                        deletedPassages++
                    }
                }
            }

            // Cleanup author record & credentials
            credentials.remove(authorId)
            authorKeyStores.remove(authorId)
            authorSettingsMap.remove(authorId)
            if (settings.discardAction == AuthorDiscardAction.DELETE) {
                deleteInternal(authorId)
                deletedPassages++
            }
        }

        InactivityCheckResult(
            purgedAuthorsCount = purgedAuthors,
            deletedChatsCount = deletedChats,
            orphanedPassagesCount = orphanedPassages,
            deletedPassagesCount = deletedPassages,
            processedAuthorIds = processedIds,
            timestamp = now
        )
    }

    override suspend fun executeAuthorDiscard(authorId: Id, explicitAction: AuthorDiscardAction?): Boolean = mutex.withLock {
        val settings = authorSettingsMap[authorId] ?: AuthorSettings(
            authorId = authorId,
            lastActiveAt = currentTime(),
            inactivityPeriodDays = 30,
            discardAction = AuthorDiscardAction.ORPHAN
        )
        val action = explicitAction ?: settings.discardAction

        // 1. Delete chats from his side
        unencryptedChatMessages.values.forEach { msgs -> msgs.removeAll { it.senderId == authorId } }
        encryptedCloudChatMessages.values.forEach { msgs -> msgs.removeAll { it.senderId == authorId } }
        ephemeralEncryptedMailbox.values.forEach { msgs -> msgs.removeAll { it.senderId == authorId } }

        // 2. Passages discard action
        val ownedPassageIds = serverParentMap.entries
            .filter { it.value == authorId && it.key != authorId }
            .map { it.key }

        for (pId in ownedPassageIds) {
            val p = passages[pId] ?: continue
            val isAdv = billboard.containsKey(pId)

            if (!isAdv) {
                // Unadvertised
                if (action == AuthorDiscardAction.ORPHAN || p.isRecordable) {
                    serverParentMap.remove(pId)
                    billboard[pId] = BillboardItem(p, isOrphan = true, advertisedAt = currentTime())
                } else {
                    deleteInternal(pId)
                }
            } else {
                if (action == AuthorDiscardAction.ORPHAN || p.isRecordable) {
                    serverParentMap.remove(pId)
                    val currB = billboard[pId]
                    if (currB != null) {
                        billboard[pId] = currB.copy(isOrphan = true)
                    }
                } else {
                    deleteInternal(pId)
                }
            }
        }

        // Cleanup
        credentials.remove(authorId)
        authorKeyStores.remove(authorId)
        authorSettingsMap.remove(authorId)
        authorActivityLogs.removeAll { it.authorId == authorId }

        if (action == AuthorDiscardAction.DELETE) {
            deleteInternal(authorId)
        }
        true
    }

    override suspend fun resolveAuthorByUsernameOrId(query: String): Id? = mutex.withLock {
        val clean = query.trim().removePrefix("@")
        // Check if query is exact author UUID (sovereign lookup is always valid)
        val byId = Id(clean)
        if (passages[byId] is connects.quotes.model.AuthorPassage) {
            return@withLock byId
        }
        // Check author settings username (only if alias search enabled)
        val matchSettings = authorSettingsMap.entries.firstOrNull {
            it.value.username?.equals(clean, ignoreCase = true) == true && it.value.aliasSearchEnabled
        }
        if (matchSettings != null) {
            return@withLock matchSettings.key
        }
        // Check author moniker (only if alias search enabled)
        val matchMoniker = passages.values.filterIsInstance<connects.quotes.model.AuthorPassage>().firstOrNull { authorPassage ->
            val settings = authorSettingsMap[authorPassage.id]
            val aliasEnabled = settings?.aliasSearchEnabled ?: true
            aliasEnabled && authorPassage.moniker.equals(clean, ignoreCase = true)
        }
        matchMoniker?.id
    }

    override suspend fun checkUsernameAvailable(username: String, currentAuthorId: Id?): Boolean = mutex.withLock {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return@withLock false
        authorSettingsMap.entries.none { (id, settings) ->
            id != currentAuthorId && settings.username?.equals(clean, ignoreCase = true) == true
        }
    }

    private fun currentTime(): Long = 1774343000000L + (++chatCounter * 1000L)
    private var chatCounter = 1L
}
