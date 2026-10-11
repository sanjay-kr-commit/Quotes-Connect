package connects.quotes.chat

import connects.quotes.crypto.PlatformCrypto
import connects.quotes.security.SecurityConfiguration
import connects.quotes.crypto.CryptoKeyPair
import connects.quotes.model.Id
import connects.quotes.model.KeyExchangePacket
import connects.quotes.model.KeyExchangePacketType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ExchangeState {
    IDLE,
    WAITING_FOR_PEER,
    NEGOTIATING,
    KEY_CONFIRMING,
    ESTABLISHED,
    FAILED,
    ABORTED,
    EXPIRED
}

private const val PROTOCOL_VERSION = SecurityConfiguration.KEY_EXCHANGE_PROTOCOL_VERSION
private const val SUITE_ID = SecurityConfiguration.KEY_EXCHANGE_SUITE_ID
private const val TRANSCRIPT_LABEL = SecurityConfiguration.KEY_EXCHANGE_TRANSCRIPT_LABEL
private const val CONFIRM_LABEL = SecurityConfiguration.KEY_EXCHANGE_CONFIRM_LABEL
private const val SESSION_KEY_LENGTH = SecurityConfiguration.KEY_EXCHANGE_SESSION_KEY_BYTES

@Serializable
data class PeerHandshakePacket(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val suiteId: String = SUITE_ID,
    val sessionId: String,
    val participantA: String,
    val participantB: String,
    val isInitiator: Boolean,
    val ephemeralPublicKey: String,
    val signatureB64: String
)

@Serializable
data class KeyConfirmPacket(
    val sessionId: String,
    val senderId: String,
    val confirmationMacB64: String
)

/**
 * Client-side authenticated peer key exchange.
 *
 * Protocol:
 *   1. Initiator sends an ephemeral P-256 public key signed by its long-term RSA identity.
 *   2. Responder verifies it, sends its ephemeral public key signed over both ephemeral keys.
 *   3. Both derive ECDH, then HKDF-SHA-256 with the canonical transcript as context.
 *   4. Both send an HMAC-SHA-256 confirmation bound to the transcript and sender role.
 *
 * The relay carries opaque packets only. It never receives the derived session key.
 */
