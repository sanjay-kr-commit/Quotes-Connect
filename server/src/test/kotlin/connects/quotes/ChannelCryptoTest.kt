package connects.quotes

import connects.quotes.crypto.ServerChannelCrypto
import connects.quotes.model.EncryptedChannelPayload
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class ChannelCryptoTest {

    @Test
    fun testServerChannelHandshakeReturnsValidDetails() {
        val handshake = ServerChannelCrypto.getHandshake()
        assertNotNull(handshake.keyId)
        assertTrue(handshake.keyId.isNotBlank())
        assertNotNull(handshake.serverPublicKey)
        assertTrue(handshake.serverPublicKey.isNotBlank())
        assertEquals("RSA-2048/AES-256-GCM", handshake.algorithm)
        assertTrue(handshake.timestamp > 0)
    }

    @Test
    fun testAsymmetricChannelEncryptionAndDecryptionRoundTrip() {
        val handshake = ServerChannelCrypto.getHandshake()
        val originalQuery = """{"query":"query { billboard { isOrphan } }"}"""

        // 1. Client simulates generating AES key and encrypting query
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val aesKey = keyGen.generateKey()
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val reqId = "REQ-TEST-12345"
        val aadString = "${handshake.keyId}:$reqId"
        
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        aesCipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))
        val cipherTextBytes = aesCipher.doFinal(originalQuery.toByteArray(Charsets.UTF_8))

        // Client encrypts AES key with server's RSA public key
        val pubBytes = Base64.getDecoder().decode(handshake.serverPublicKey)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pubBytes))
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepParams)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val envelope = EncryptedChannelPayload(
            keyId = handshake.keyId,
            requestId = reqId,
            encryptedAesKey = Base64.getEncoder().encodeToString(encryptedAesKey),
            iv = Base64.getEncoder().encodeToString(iv),
            cipherText = Base64.getEncoder().encodeToString(cipherTextBytes)
        )

        // 2. Server decrypts request envelope
        val (serverRecoveredAesKey, decryptedQuery) = ServerChannelCrypto.decryptRequest(envelope)
        assertEquals(originalQuery, decryptedQuery)
        assertContentEquals(aesKey.encoded, serverRecoveredAesKey)

        // 3. Server encrypts response
        val mockResponseJson = """{"data":{"billboard":[]}}"""
        val encryptedResponse = ServerChannelCrypto.encryptResponse(serverRecoveredAesKey, mockResponseJson, envelope.requestId, envelope.keyId)

        // 4. Client decrypts response using its AES key
        val respIv = Base64.getDecoder().decode(encryptedResponse.iv)
        val respCipher = Base64.getDecoder().decode(encryptedResponse.cipherText)
        val clientDecryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        clientDecryptCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, respIv))
        val respAad = "${handshake.keyId}:${encryptedResponse.requestId}"
        clientDecryptCipher.updateAAD(respAad.toByteArray(Charsets.UTF_8))
        val decryptedResponse = String(clientDecryptCipher.doFinal(respCipher), Charsets.UTF_8)

        assertEquals(mockResponseJson, decryptedResponse)
    }

    @Test
    fun testKeyRotationUpdatesKeyId() {
        val initialId = ServerChannelCrypto.keyId
        ServerChannelCrypto.rotateKeys()
        val rotatedId = ServerChannelCrypto.keyId
        assertNotEquals(initialId, rotatedId)
    }
}
