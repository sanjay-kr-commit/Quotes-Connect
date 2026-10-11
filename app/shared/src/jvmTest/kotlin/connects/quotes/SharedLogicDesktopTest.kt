package connects.quotes
import kotlinx.coroutines.test.runTest

import connects.quotes.crypto.LocalChatKeyStore
import connects.quotes.crypto.PlatformCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SharedLogicDesktopTest {

    @Test
    fun testPlatformCryptoAsymmetricEnvelopeEncryptionRoundTrip() = runTest {
        // 1. Generate key pair on client side
        val keyPairAlice = PlatformCrypto.generateKeyPair()
        assertTrue(keyPairAlice.publicKey.isNotBlank())
        assertTrue(keyPairAlice.privateKey.isNotBlank())
        assertNotEquals(keyPairAlice.publicKey, keyPairAlice.privateKey)

        val keyPairBob = PlatformCrypto.generateKeyPair()
        assertNotEquals(keyPairAlice.publicKey, keyPairBob.publicKey)

        // 2. Alice encrypts a private quote/citation for Bob using Bob's public key
        val secretQuote = "Private Citation: 'The only true wisdom is in knowing you know nothing.' - Socrates #wisdom #philosophy"
        val cipherText = PlatformCrypto.encrypt(secretQuote, keyPairBob.publicKey)

        // Verify cipherText is enveloped and contains ASYM prefix
        assertTrue(cipherText.startsWith("ASYM:"), "Ciphertext should follow envelope format starting with ASYM:")
        assertNotEquals(secretQuote, cipherText)

        // 3. Bob decrypts with his client-side private key
        val decryptedByBob = PlatformCrypto.decrypt(cipherText, keyPairBob.privateKey)
        assertEquals(secretQuote, decryptedByBob)

        // 4. Alice (or anyone without Bob's private key) cannot decrypt
        kotlin.test.assertFailsWith<connects.quotes.security.DecryptionFailed>("Alice's private key must not be able to decrypt Bob's message") {
            PlatformCrypto.decrypt(cipherText, keyPairAlice.privateKey)
        }
    }

    @Test
    fun testLocalChatKeyStorePersistenceAndRetrieval() = runTest {
        val authorId = "author-test-" + System.currentTimeMillis()
        val kp = LocalChatKeyStore.getOrGenerateKeyPair(authorId)

        assertTrue(kp.publicKey.isNotBlank())
        assertTrue(kp.privateKey.isNotBlank())

        val retrieved = LocalChatKeyStore.getKeyPair(authorId)
        assertEquals(kp.publicKey, retrieved?.publicKey)
        assertEquals(kp.privateKey, retrieved?.privateKey)

        // Calling getOrGenerate again should return the existing stored pair
        val secondCall = LocalChatKeyStore.getOrGenerateKeyPair(authorId)
        assertEquals(kp.publicKey, secondCall.publicKey)
        assertEquals(kp.privateKey, secondCall.privateKey)
    }

    @Test
    fun testClientChatDatabasePersistenceAndOutgoingStack() = runTest {
        val chatId = connects.quotes.model.Id("chat-test-db-1")
        val aliceId = connects.quotes.model.Id("author-alice")
        val bobId = connects.quotes.model.Id("author-bob")

        // Clean previous test data
        connects.quotes.database.ClientChatDatabase.clearChat(chatId)

        // 1. Save an encrypted message with decrypted plaintext locally
        val msg1 = connects.quotes.model.ChatMessage(
            id = connects.quotes.model.Id("msg-1"),
            chatId = chatId,
            senderId = aliceId,
            recipientId = bobId,
            content = "ASYM:test-ciphertext-1",
            isEncrypted = true,
            citedPassageId = null,
            timestamp = 1000L
        )
        val plaintext1 = "Hello Bob! Secret sovereign message #1"
        connects.quotes.database.ClientChatDatabase.saveMessage(msg1, decryptedPlaintext = plaintext1)

        // 2. Save a second encrypted message from Alice
        val msg2 = connects.quotes.model.ChatMessage(
            id = connects.quotes.model.Id("msg-2"),
            chatId = chatId,
            senderId = aliceId,
            recipientId = bobId,
            content = "ASYM:test-ciphertext-2",
            isEncrypted = true,
            citedPassageId = null,
            timestamp = 2000L
        )
        val plaintext2 = "Bob, here is our second private citation #2"
        connects.quotes.database.ClientChatDatabase.saveMessage(msg2, decryptedPlaintext = plaintext2)

        // 3. Verify messages are persisted and retrieved
        val retrieved = connects.quotes.database.ClientChatDatabase.getMessages(chatId)
        assertEquals(2, retrieved.size)
        assertEquals("msg-1", retrieved[0].id.value)
        assertEquals("msg-2", retrieved[1].id.value)

        // 4. Verify decrypted plaintext retrieval
        assertEquals(plaintext1, connects.quotes.database.ClientChatDatabase.getDecryptedPlaintext(msg1.id))
        assertEquals(plaintext2, connects.quotes.database.ClientChatDatabase.getDecryptedPlaintext(msg2.id))

        // 5. Verify outgoing stack for Alice (for key loss re-encryption & resending)
        val aliceOutgoingStack = connects.quotes.database.ClientChatDatabase.getOutgoingStack(chatId, aliceId)
        assertEquals(2, aliceOutgoingStack.size)
        assertEquals("msg-1", aliceOutgoingStack[0].id.value)
        assertEquals("msg-2", aliceOutgoingStack[1].id.value)

        // Bob has 0 outgoing messages
        val bobOutgoingStack = connects.quotes.database.ClientChatDatabase.getOutgoingStack(chatId, bobId)
        assertEquals(0, bobOutgoingStack.size)

        // Cleanup
        connects.quotes.database.ClientChatDatabase.clearChat(chatId)
        assertEquals(0, connects.quotes.database.ClientChatDatabase.getMessages(chatId).size)
    }
}