class ClientKeyExchangeManager(
    val authorId: Id,
    val sendToRelay: suspend (KeyExchangePacket) -> Unit,
    val onStateChanged: (String, ExchangeState, ByteArray?) -> Unit = { _, _, _ -> }
) {
    private val json = Json { ignoreUnknownKeys = false }
    private val sessionStates = mutableMapOf<String, SessionContext>()

    private class SessionContext(
        val sessionId: String,
        val peerId: String,
        val longTermKeyPair: CryptoKeyPair,
        val isInitiator: Boolean,
        val peerLongTermPublicKey: String
    ) {
        var state: ExchangeState = ExchangeState.IDLE
        var ephemeralKeyPair: CryptoKeyPair? = null
        var peerEphemeralKey: String? = null
        var sessionSecret: ByteArray? = null
        var peerConfirmed: Boolean = false
        var expectedSequence = -1L
        var nextOutgoingSequence = 0L
        var transcript: ByteArray? = null
    }

    private fun helloSigningBytes(
        sessionId: String,
        participantA: String,
        participantB: String,
        ephemeralPublicKey: String
    ): ByteArray =
        "$TRANSCRIPT_LABEL|HELLO|$PROTOCOL_VERSION|$SUITE_ID|$sessionId|$participantA|$participantB|$ephemeralPublicKey"
            .encodeToByteArray()

    private fun replySigningBytes(
        sessionId: String,
        participantA: String,
        participantB: String,
        initiatorPublicKey: String,
        responderPublicKey: String
    ): ByteArray =
        "$TRANSCRIPT_LABEL|REPLY|$PROTOCOL_VERSION|$SUITE_ID|$sessionId|$participantA|$participantB|$initiatorPublicKey|$responderPublicKey"
            .encodeToByteArray()

    private fun canonicalTranscript(
        sessionId: String,
        participantA: String,
        participantB: String,
        initiatorPublicKey: String,
        responderPublicKey: String
    ): ByteArray =
        "$TRANSCRIPT_LABEL|TRANSCRIPT|$PROTOCOL_VERSION|$SUITE_ID|$sessionId|$participantA|$participantB|$initiatorPublicKey|$responderPublicKey"
            .encodeToByteArray()

    private suspend fun deriveSessionSecret(ctx: SessionContext) {
        val ephemeral = ctx.ephemeralKeyPair ?: throw IllegalStateException("Missing local ephemeral key")
        val peerEphemeral = ctx.peerEphemeralKey ?: throw IllegalStateException("Missing peer ephemeral key")

        val initiatorPublicKey = if (ctx.isInitiator) ephemeral.publicKey else peerEphemeral
        val responderPublicKey = if (ctx.isInitiator) peerEphemeral else ephemeral.publicKey
        val transcript = canonicalTranscript(
            ctx.sessionId,
            if (ctx.isInitiator) authorId.value else ctx.peerId,
            if (ctx.isInitiator) ctx.peerId else authorId.value,
            initiatorPublicKey,
            responderPublicKey
        )

        ctx.transcript = transcript
        val ecdhSecret = PlatformCrypto.deriveAgreementSecret(ephemeral.privateKey, peerEphemeral)

        // The session identifier is public context. The transcript is the HKDF context.
        ctx.sessionSecret = PlatformCrypto.hkdfSha256(
            inputKeyMaterial = ecdhSecret,
            salt = ctx.sessionId.encodeToByteArray(),
            info = transcript,
            length = SESSION_KEY_LENGTH
        )
    }

    private suspend fun sendConfirmation(ctx: SessionContext) {
        val sessionSecret = ctx.sessionSecret ?: throw IllegalStateException("Missing session secret")
        val transcript = ctx.transcript ?: throw IllegalStateException("Missing transcript")
        val role = if (ctx.isInitiator) "INITIATOR" else "RESPONDER"
        val confirmationMessage =
            "$CONFIRM_LABEL|$PROTOCOL_VERSION|$SUITE_ID|${ctx.sessionId}|${authorId.value}|$role|"
                .encodeToByteArray() + transcript
        val mac = PlatformCrypto.hmacSha256(sessionSecret, confirmationMessage)

        sendPacket(ctx,
            KeyExchangePacket(
                sessionId = ctx.sessionId,
                type = KeyExchangePacketType.COMPLETE,
                senderId = authorId.value,
                recipientId = ctx.peerId,
                payload = json.encodeToString(
                    KeyConfirmPacket(
                        sessionId = ctx.sessionId,
                        senderId = authorId.value,
                        confirmationMacB64 = mac.encodeBase64()
                    )
                )
            )
        )
    }

    private suspend fun verifyConfirmation(
        ctx: SessionContext,
        confirmPacket: KeyConfirmPacket
    ): Boolean {
        if (confirmPacket.sessionId != ctx.sessionId || confirmPacket.senderId != ctx.peerId) {
            return false
        }
        val sessionSecret = ctx.sessionSecret ?: return false
        val transcript = ctx.transcript ?: return false
        val peerRole = if (ctx.isInitiator) "RESPONDER" else "INITIATOR"
        val confirmationMessage =
            "$CONFIRM_LABEL|$PROTOCOL_VERSION|$SUITE_ID|${ctx.sessionId}|${ctx.peerId}|$peerRole|"
                .encodeToByteArray() + transcript
        val expected = PlatformCrypto.hmacSha256(sessionSecret, confirmationMessage)
        val received = confirmPacket.confirmationMacB64.decodeBase64()
        return constantTimeEquals(expected, received)
    }

    private suspend fun sendPacket(ctx: SessionContext, packet: KeyExchangePacket) {
        sendToRelay(packet.copy(sequence = ctx.nextOutgoingSequence++))
    }

    suspend fun startExchange(
        sessionId: String,
        peerId: Id,
        longTermKeyPair: CryptoKeyPair,
        peerLongTermPublicKey: String,
        isInitiator: Boolean
    ) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(peerId.value.isNotBlank()) { "peerId must not be blank" }
        require(sessionId !in sessionStates) { "Session already exists" }

        val ctx = SessionContext(
            sessionId = sessionId,
            peerId = peerId.value,
            longTermKeyPair = longTermKeyPair,
            isInitiator = isInitiator,
            peerLongTermPublicKey = peerLongTermPublicKey
        )
        ctx.ephemeralKeyPair = PlatformCrypto.generateAgreementKeyPair()
        ctx.state = ExchangeState.WAITING_FOR_PEER
        sessionStates[sessionId] = ctx
        onStateChanged(sessionId, ctx.state, null)

        // Only the initiator starts the wire exchange. The responder waits for the relay packet.
        if (!isInitiator) return

        val ephemeralPublicKey = ctx.ephemeralKeyPair!!.publicKey
        val signature = PlatformCrypto.sign(
            helloSigningBytes(
                sessionId,
                authorId.value,
                peerId.value,
                ephemeralPublicKey
            ),
            longTermKeyPair.privateKey
        )
        sendPacket(ctx,
            KeyExchangePacket(
                sessionId = sessionId,
                type = KeyExchangePacketType.PUBLIC_KEY,
                senderId = authorId.value,
                recipientId = peerId.value,
                payload = json.encodeToString(
                    PeerHandshakePacket(
                        sessionId = sessionId,
                        participantA = authorId.value,
                        participantB = peerId.value,
                        isInitiator = true,
                        ephemeralPublicKey = ephemeralPublicKey,
                        signatureB64 = signature
                    )
                )
            )
        )
    }

    suspend fun receiveRelayPacket(packet: KeyExchangePacket) {
        val ctx = sessionStates[packet.sessionId] ?: return

        if (packet.senderId != ctx.peerId || packet.recipientId != authorId.value) {
            failSession(ctx, "Packet participant mismatch")
            return
        }

        if (packet.sequence <= ctx.expectedSequence) {
            failSession(ctx, "Replayed sequence number")
            return
        }
        ctx.expectedSequence = packet.sequence

        try {
            when (packet.type) {
                KeyExchangePacketType.PUBLIC_KEY -> handlePublicKey(ctx, packet)
                KeyExchangePacketType.COMPLETE -> handleConfirmation(ctx, packet)
                KeyExchangePacketType.ABORT -> {
                    ctx.state = ExchangeState.ABORTED
                    onStateChanged(ctx.sessionId, ctx.state, null)
                    sessionStates.remove(ctx.sessionId)
                }
                KeyExchangePacketType.HELLO,
                KeyExchangePacketType.KEY_CONFIRM,
                KeyExchangePacketType.WAITING_FOR_PEER,
                KeyExchangePacketType.PEER_CONNECTED -> {
                    failSession(ctx, "Unsupported key-exchange packet type")
                }
            }
        } catch (_: Exception) {
            failSession(ctx, "Unexpected protocol exception")
        }
    }

    private suspend fun handlePublicKey(ctx: SessionContext, packet: KeyExchangePacket) {
        if (ctx.state != ExchangeState.WAITING_FOR_PEER) {
            failSession(ctx, "Unexpected PUBLIC_KEY state")
            return
        }

        val peerHandshake = try {
            json.decodeFromString<PeerHandshakePacket>(packet.payload)
        } catch (_: Exception) {
            failSession(ctx, "Malformed PUBLIC_KEY payload")
            return
        }

        if (
            peerHandshake.protocolVersion != PROTOCOL_VERSION ||
            peerHandshake.suiteId != SUITE_ID ||
            peerHandshake.sessionId != ctx.sessionId ||
            peerHandshake.participantA != (if (ctx.isInitiator) authorId.value else ctx.peerId) ||
            peerHandshake.participantB != (if (ctx.isInitiator) ctx.peerId else authorId.value) ||
            peerHandshake.isInitiator == ctx.isInitiator ||
            peerHandshake.ephemeralPublicKey.isBlank()
        ) {
            failSession(ctx, "Handshake role or transcript mismatch")
            return
        }

        val validSignature = if (ctx.isInitiator) {
            // Responder signs the complete two-key transcript.
            val localEphemeral = ctx.ephemeralKeyPair?.publicKey ?: run {
                failSession(ctx, "Missing local ephemeral key")
                return
            }
            PlatformCrypto.verify(
                replySigningBytes(
                    ctx.sessionId,
                    authorId.value,
                    ctx.peerId,
                    localEphemeral,
                    peerHandshake.ephemeralPublicKey
                ),
                peerHandshake.signatureB64,
                ctx.peerLongTermPublicKey
            )
        } else {
            // Responder verifies the initiator's first message.
            PlatformCrypto.verify(
                helloSigningBytes(
                    ctx.sessionId,
                    ctx.peerId,
                    authorId.value,
                    peerHandshake.ephemeralPublicKey
                ),
                peerHandshake.signatureB64,
                ctx.peerLongTermPublicKey
            )
        }

        if (!validSignature) {
            failSession(ctx, "Invalid peer signature")
            return
        }

        ctx.peerEphemeralKey = peerHandshake.ephemeralPublicKey

        if (!ctx.isInitiator) {
            ctx.state = ExchangeState.NEGOTIATING
            val localEphemeral = ctx.ephemeralKeyPair?.publicKey ?: run {
                failSession(ctx, "Missing local ephemeral key")
                return
            }
            val signature = PlatformCrypto.sign(
                replySigningBytes(
                    ctx.sessionId,
                    ctx.peerId,
                    authorId.value,
                    peerHandshake.ephemeralPublicKey,
                    localEphemeral
                ),
                ctx.longTermKeyPair.privateKey
            )
            sendPacket(ctx,
                KeyExchangePacket(
                    sessionId = ctx.sessionId,
                    type = KeyExchangePacketType.PUBLIC_KEY,
                    senderId = authorId.value,
                    recipientId = ctx.peerId,
                    payload = json.encodeToString(
                        PeerHandshakePacket(
                            sessionId = ctx.sessionId,
                            participantA = ctx.peerId,
                            participantB = authorId.value,
                            isInitiator = false,
                            ephemeralPublicKey = localEphemeral,
                            signatureB64 = signature
                        )
                    )
                )
            )
        } else {
            ctx.state = ExchangeState.NEGOTIATING
        }

        deriveSessionSecret(ctx)
        ctx.state = ExchangeState.KEY_CONFIRMING
        onStateChanged(ctx.sessionId, ctx.state, null)
        sendConfirmation(ctx)
    }

    private suspend fun handleConfirmation(ctx: SessionContext, packet: KeyExchangePacket) {
        if (ctx.state != ExchangeState.KEY_CONFIRMING) {
            failSession(ctx, "Unexpected COMPLETE state")
            return
        }

        val confirmPacket = try {
            json.decodeFromString<KeyConfirmPacket>(packet.payload)
        } catch (_: Exception) {
            failSession(ctx, "Malformed COMPLETE payload")
            return
        }

        if (!verifyConfirmation(ctx, confirmPacket)) {
            failSession(ctx, "Key confirmation failed")
            return
        }

        ctx.peerConfirmed = true
        ctx.state = ExchangeState.ESTABLISHED
        val establishedSecret = ctx.sessionSecret
        onStateChanged(ctx.sessionId, ctx.state, establishedSecret)
        // Do not retain the session secret after handing it to the owning layer.
        ctx.sessionSecret = null
        ctx.ephemeralKeyPair = null
        ctx.peerEphemeralKey = null
        ctx.transcript = null
        sessionStates.remove(ctx.sessionId)
    }

    private fun failSession(ctx: SessionContext, reason: String) {
        // Never surface exception text or cryptographic material as a protocol failure reason.
        ctx.state = ExchangeState.FAILED
        ctx.sessionSecret = null
        ctx.ephemeralKeyPair = null
        ctx.peerEphemeralKey = null
        ctx.transcript = null
        onStateChanged(ctx.sessionId, ctx.state, null)
        sessionStates.remove(ctx.sessionId)
    }
}

private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String =
    kotlin.io.encoding.Base64.Default.encode(this)

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun String.decodeBase64(): ByteArray =
    kotlin.io.encoding.Base64.Default.decode(this)
