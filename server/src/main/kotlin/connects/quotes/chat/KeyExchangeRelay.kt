package connects.quotes.chat

import connects.quotes.model.KeyExchangePacket
import connects.quotes.model.KeyExchangePacketType

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap





data class KeyExchangeParticipant(
    val authorId: String,
    val channel: Channel<KeyExchangePacket>
)

data class KeyExchangeSession(
    val id: String,
    val chatId: String,
    val participantA: String,
    val participantB: String,
    val createdAt: Long,
    val expiresAt: Long,
    @Volatile var participantAConn: KeyExchangeParticipant? = null,
    @Volatile var participantBConn: KeyExchangeParticipant? = null,
    var lastSequenceA: Long = -1L,
    var lastSequenceB: Long = -1L,
    val confirmations: MutableSet<String> = mutableSetOf()
) {
    val isBothConnected: Boolean
        get() = participantAConn != null && participantBConn != null
}

/**
 * Dedicated In-Memory Key Exchange Relay (QC-KEYRELAY-1).
 *
 * Hard Invariants:
 * 1. Both participants MUST be actively connected for key exchange to proceed.
 * 2. Zero database persistence: no tables, no queues, no mailbox for key material.
 * 3. Ephemeral relay state is strictly bound to memory and destroyed upon completion or disconnect.
 * 4. Strict participant authorization: non-participants cannot eavesdrop, spoof, or join.
 * 5. Bounded backpressure prevents memory exhaustion attacks.
 */
object KeyExchangeRelay {
    private val logger = LoggerFactory.getLogger(KeyExchangeRelay::class.java)

    private const val MAX_RELAY_SESSIONS = 256
    private const val RELAY_TIMEOUT_MS = 45_000L
    private const val CHANNEL_BUFFER_CAPACITY = 16

    // sessionId -> KeyExchangeSession
    private val activeSessions = ConcurrentHashMap<String, KeyExchangeSession>()
    // chatId -> sessionId mapping
    private val chatToSession = ConcurrentHashMap<String, String>()

    fun getOrCreateSession(
        chatId: String,
        participantA: String,
        participantB: String
    ): KeyExchangeSession {
        cleanExpiredSessions()

        val existingSessionId = chatToSession[chatId]
        if (existingSessionId != null) {
            val session = activeSessions[existingSessionId]
            if (session != null && System.currentTimeMillis() < session.expiresAt) {
                return session
            } else {
                closeSession(existingSessionId, "EXPIRED")
            }
        }

        if (activeSessions.size >= MAX_RELAY_SESSIONS) {
            cleanExpiredSessions()
            if (activeSessions.size >= MAX_RELAY_SESSIONS) {
                error("Relay capacity exceeded: max concurrent key exchange sessions reached")
            }
        }

        val sessionId = "ke-sess-" + UUID.randomUUID().toString().take(12)
        val now = System.currentTimeMillis()
        val session = KeyExchangeSession(
            id = sessionId,
            chatId = chatId,
            participantA = participantA,
            participantB = participantB,
            createdAt = now,
            expiresAt = now + RELAY_TIMEOUT_MS
        )

        activeSessions[sessionId] = session
        chatToSession[chatId] = sessionId
        logger.info("KEY_RELAY_STARTED chatId=$chatId sessionId=$sessionId")
        return session
    }

    fun getSession(sessionId: String): KeyExchangeSession? {
        val session = activeSessions[sessionId] ?: return null
        if (System.currentTimeMillis() >= session.expiresAt) {
            closeSession(sessionId, "TIMEOUT")
            return null
        }
        return session
    }

