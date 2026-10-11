package connects.quotes.crypto

import connects.quotes.security.CryptoEnvelope
import connects.quotes.security.DecryptionFailed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class PlatformCryptoTest {

    @Test
    fun testGenerateKeyAndEncryptDecrypt() = runTest {
        val keyPair = PlatformCrypto.generateKeyPair()
        assertTrue(keyPair.publicKey.isNotBlank())
        assertTrue(keyPair.privateKey.isNotBlank())

        val plainText = "Hello, Cross-Platform Crypto!"
        val cipherText = PlatformCrypto.encrypt(plainText, keyPair.publicKey)
        assertTrue(cipherText.startsWith("ASYM:"))
        
        val decrypted = PlatformCrypto.decrypt(cipherText, keyPair.privateKey)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testEnvelopeEncryption() = runTest {
        val keyPair = PlatformCrypto.generateKeyPair()
        val plainText = "Secret Envelope Payload"
        val aad = "Envelope Header Data"
        
        val envelope = PlatformCrypto.encryptEnvelope(plainText, keyPair.publicKey, aad, "test-key-id")
        assertEquals(1, envelope.version)
        assertEquals(CryptoEnvelope.SUITE_RSA2048_AES256GCM, envelope.suite)
        assertEquals("test-key-id", envelope.keyId)
        assertEquals(aad, envelope.aad)
        
        val decrypted = PlatformCrypto.decryptEnvelope(envelope, keyPair.privateKey)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testSymmetricEncryption() = runTest {
        val plainText = "Symmetric Secret"
        val password = "SuperSecretPassword123"
        
        val blob = PlatformCrypto.encryptSymmetric(plainText, password)
        assertEquals(KeystoreCryptoContract.CURRENT_BLOB_VERSION, blob.version)
        assertTrue(blob.encryptedPayload.isNotBlank())

        val salt = Base64.decode(blob.salt)
        val iv = Base64.decode(blob.iv)
        val ciphertext = Base64.decode(blob.encryptedPayload)
        assertEquals(KeystoreCryptoContract.SALT_BYTES, salt.size)
        assertEquals(KeystoreCryptoContract.NONCE_BYTES, iv.size)
        assertEquals(plainText.encodeToByteArray().size + KeystoreCryptoContract.AUTH_TAG_BYTES, ciphertext.size)

        val decrypted = PlatformCrypto.decryptSymmetric(blob, password)
        assertEquals(plainText, decrypted)
    }
    
    @Test
    fun testChannelEncryption() = runTest {
        val serverKeys = PlatformCrypto.generateKeyPair()
        val plainText = "Channel Payload"
        
        val (aesKey, payload) = PlatformCrypto.encryptChannel(plainText, serverKeys.publicKey, "server-key")
        
        // Simulating server decrypting the AES key using its private key (mocked here as we just want the channel decryption)
        val decrypted = PlatformCrypto.decryptChannel(payload.cipherText, payload.iv, aesKey, aad = "server-key:${payload.requestId}")
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testInvalidDecryptionFailsClosed() = runTest {
        val keyPair = PlatformCrypto.generateKeyPair()
        val wrongKeyPair = PlatformCrypto.generateKeyPair()
        
        val cipherText = PlatformCrypto.encrypt("Secret", keyPair.publicKey)
        
        // Wrong private key
        assertFailsWith<DecryptionFailed> {
            PlatformCrypto.decrypt(cipherText, wrongKeyPair.privateKey)
        }
        
        // Corrupted ciphertext
        val corrupted = cipherText.dropLast(5) + "XXXXX"
        assertFailsWith<DecryptionFailed> {
            PlatformCrypto.decrypt(corrupted, keyPair.privateKey)
        }
        
        // Malformed envelope
        assertFailsWith<DecryptionFailed> {
            PlatformCrypto.decrypt("ASYM:NOT_ENOUGH_PARTS", keyPair.privateKey)
        }
    }
    @Test
    fun testHmacAndHkdfSha256Vectors() = runTest {
        val hmac = PlatformCrypto.hmacSha256(
            "key".encodeToByteArray(),
            "The quick brown fox jumps over the lazy dog".encodeToByteArray()
        )
        val expectedHmac = hexToBytes("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8")
        assertContentEquals(expectedHmac, hmac)

        val ikm = ByteArray(22) { 0x0b.toByte() }
        val salt = hexToBytes("000102030405060708090a0b0c")
        val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
        val okm = PlatformCrypto.hkdfSha256(ikm, salt, info, 42)
        val expectedOkm = hexToBytes(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )
        assertContentEquals(expectedOkm, okm)
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

}
