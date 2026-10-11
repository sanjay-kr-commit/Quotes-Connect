package connects.quotes.chat

import connects.quotes.crypto.PlatformCrypto
import connects.quotes.model.Id
import connects.quotes.model.KeyExchangePacket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ClientKeyExchangeTest {
    @Test
    fun twoPartyHandshakeEstablishesSameSessionSecret() = runTest {
        val alice = Id("alice")
        val bob = Id("bob")
        val aliceIdentity = PlatformCrypto.generateKeyPair()
        val bobIdentity = PlatformCrypto.generateKeyPair()

        var aliceSecret: ByteArray? = null
        var bobSecret: ByteArray? = null
        var aliceManager: ClientKeyExchangeManager? = null
        var bobManager: ClientKeyExchangeManager? = null

        suspend fun deliver(
            target: ClientKeyExchangeManager,
            packet: KeyExchangePacket
        ) {
            target.receiveRelayPacket(packet)
        }

        aliceManager = ClientKeyExchangeManager(
            authorId = alice,
            sendToRelay = { packet -> deliver(bobManager!!, packet) },
            onStateChanged = { _, state, secret ->
                if (state == ExchangeState.ESTABLISHED) aliceSecret = secret
            }
        )
        bobManager = ClientKeyExchangeManager(
            authorId = bob,
            sendToRelay = { packet -> deliver(aliceManager!!, packet) },
            onStateChanged = { _, state, secret ->
                if (state == ExchangeState.ESTABLISHED) bobSecret = secret
            }
        )

        // The responder must have a waiting context before the relay delivers the initiator hello.
        bobManager!!.startExchange(
            sessionId = "session-1",
            peerId = alice,
            longTermKeyPair = bobIdentity,
            peerLongTermPublicKey = aliceIdentity.publicKey,
            isInitiator = false
        )
        aliceManager!!.startExchange(
            sessionId = "session-1",
            peerId = bob,
            longTermKeyPair = aliceIdentity,
            peerLongTermPublicKey = bobIdentity.publicKey,
            isInitiator = true
        )

        assertNotNull(aliceSecret)
        assertNotNull(bobSecret)
        assertContentEquals(aliceSecret!!, bobSecret!!)
        assertTrue(aliceSecret!!.size == 32)
    }

    @Test
    fun replayedPacketFailsClosed() = runTest {
        val alice = Id("alice")
        val bob = Id("bob")
        val aliceIdentity = PlatformCrypto.generateKeyPair()
        val bobIdentity = PlatformCrypto.generateKeyPair()

        var captured: KeyExchangePacket? = null
        var finalState: ExchangeState? = null
        val bobManager = ClientKeyExchangeManager(
            authorId = bob,
            sendToRelay = { },
            onStateChanged = { _, state, _ -> finalState = state }
        )
        bobManager.startExchange(
            sessionId = "session-2",
            peerId = alice,
            longTermKeyPair = bobIdentity,
            peerLongTermPublicKey = aliceIdentity.publicKey,
            isInitiator = false
        )

        val initiatorEphemeral = PlatformCrypto.generateAgreementKeyPair()
        val signature = PlatformCrypto.sign(
            "QC-KEYEXCHANGE-V1|HELLO|1|QC-ECDH-P256-HKDF-SHA256|session-2|alice|bob|${initiatorEphemeral.publicKey}"
                .encodeToByteArray(),
            aliceIdentity.privateKey
        )
        val packet = KeyExchangePacket(
            sessionId = "session-2",
            type = connects.quotes.model.KeyExchangePacketType.PUBLIC_KEY,
            senderId = "alice",
            recipientId = "bob",
            sequence = 1,
            payload = kotlinx.serialization.json.Json.encodeToString(
                PeerHandshakePacket(
                    sessionId = "session-2",
                    participantA = "alice",
                    participantB = "bob",
                    isInitiator = true,
                    ephemeralPublicKey = initiatorEphemeral.publicKey,
                    signatureB64 = signature
                )
            )
        )
        captured = packet
        bobManager.receiveRelayPacket(packet)
        bobManager.receiveRelayPacket(captured!!)

        // The second packet has the same sequence and is rejected before crypto processing.
        assertEquals(ExchangeState.FAILED, finalState)
    }
}