    fun registerParticipant(
        session: KeyExchangeSession,
        authorId: String
    ): Pair<Channel<KeyExchangePacket>, Boolean> {
        val isA = session.participantA == authorId
        val isB = session.participantB == authorId
        if (!isA && !isB) {
            throw IllegalArgumentException("Author $authorId is not a participant in session ${session.id}")
        }

        val channel = Channel<KeyExchangePacket>(CHANNEL_BUFFER_CAPACITY)
        val participant = KeyExchangeParticipant(authorId, channel)

        synchronized(session) {
            if (isA) {
                session.participantAConn?.channel?.close()
                session.participantAConn = participant
            } else {
                session.participantBConn?.channel?.close()
                session.participantBConn = participant
            }
        }

        val bothActive = session.isBothConnected
        if (bothActive) {
            val connectedPacketA = KeyExchangePacket(
                sessionId = session.id,
                type = KeyExchangePacketType.PEER_CONNECTED,
                senderId = "RELAY",
                recipientId = session.participantA,
                payload = "Peer is active. Secure relay ready."
            )
            val connectedPacketB = KeyExchangePacket(
                sessionId = session.id,
                type = KeyExchangePacketType.PEER_CONNECTED,
                senderId = "RELAY",
                recipientId = session.participantB,
                payload = "Peer is active. Secure relay ready."
            )
            session.participantAConn?.channel?.trySend(connectedPacketA)
            session.participantBConn?.channel?.trySend(connectedPacketB)
        } else {
            val waitingPacket = KeyExchangePacket(
                sessionId = session.id,
                type = KeyExchangePacketType.WAITING_FOR_PEER,
                senderId = "RELAY",
                recipientId = authorId,
                payload = "Waiting for the other participant to come online."
            )
            channel.trySend(waitingPacket)
        }

        return Pair(channel, bothActive)
    }

    fun unregisterParticipant(sessionId: String, authorId: String) {
        val session = activeSessions[sessionId] ?: return
        synchronized(session) {
            if (session.participantA == authorId) {
                session.participantAConn?.channel?.close()
                session.participantAConn = null
            } else if (session.participantB == authorId) {
                session.participantBConn?.channel?.close()
                session.participantBConn = null
            }
        }
        closeSession(sessionId, "DISCONNECTED")
    }

    suspend fun relayPacket(packet: KeyExchangePacket): Boolean {
        val session = activeSessions[packet.sessionId] ?: return false
        if (System.currentTimeMillis() >= session.expiresAt) {
            closeSession(session.id, "TIMEOUT")
            return false
        }

        val targetParticipant: KeyExchangeParticipant?
        var closeAfterSend = false

        synchronized(session) {
            if (packet.senderId == session.participantA) {
                if (packet.sequence <= session.lastSequenceA) {
                    logger.warn("REJECTED_RELAY_REPLAY sender=${packet.senderId} sessionId=${session.id}")
                    return false
                }
                session.lastSequenceA = packet.sequence
                targetParticipant = session.participantBConn
            } else if (packet.senderId == session.participantB) {
                if (packet.sequence <= session.lastSequenceB) {
                    logger.warn("REJECTED_RELAY_REPLAY sender=${packet.senderId} sessionId=${session.id}")
                    return false
                }
                session.lastSequenceB = packet.sequence
                targetParticipant = session.participantAConn
            } else {
                logger.warn("REJECTED_RELAY_PACKET: sender ${packet.senderId} not in session ${session.id}")
                return false
            }

            if (packet.type == KeyExchangePacketType.COMPLETE) {
                session.confirmations += packet.senderId
                closeAfterSend = session.confirmations.size == 2
            } else if (packet.type == KeyExchangePacketType.ABORT) {
                closeAfterSend = true
            }
        }

        if (targetParticipant == null) {
            return false
        }

        val sent = withTimeoutOrNull(2000L) {
            targetParticipant!!.channel.send(packet)
            true
        } ?: false

        if (sent && closeAfterSend) {
            closeSession(
                session.id,
                if (packet.type == KeyExchangePacketType.ABORT) "ABORT" else "COMPLETE"
            )
        }

        return sent
    }

    fun closeSession(sessionId: String, reason: String) {
        val session = activeSessions.remove(sessionId) ?: return
        chatToSession.remove(session.chatId)
        synchronized(session) {
            session.participantAConn?.channel?.close()
            session.participantBConn?.channel?.close()
            session.participantAConn = null
            session.participantBConn = null
        }
        logger.info("KEY_RELAY_CLOSED sessionId=${session.id} reason=$reason")
    }

    private fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        val expired = activeSessions.filterValues { it.expiresAt <= now }.keys
        for (sid in expired) {
            closeSession(sid, "TIMEOUT")
        }
    }
}
