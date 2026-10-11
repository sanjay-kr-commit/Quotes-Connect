package connects.quotes.chat

import connects.quotes.model.ChatMessage
import connects.quotes.model.ChatSession
import connects.quotes.model.ChatIntentCode
import connects.quotes.model.Id
import connects.quotes.model.LiveChatPacket
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * High-performance, intent-driven reactive Hub for live TCP chat streaming and push notifications.
 *
 * Implements an intent processing and loopback mechanism where each TCP connection has a unique UUID
 * and a filter lambda. When events occur (messages, approvals, deletions, typing, notifications),
 * the server invokes the subscriber lambdas to route intents with sub-millisecond latency.
 */
object LiveChatHub {
    private val logger = LoggerFactory.getLogger(LiveChatHub::class.java)

    /**
     * Intent subscriber descriptor.
     * Contains the connection UUID and the lambda function accepting LiveChatPacket intents.
     */
    data class IntentSubscriber(
        val connectionId: String,                    // Unique TCP connection UUID
        val authorId: String,                        // Sovereign Author UUID
        val chatId: String? = null,                  // Optional chat conversation scope
        val registeredIntents: Set<Int> = emptySet(),// Set of registered intent codes (empty = all)
        val channel: Channel<LiveChatPacket>,        // TCP streaming channel
        val predicate: (LiveChatPacket) -> Boolean   // The lambda accepting intent!
    )

    // Central Hash Map: UUID -> IntentSubscriber (containing the lambda accepting intent)
    private val intentSubscribers = ConcurrentHashMap<String, IntentSubscriber>()

    // Direct subscriber sets for backwards-compatible targeted routing
    data class ChatSubscriber(
        val authorId: String,
        val channel: Channel<LiveChatPacket>
    )
    private val chatSubscribers = ConcurrentHashMap<String, CopyOnWriteArraySet<ChatSubscriber>>()
    private val notificationSubscribers = ConcurrentHashMap<String, CopyOnWriteArraySet<Channel<LiveChatPacket>>>()
    private val globalEventListeners = CopyOnWriteArraySet<Channel<LiveChatPacket>>()

    /**
     * Registers an intent-based TCP subscriber with its connection UUID and lambda.
     * Returns the registered lambda function.
     */
    fun registerIntentSubscriber(
        connectionId: String = UUID.randomUUID().toString(),
        authorId: String,
        chatId: String? = null,
        intents: Set<Int> = emptySet(),
        channel: Channel<LiveChatPacket>,
        customFilter: ((LiveChatPacket) -> Boolean)? = null
    ): (LiveChatPacket) -> Boolean {
        val lambda: (LiveChatPacket) -> Boolean = customFilter ?: { packet ->
            // 1. Intent filter check (PING always allowed)
            val matchesIntent = intents.isEmpty() || packet.intentCode == 0 || packet.intentCode in intents || packet.intentCode == ChatIntentCode.PING

            // 2. Author relevance check
            val matchesAuthor = when (packet.intentCode) {
                ChatIntentCode.MESSAGE -> packet.message?.recipientId?.value == authorId || packet.message?.senderId?.value == authorId
                ChatIntentCode.STATUS_CHANGED -> packet.session?.initiatorId?.value == authorId || packet.session?.recipientId?.value == authorId || packet.senderId == authorId
                ChatIntentCode.NOTIFICATION -> packet.senderId != authorId // do not self-notify
                ChatIntentCode.TYPING -> (packet.senderId != authorId) && (chatId == null || packet.chatId == chatId)
                ChatIntentCode.CHAT_DELETED -> true
                ChatIntentCode.ALIAS_CHANGED -> packet.session?.initiatorId?.value == authorId || packet.session?.recipientId?.value == authorId
                ChatIntentCode.KEY_CHANGED -> packet.session?.initiatorId?.value == authorId || packet.session?.recipientId?.value == authorId
                else -> true
            }

            // 3. Conversation scope check (if tab/window is bound to a specific chat)
            val matchesChat = chatId == null || packet.chatId == null || packet.chatId == chatId

            matchesIntent && matchesAuthor && matchesChat
        }

        val subscriber = IntentSubscriber(
            connectionId = connectionId,
            authorId = authorId,
            chatId = chatId,
            registeredIntents = intents,
            channel = channel,
            predicate = lambda
        )
        intentSubscribers[connectionId] = subscriber
        logger.debug("Registered TCP intent subscriber $connectionId for author $authorId, chatId $chatId, intents $intents")
        return lambda
    }

