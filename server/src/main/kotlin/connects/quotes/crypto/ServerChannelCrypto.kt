package connects.quotes.crypto

import connects.quotes.model.ChannelHandshakeResponse
import connects.quotes.model.EncryptedChannelPayload
import connects.quotes.model.EncryptedChannelResponse
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ephemeral Asymmetric Channel Cryptography Engine for Ktor Server.
 * Secures communication against Man-In-The-Middle (MITM) attacks on unencrypted HTTP networks.
 *
 * Protocol:
 * 1. Server generates ephemeral RSA-2048 key pair.
 * 2. Client retrieves public key via `GET /api/channel/handshake`.
 * 3. Client encrypts request payload with AES-256-GCM and wraps AES key with Server's RSA public key.
 * 4. Server decrypts AES key with private key, decrypts payload, processes request, and encrypts response with that AES key.
 */
object ServerChannelCrypto {
    private val secureRandom = SecureRandom()
    private var keyPair: KeyPair
    var keyId: String
        private set
    var publicKeyBase64: String
        private set
    private var generatedAt: Long = System.currentTimeMillis()

    private val acceptedRequests = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        val (kp, id, pubB64) = generateNewKeyPair()
        keyPair = kp
        keyId = id
        publicKeyBase64 = pubB64
    }

    private fun generateNewKeyPair(): Triple<KeyPair, String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val kp = kpg.generateKeyPair()
        val id = UUID.randomUUID().toString().take(8)
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        return Triple(kp, id, pubB64)
    }

    /**
     * Rotates the ephemeral asymmetric key pair.
     */
    fun rotateKeys() {
        val (kp, id, pubB64) = generateNewKeyPair()
        keyPair = kp
        keyId = id
        publicKeyBase64 = pubB64
        generatedAt = System.currentTimeMillis()
        acceptedRequests.clear()
    }

    /**
     * Produces the public handshake payload for connecting clients.
     */
    fun getHandshake(): ChannelHandshakeResponse {
        return ChannelHandshakeResponse(
            keyId = keyId,
            serverPublicKey = publicKeyBase64,
            algorithm = "RSA-2048/AES-256-GCM",
            timestamp = generatedAt
        )
    }

    /**
     * Decrypts an incoming client request envelope.
     * Returns the recovered AES key bytes and the decrypted plaintext UTF-8 JSON request.
     */
    fun decryptRequest(envelope: EncryptedChannelPayload): Pair<ByteArray, String> {
        return try {
            if (envelope.keyId != this.keyId) {
                throw SecurityException("Unknown or expired channel key ID: ${envelope.keyId}")
            }
            // 1. Unwrap AES-256 session key using Server's private RSA key (OAEP with SHA-256, fallback to PKCS1 for legacy compatibility)
            val encryptedAesKeyBytes = Base64.getDecoder().decode(envelope.encryptedAesKey.trim())
            val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
            rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private, oaepParams)
            val rawAesKey = rsaCipher.doFinal(encryptedAesKeyBytes)

            // 2. Decrypt AES-GCM ciphertext payload
            val iv = Base64.getDecoder().decode(envelope.iv.trim())
            val cipherText = Base64.getDecoder().decode(envelope.cipherText.trim())
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(rawAesKey, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            aesCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val aadString = "${envelope.keyId}:${envelope.requestId}"
            aesCipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))
            val plainBytes = aesCipher.doFinal(cipherText)

            // 3. Prevent Replay Attacks (Atomic Check-and-Record)
            val requestKey = "${envelope.keyId}:${envelope.requestId}"
            if (acceptedRequests.putIfAbsent(requestKey, System.currentTimeMillis()) != null) {
                throw connects.quotes.security.ReplayDetected("Duplicate request ID: ${envelope.requestId}")
            }

            Pair(rawAesKey, String(plainBytes, Charsets.UTF_8))
        } catch (e: connects.quotes.security.ReplayDetected) {
            throw e
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt asymmetric channel payload", e)
        }
    }

    /**
     * Encrypts server response to client using the shared AES key established during request decryption.
     */
    fun encryptResponse(rawAesKey: ByteArray, plainResponse: String, requestId: String, keyId: String): EncryptedChannelResponse {
        return try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(rawAesKey, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            aesCipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            
            val aadString = "$keyId:$requestId"
            aesCipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))
            
            val cipherBytes = aesCipher.doFinal(plainResponse.toByteArray(Charsets.UTF_8))

            EncryptedChannelResponse(
                requestId = requestId,
                iv = Base64.getEncoder().encodeToString(iv),
                cipherText = Base64.getEncoder().encodeToString(cipherBytes)
            )
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt asymmetric channel response", e)
        }
    }
}
