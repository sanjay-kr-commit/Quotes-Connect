package connects.quotes.routes

import connects.quotes.chat.KeyExchangeRelay
import connects.quotes.model.KeyExchangePacket
import connects.quotes.model.KeyExchangePacketType
import connects.quotes.chat.LiveChatHub
import connects.quotes.database.JdbcPassageRepository
import connects.quotes.model.Id
import connects.quotes.model.ChatIntentCode
import connects.quotes.model.LiveChatPacket
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private fun ApplicationCall.extractPrincipal(repository: JdbcPassageRepository): connects.quotes.security.AuthPrincipal? {
    val authHeader = request.header(HttpHeaders.Authorization)
    val token = authHeader?.removePrefix("Bearer ")?.trim()
    return if (!token.isNullOrBlank()) repository.validateSession(token) else null
}

/**
 * Registers real-time live chat TCP streaming, push notifications, typing events, and resync requests.
 */
fun Route.chatRoutes(repository: JdbcPassageRepository) {
    // Intent-based unified reactive TCP stream endpoint
    // Clients/tabs register their connection UUID, authorId, optional chatId, and registered intents
    get("/api/chat/stream") {
        val principal = call.extractPrincipal(repository)
        val authorId = call.parameters["authorId"] ?: principal?.authorId?.value
        val chatId = call.parameters["chatId"]
        val connId = call.parameters["connectionId"]?.ifBlank { null } ?: UUID.randomUUID().toString()
        val intentsParam = call.parameters["intents"]

        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@get
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@get
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: caller cannot connect as another author")
            return@get
        }

        if (!chatId.isNullOrBlank()) {
            val session = repository.getChatSession(Id(chatId))
            if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
                call.respond(HttpStatusCode.Forbidden, "Forbidden: author is not a participant of this chat")
                return@get
            }
        }

        val intentSet: Set<Int> = if (!intentsParam.isNullOrBlank() && intentsParam != "all") {
            intentsParam.split(",").mapNotNull { token ->
                token.trim().toIntOrNull() ?: when (token.trim().uppercase()) {
                    "STATUS_CHANGED" -> ChatIntentCode.STATUS_CHANGED
                    "MESSAGE" -> ChatIntentCode.MESSAGE
                    "TYPING" -> ChatIntentCode.TYPING
                    "CHAT_DELETED" -> ChatIntentCode.CHAT_DELETED
                    "NOTIFICATION" -> ChatIntentCode.NOTIFICATION
                    "GRAPH_CHANGED" -> ChatIntentCode.GRAPH_CHANGED
                    "ALIAS_CHANGED" -> ChatIntentCode.ALIAS_CHANGED
                    "KEY_CHANGED" -> ChatIntentCode.KEY_CHANGED
                    else -> null
                }
            }.toSet()
        } else emptySet()

        repository.logAuthorActivity(Id(authorId), "INTENT_STREAM_OPEN")

        val channel = Channel<LiveChatPacket>(Channel.BUFFERED)
        LiveChatHub.registerIntentSubscriber(
            connectionId = connId,
            authorId = authorId,
            chatId = chatId,
            intents = intentSet,
            channel = channel
        )

        call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val initialPacket = LiveChatPacket(
                    type = "CONNECTED",
                    connectionId = connId,
                    chatId = chatId,
                    senderId = authorId,
                    timestamp = System.currentTimeMillis()
                )
                write("data: ${Json.encodeToString(initialPacket)}\n\n")
                flush()

                while (kotlin.coroutines.coroutineContext.isActive) {
                    val packet = withTimeoutOrNull(15000L) {
                        channel.receive()
                    }
                    if (packet != null) {
                        write("data: ${Json.encodeToString(packet)}\n\n")
                        flush()
                    } else {
                        val pingPacket = LiveChatPacket(
                            type = "PING",
                            intentCode = ChatIntentCode.PING,
                            connectionId = connId,
                            chatId = chatId,
                            timestamp = System.currentTimeMillis()
                        )
                        write("data: ${Json.encodeToString(pingPacket)}\n\n")
                        write(":keepalive\n\n")
                        flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Socket closed
        } finally {
            LiveChatHub.unregisterIntentSubscriber(connId)
        }
    }

    // Real-time live TCP chat streaming endpoint
    // Keeps persistent TCP streaming connection while chat is open
    get("/api/chat/live") {
        val principal = call.extractPrincipal(repository)
        val chatId = call.parameters["chatId"]
        val authorId = call.parameters["authorId"] ?: principal?.authorId?.value
        if (chatId.isNullOrBlank() || authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId or authorId")
            return@get
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@get
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: caller cannot connect as another author")
            return@get
        }

        val session = repository.getChatSession(Id(chatId))
        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: author is not a participant of this chat")
            return@get
        }

        // Log author active on chat connect
        repository.logAuthorActivity(Id(authorId), "CHAT_OPEN")

        val channel = LiveChatHub.registerChatListener(chatId, authorId)
        call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val initialPacket = LiveChatPacket(
                    type = "CONNECTED",
                    chatId = chatId,
                    senderId = authorId,
                    timestamp = System.currentTimeMillis()
                )
                write("data: ${Json.encodeToString(initialPacket)}\n\n")
                flush()

                while (kotlin.coroutines.coroutineContext.isActive) {
                    val packet = withTimeoutOrNull(15000L) {
                        channel.receive()
                    }
                    if (packet != null) {
                        write("data: ${Json.encodeToString(packet)}\n\n")
                        flush()
                    } else {
                        // Send TCP keepalive ping packet
                        val pingPacket = LiveChatPacket(
                            type = "PING",
                            chatId = chatId,
                            timestamp = System.currentTimeMillis()
                        )
                        write("data: ${Json.encodeToString(pingPacket)}\n\n")
                        write(":keepalive\n\n")
                        flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Client exited or socket closed
        } finally {
            LiveChatHub.unregisterChatListener(chatId, channel)
        }
    }

    // Real-time notification stream for sovereign author
    // Alerts user when chat is not open but new message is received
    get("/api/chat/notifications") {
        val principal = call.extractPrincipal(repository)
        val authorId = call.parameters["authorId"] ?: principal?.authorId?.value
        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@get
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@get
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: caller cannot listen to notifications of another author")
            return@get
        }

        // Log author active on notification connect
        repository.logAuthorActivity(Id(authorId), "NOTIFICATIONS_STREAM")

        val channel = LiveChatHub.registerNotificationListener(authorId)
        call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val initial = LiveChatPacket(
                    type = "CONNECTED",
                    senderId = authorId,
                    timestamp = System.currentTimeMillis()
                )
                write("data: ${Json.encodeToString(initial)}\n\n")
                flush()

                while (kotlin.coroutines.coroutineContext.isActive) {
                    val packet = withTimeoutOrNull(20000L) {
                        channel.receive()
                    }
                    if (packet != null) {
                        write("data: ${Json.encodeToString(packet)}\n\n")
                        flush()
                    } else {
                        val pingPacket = LiveChatPacket(
                            type = "PING",
                            senderId = authorId,
                            timestamp = System.currentTimeMillis()
                        )
                        write("data: ${Json.encodeToString(pingPacket)}\n\n")
                        write(":keepalive\n\n")
                        flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Disconnected
        } finally {
            LiveChatHub.unregisterNotificationListener(authorId, channel)
        }
    }

    post("/api/chat/typing") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val chatId = params["chatId"]
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val isTyping = params["isTyping"]?.toBooleanStrictOrNull() ?: false
        if (chatId == null || authorId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId or authorId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: cannot spoof typing status")
            return@post
        }
        val session = repository.getChatSession(Id(chatId))
        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: author is not a participant of this chat")
            return@post
        }
        LiveChatHub.broadcastTyping(chatId, authorId, isTyping)
        call.respond(HttpStatusCode.OK, """{"status":"ok"}""")
    }

    post("/api/chat/resync") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val chatId = params["chatId"]
        val requesterId = params["requesterId"] ?: params["authorId"] ?: principal?.authorId?.value
        if (chatId == null || requesterId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId or requesterId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        if (principal.authorId.value != requesterId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: cannot spoof resync request")
            return@post
        }
        val session = repository.getChatSession(Id(chatId))
        if (session == null || (session.initiatorId.value != requesterId && session.recipientId.value != requesterId)) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: requester is not a participant of this chat")
            return@post
        }
        LiveChatHub.broadcastResyncRequest(chatId, requesterId)
        call.respond(HttpStatusCode.OK, """{"status":"ok"}""")
    }

    // Dedicated Key Exchange Relay (QC-KEYRELAY-1)
    // Key exchange occurs ONLY when both parties are actively present.
    // Zero database persistence: key material is in-memory only and destroyed upon completion or disconnect.
    post("/api/chat/key-relay/join") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val chatId = params["chatId"]
        val authorId = params["authorId"] ?: principal?.authorId?.value
        if (chatId == null || authorId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId or authorId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden")
            return@post
        }

        val session = repository.getChatSession(Id(chatId))
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Chat session not found")
            return@post
        }
        if (session.initiatorId.value != authorId && session.recipientId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Author is not a member of chat session")
            return@post
        }

        val peerId = if (session.initiatorId.value == authorId) session.recipientId.value else session.initiatorId.value
        val relaySession = KeyExchangeRelay.getOrCreateSession(chatId, session.initiatorId.value, session.recipientId.value)
        val (_, bothActive) = KeyExchangeRelay.registerParticipant(relaySession, authorId)

        call.respond(HttpStatusCode.OK, """{"sessionId":"${relaySession.id}","bothActive":$bothActive,"peerId":"$peerId"}""")
    }

    get("/api/chat/key-relay/stream") {
        val principal = call.extractPrincipal(repository)
        val sessionId = call.parameters["sessionId"]
        val authorId = call.parameters["authorId"] ?: principal?.authorId?.value
        if (sessionId == null || authorId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing sessionId or authorId")
            return@get
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@get
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden")
            return@get
        }

        val relaySession = KeyExchangeRelay.getSession(sessionId)
        if (relaySession == null) {
            call.respond(HttpStatusCode.NotFound, "Relay session not found or expired")
            return@get
        }
        if (relaySession.participantA != authorId && relaySession.participantB != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden")
            return@get
        }

        val (channel, _) = KeyExchangeRelay.registerParticipant(relaySession, authorId)
        call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                while (kotlin.coroutines.coroutineContext.isActive) {
                    val packet = withTimeoutOrNull(10000L) {
                        channel.receive()
                    }
                    if (packet != null) {
                        write("data: ${Json.encodeToString(packet)}\n\n")
                        flush()
                        if (packet.type == KeyExchangePacketType.COMPLETE || packet.type == KeyExchangePacketType.ABORT) {
                            break
                        }
                    } else {
                        write(":keepalive\n\n")
                        flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Disconnected
        } finally {
            KeyExchangeRelay.unregisterParticipant(sessionId, authorId)
        }
    }

    post("/api/chat/key-relay/packet") {
        val principal = call.extractPrincipal(repository)
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        val packet = call.receive<KeyExchangePacket>()
        if (packet.senderId.isNotEmpty() && principal.authorId.value != packet.senderId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: cannot spoof senderId in relay packet")
            return@post
        }
        val sent = KeyExchangeRelay.relayPacket(packet)
        if (sent) {
            call.respond(HttpStatusCode.OK, """{"status":"relayed"}""")
        } else {
            call.respond(HttpStatusCode.Gone, """{"error":"Relay failed: peer disconnected or session expired"}""")
        }
    }

    // Propose shared alias for chat session
    post("/api/chat/alias/propose") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val chatId = params["chatId"]
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val alias = params["alias"]
        if (chatId == null || authorId == null || alias.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId, authorId, or alias")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden")
            return@post
        }
        val session = repository.getChatSession(Id(chatId))
        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: author is not a participant of this chat")
            return@post
        }
        val updated = repository.proposeSharedAlias(Id(chatId), Id(authorId), alias)
        if (updated != null) {
            LiveChatHub.broadcastAliasProposal(updated, authorId, alias)
            call.respond(HttpStatusCode.OK, Json.encodeToString(updated))
        } else {
            call.respond(HttpStatusCode.BadRequest, "Unable to propose alias")
        }
    }

    // Accept shared alias for chat session
    post("/api/chat/alias/accept") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val chatId = params["chatId"]
        val authorId = params["authorId"] ?: principal?.authorId?.value
        if (chatId == null || authorId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing chatId or authorId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Authentication required")
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden")
            return@post
        }
        val session = repository.getChatSession(Id(chatId))
        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
            call.respond(HttpStatusCode.Forbidden, "Forbidden: author is not a participant of this chat")
            return@post
        }
        val updated = repository.acceptSharedAlias(Id(chatId), Id(authorId))
        if (updated != null) {
            LiveChatHub.broadcastAliasAccepted(updated, updated.sharedAlias ?: "")
            call.respond(HttpStatusCode.OK, Json.encodeToString(updated))
        } else {
            call.respond(HttpStatusCode.BadRequest, "Unable to accept alias")
        }
    }
}