    /**
     * Unregisters an intent-based TCP subscriber by its connection UUID.
     */
    fun unregisterIntentSubscriber(connectionId: String) {
        val sub = intentSubscribers.remove(connectionId)
        sub?.channel?.close()
        logger.debug("Unregistered TCP intent subscriber $connectionId")
    }

    /**
     * Intent processing and loopback mechanism.
     * Iterates over the UUID -> lambda hash map and executes each subscriber's lambda function.
     */
    fun dispatchIntent(packet: LiveChatPacket) {
        val resolvedPacket = if (packet.intentCode == 0) {
            packet.copy(intentCode = ChatIntentCode.fromType(packet.type))
        } else packet

        intentSubscribers.forEach { (uuid, subscriber) ->
            try {
                if (subscriber.predicate(resolvedPacket)) {
                    subscriber.channel.trySend(resolvedPacket)
                }
            } catch (e: Exception) {
                logger.warn("Error dispatching intent to subscriber $uuid: ${e.message}")
            }
        }
    }

    fun registerChatListener(chatId: String, authorId: String): Channel<LiveChatPacket> {
        val channel = Channel<LiveChatPacket>(Channel.BUFFERED)
        val subscriber = ChatSubscriber(authorId, channel)
        chatSubscribers.computeIfAbsent(chatId) { CopyOnWriteArraySet() }.add(subscriber)
        logger.debug("Registered live chat listener for chat $chatId by author $authorId")
        return channel
    }

    fun unregisterChatListener(chatId: String, subscriberChannel: Channel<LiveChatPacket>) {
        chatSubscribers[chatId]?.removeIf { it.channel == subscriberChannel }
        if (chatSubscribers[chatId]?.isEmpty() == true) {
            chatSubscribers.remove(chatId)
        }
        subscriberChannel.close()
        logger.debug("Unregistered live chat listener for chat $chatId")
    }

    fun registerNotificationListener(authorId: String): Channel<LiveChatPacket> {
        val channel = Channel<LiveChatPacket>(Channel.BUFFERED)
        notificationSubscribers.computeIfAbsent(authorId) { CopyOnWriteArraySet() }.add(channel)
        logger.debug("Registered notification listener for author $authorId")
        return channel
    }

    fun unregisterNotificationListener(authorId: String, channel: Channel<LiveChatPacket>) {
        notificationSubscribers[authorId]?.remove(channel)
        if (notificationSubscribers[authorId]?.isEmpty() == true) {
            notificationSubscribers.remove(authorId)
        }
        channel.close()
        logger.debug("Unregistered notification listener for author $authorId")
    }

    fun broadcastMessage(message: ChatMessage) {
        val chatId = message.chatId.value
        val packet = LiveChatPacket(
            type = "MESSAGE",
            intentCode = ChatIntentCode.MESSAGE,
            chatId = chatId,
            message = message,
            senderId = message.senderId.value,
            timestamp = message.timestamp
        )

        // 1. Deliver to active live chat subscribers
        val activeListeners = chatSubscribers[chatId]
        var recipientHasChatOpen = false

        if (activeListeners != null) {
            for (sub in activeListeners) {
                if (sub.authorId == message.recipientId.value) {
                    recipientHasChatOpen = true
                }
                sub.channel.trySend(packet)
            }
        }

        // 2. If recipient is NOT in this chat right now, deliver in-app notification!
        if (!recipientHasChatOpen) {
            val previewText = if (message.isEncrypted) {
                "[Encrypted Private Citation]"
            } else {
                message.content.take(64)
            }
            val notifPacket = LiveChatPacket(
                type = "NOTIFICATION",
                intentCode = ChatIntentCode.NOTIFICATION,
                chatId = chatId,
                senderId = message.senderId.value,
                text = previewText,
                timestamp = message.timestamp
            )
            val notifChannels = notificationSubscribers[message.recipientId.value]
            if (notifChannels != null) {
                for (ch in notifChannels) {
                    ch.trySend(notifPacket)
                }
            }
            dispatchIntent(notifPacket)
        }

        // 3. Loopback dispatch for all intent subscribers
        dispatchIntent(packet)
    }

