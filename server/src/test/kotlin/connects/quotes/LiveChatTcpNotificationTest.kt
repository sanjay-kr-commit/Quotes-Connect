package connects.quotes

import connects.quotes.chat.LiveChatHub
import connects.quotes.model.ChatSession
import connects.quotes.model.ChatType
import connects.quotes.model.ChatStatus
import connects.quotes.model.Id
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class LiveChatTcpNotificationTest {

    @Test
    fun testBroadcastChatRequestedSendsNotificationToBothParties() = runBlocking {
        val initiatorId = "author-init-123"
        val recipientId = "author-recip-456"
        val chatId = "chat-req-789"

        val initSub = LiveChatHub.registerNotificationListener(initiatorId)
        val recipSub = LiveChatHub.registerNotificationListener(recipientId)

        try {
            val session = ChatSession(
                id = Id(chatId),
                initiatorId = Id(initiatorId),
                recipientId = Id(recipientId),
                type = ChatType.ENCRYPTED_CLOUD,
                status = ChatStatus.PENDING,
                createdAt = 1000L,
                updatedAt = 1000L
            )

            LiveChatHub.broadcastChatRequested(session)

            withTimeout(2000L) {
                val initPacket = initSub.receive()
                assertEquals("STATUS_CHANGED", initPacket.type)
                assertEquals(chatId, initPacket.chatId)
                assertEquals(initiatorId, initPacket.senderId)
                assertEquals(session, initPacket.session)

                val recipPacket = recipSub.receive()
                assertEquals("STATUS_CHANGED", recipPacket.type)
                assertEquals(chatId, recipPacket.chatId)
                assertEquals(initiatorId, recipPacket.senderId)
                assertEquals("New chat request from $initiatorId", recipPacket.text)
                assertEquals(session, recipPacket.session)
            }
        } finally {
            LiveChatHub.unregisterNotificationListener(initiatorId, initSub)
            LiveChatHub.unregisterNotificationListener(recipientId, recipSub)
        }
    }

    @Test
    fun testBroadcastChatDeletedSendsToRoomAndBothNotificationStreams() = runBlocking {
        val chatId = "chat-del-999"
        val deletedBy = "author-deleter-1"
        val initiatorId = "author-deleter-1"
        val recipientId = "author-peer-2"

        val roomSub = LiveChatHub.registerChatListener(chatId, initiatorId)
        val initNotifSub = LiveChatHub.registerNotificationListener(initiatorId)
        val recipNotifSub = LiveChatHub.registerNotificationListener(recipientId)

        try {
            LiveChatHub.broadcastChatDeleted(
                chatId = chatId,
                deletedBy = deletedBy,
                initiatorId = initiatorId,
                recipientId = recipientId
            )

            withTimeout(2000L) {
                val roomPacket = roomSub.receive()
                assertEquals("CHAT_DELETED", roomPacket.type)
                assertEquals(chatId, roomPacket.chatId)
                assertEquals(deletedBy, roomPacket.senderId)

                val initPacket = initNotifSub.receive()
                assertEquals("CHAT_DELETED", initPacket.type)
                assertEquals(chatId, initPacket.chatId)

                val recipPacket = recipNotifSub.receive()
                assertEquals("CHAT_DELETED", recipPacket.type)
                assertEquals(chatId, recipPacket.chatId)
            }
        } finally {
            LiveChatHub.unregisterChatListener(chatId, roomSub)
            LiveChatHub.unregisterNotificationListener(initiatorId, initNotifSub)
            LiveChatHub.unregisterNotificationListener(recipientId, recipNotifSub)
        }
    }

    @Test
    fun testBroadcastSessionUpdatedSendsNotificationWithStatusText() = runBlocking {
        val initiatorId = "author-a-1"
        val recipientId = "author-b-2"
        val chatId = "chat-acc-3"

        val initSub = LiveChatHub.registerNotificationListener(initiatorId)
        val recipSub = LiveChatHub.registerNotificationListener(recipientId)

        try {
            val session = ChatSession(
                id = Id(chatId),
                initiatorId = Id(initiatorId),
                recipientId = Id(recipientId),
                type = ChatType.ENCRYPTED_CLOUD,
                status = ChatStatus.ACCEPTED,
                createdAt = 1000L,
                updatedAt = 2000L
            )

            LiveChatHub.broadcastSessionUpdated(session, "Peer accepted chat request")

            withTimeout(2000L) {
                val initPacket = initSub.receive()
                assertEquals("STATUS_CHANGED", initPacket.type)
                assertEquals("Peer accepted chat request", initPacket.text)
                assertEquals(session, initPacket.session)

                val recipPacket = recipSub.receive()
                assertEquals("STATUS_CHANGED", recipPacket.type)
                assertEquals("Peer accepted chat request", recipPacket.text)
                assertEquals(session, recipPacket.session)
            }
        } finally {
            LiveChatHub.unregisterNotificationListener(initiatorId, initSub)
            LiveChatHub.unregisterNotificationListener(recipientId, recipSub)
        }
    }

    @Test
    fun testIntentSubscriberOnlyReceivesRegisteredIntents() = runBlocking {
        val authorId = "author-intent-test-1"
        val peerId = "author-intent-test-2"
        val chatId = "chat-intent-777"
        val connectionId = java.util.UUID.randomUUID().toString()
        val channel = kotlinx.coroutines.channels.Channel<connects.quotes.model.LiveChatPacket>(kotlinx.coroutines.channels.Channel.BUFFERED)

        // Register for MESSAGE (2) and STATUS_CHANGED (1) only - NOT TYPING (3)
        val registeredIntents = setOf(connects.quotes.model.ChatIntentCode.MESSAGE, connects.quotes.model.ChatIntentCode.STATUS_CHANGED)
        LiveChatHub.registerIntentSubscriber(
            connectionId = connectionId,
            authorId = authorId,
            chatId = chatId,
            intents = registeredIntents,
            channel = channel
        )

        try {
            // 1. Broadcast TYPING intent (code 3) - should NOT be delivered
            LiveChatHub.broadcastTyping(chatId, isTyping = true, senderId = peerId)
            val nonMatchingResult = channel.tryReceive()
            assertTrue(nonMatchingResult.isFailure, "Typing packet should have been filtered out by registered intents")

            // 2. Broadcast MESSAGE intent (code 2) - should be delivered
            val chatMsg = connects.quotes.model.ChatMessage(
                id = Id("msg-intent-1"),
                chatId = Id(chatId),
                senderId = Id(peerId),
                recipientId = Id(authorId),
                content = "Granular intent message",
                isEncrypted = false,
                timestamp = System.currentTimeMillis()
            )
            LiveChatHub.broadcastMessage(chatMsg)

            withTimeout(2000L) {
                val received = channel.receive()
                assertEquals("MESSAGE", received.type)
                assertEquals(connects.quotes.model.ChatIntentCode.MESSAGE, received.intentCode)
                assertEquals(chatMsg, received.message)
            }
        } finally {
            LiveChatHub.unregisterIntentSubscriber(connectionId)
        }
    }

    @Test
    fun testIntentSubscriberCustomLambdaFilter() = runBlocking {
        val connectionId = java.util.UUID.randomUUID().toString()
        val channel = kotlinx.coroutines.channels.Channel<connects.quotes.model.LiveChatPacket>(kotlinx.coroutines.channels.Channel.BUFFERED)

        // Custom lambda: accepts only packets where text contains "CRITICAL"
        val customLambda: (connects.quotes.model.LiveChatPacket) -> Boolean = { packet ->
            packet.text?.contains("CRITICAL") == true
        }

        val returnedLambda = LiveChatHub.registerIntentSubscriber(
            connectionId = connectionId,
            authorId = "author-lambda-user",
            channel = channel,
            customFilter = customLambda
        )
        assertSame(customLambda, returnedLambda)

        try {
            // Dispatch standard packet without "CRITICAL"
            LiveChatHub.dispatchIntent(
                connects.quotes.model.LiveChatPacket(
                    type = "NOTIFICATION",
                    intentCode = connects.quotes.model.ChatIntentCode.NOTIFICATION,
                    text = "Standard update"
                )
            )
            val filteredOut = channel.tryReceive()
            assertTrue(filteredOut.isFailure, "Packet without CRITICAL should be rejected by custom lambda")

            // Dispatch packet with "CRITICAL"
            LiveChatHub.dispatchIntent(
                connects.quotes.model.LiveChatPacket(
                    type = "NOTIFICATION",
                    intentCode = connects.quotes.model.ChatIntentCode.NOTIFICATION,
                    text = "CRITICAL security alert"
                )
            )
            withTimeout(2000L) {
                val received = channel.receive()
                assertEquals("CRITICAL security alert", received.text)
            }
        } finally {
            LiveChatHub.unregisterIntentSubscriber(connectionId)
            assertTrue(channel.isClosedForSend, "Channel should be closed after unregistering intent subscriber")
        }
    }
}