    fun broadcastKeyChanged(session: ChatSession, changedAuthorId: Id, newPublicKey: String) {
        val chatId = session.id.value
        val packet = LiveChatPacket(
            type = "KEY_CHANGED",
            intentCode = ChatIntentCode.KEY_CHANGED,
            chatId = chatId,
            session = session,
            senderId = changedAuthorId.value,
            newPublicKey = newPublicKey,
            timestamp = System.currentTimeMillis()
        )

        chatSubscribers[chatId]?.forEach { it.channel.trySend(packet) }
        val counterpartyId = if (session.initiatorId == changedAuthorId) session.recipientId.value else session.initiatorId.value
        notificationSubscribers[counterpartyId]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastChatRequested(session: ChatSession) {
        val chatId = session.id.value
        val packet = LiveChatPacket(
            type = "STATUS_CHANGED",
            intentCode = ChatIntentCode.STATUS_CHANGED,
            chatId = chatId,
            session = session,
            senderId = session.initiatorId.value,
            text = "New chat request from ${session.initiatorId.value}",
            timestamp = System.currentTimeMillis()
        )
        notificationSubscribers[session.recipientId.value]?.forEach { it.trySend(packet) }
        notificationSubscribers[session.initiatorId.value]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastSessionUpdated(session: ChatSession, statusMessage: String? = null) {
        val chatId = session.id.value
        val text = statusMessage ?: if (session.status.name == "ACCEPTED") "Peer accepted chat request" else "Chat status: ${session.status.name}"
        val packet = LiveChatPacket(
            type = "STATUS_CHANGED",
            intentCode = ChatIntentCode.STATUS_CHANGED,
            chatId = chatId,
            session = session,
            senderId = session.recipientId.value,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[chatId]?.forEach { it.channel.trySend(packet) }
        notificationSubscribers[session.initiatorId.value]?.forEach { it.trySend(packet) }
        notificationSubscribers[session.recipientId.value]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastChatDeleted(chatId: String, deletedBy: String, initiatorId: String, recipientId: String) {
        val packet = LiveChatPacket(
            type = "CHAT_DELETED",
            intentCode = ChatIntentCode.CHAT_DELETED,
            chatId = chatId,
            senderId = deletedBy,
            text = "Chat conversation was deleted",
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[chatId]?.forEach { it.channel.trySend(packet) }
        notificationSubscribers[initiatorId]?.forEach { it.trySend(packet) }
        notificationSubscribers[recipientId]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastTyping(chatId: String, senderId: String, isTyping: Boolean) {
        val packet = LiveChatPacket(
            type = "TYPING",
            intentCode = ChatIntentCode.TYPING,
            chatId = chatId,
            senderId = senderId,
            isTyping = isTyping,
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[chatId]?.forEach { it.channel.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastResyncRequest(chatId: String, requesterId: String) {
        val packet = LiveChatPacket(
            type = "RESYNC_REQUEST",
            intentCode = ChatIntentCode.RESYNC_REQUEST,
            chatId = chatId,
            senderId = requesterId,
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[chatId]?.forEach { it.channel.trySend(packet) }
        dispatchIntent(packet)
    }

    // Global graph event listeners for non-destructive instant UI syncing (SSE: /api/events)
    fun registerEventListener(): Channel<LiveChatPacket> {
        val channel = Channel<LiveChatPacket>(Channel.BUFFERED)
        globalEventListeners.add(channel)
        return channel
    }

    fun unregisterEventListener(channel: Channel<LiveChatPacket>) {
        globalEventListeners.remove(channel)
        channel.close()
    }

    fun broadcastGraphChanged(entityType: String, entityId: String) {
        val packet = LiveChatPacket(
            type = "GRAPH_CHANGED",
            intentCode = ChatIntentCode.GRAPH_CHANGED,
            entityType = entityType,
            text = entityId,
            timestamp = System.currentTimeMillis()
        )
        globalEventListeners.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastAliasProposal(session: ChatSession, proposerId: String, alias: String) {
        val packet = LiveChatPacket(
            type = "ALIAS_PROPOSAL",
            intentCode = ChatIntentCode.ALIAS_CHANGED,
            chatId = session.id.value,
            session = session,
            senderId = proposerId,
            alias = alias,
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[session.id.value]?.forEach { it.channel.trySend(packet) }
        val counterparty = if (session.initiatorId.value == proposerId) session.recipientId.value else session.initiatorId.value
        notificationSubscribers[counterparty]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }

    fun broadcastAliasAccepted(session: ChatSession, alias: String) {
        val packet = LiveChatPacket(
            type = "ALIAS_ACCEPTED",
            intentCode = ChatIntentCode.ALIAS_CHANGED,
            chatId = session.id.value,
            session = session,
            alias = alias,
            timestamp = System.currentTimeMillis()
        )
        chatSubscribers[session.id.value]?.forEach { it.channel.trySend(packet) }
        notificationSubscribers[session.initiatorId.value]?.forEach { it.trySend(packet) }
        notificationSubscribers[session.recipientId.value]?.forEach { it.trySend(packet) }
        dispatchIntent(packet)
    }
}
