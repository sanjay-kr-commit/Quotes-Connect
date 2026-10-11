package connects.quotes.crypto

import kotlinx.serialization.Serializable

/**
 * Public and private key pair for asymmetric end-to-end encryption.
 * The private key is strictly kept client-side and never leaves the user's device.
 */
@Serializable
data class CryptoKeyPair(
    val publicKey: String,  // Base64-encoded X.509 RSA-2048 public key
    val privateKey: String  // Base64-encoded PKCS8 RSA-2048 private key
)

/**
 * Cross-platform asymmetric encryption service.
 * Uses RSA-2048 with hybrid AES-256 envelope encryption.
 */
expect object PlatformCrypto {
    /**
     * Generates a new 2048-bit RSA asymmetric key pair.
     */
    suspend fun generateKeyPair(): CryptoKeyPair

    /**
     * Generates an ephemeral ECDH (P-256) key pair for session forward secrecy.
     */
    suspend fun generateAgreementKeyPair(): CryptoKeyPair

    /**
     * Derives a shared session secret using ECDH P-256.
     */
    suspend fun deriveAgreementSecret(privateKeyBase64: String, peerPublicKeyBase64: String): ByteArray

    /**
     * Signs a message using the user's RSA identity private key (SHA256withRSA).
     */
    suspend fun sign(message: ByteArray, privateKeyBase64: String): String

    /**
     * Verifies a signature using the peer's RSA identity public key (SHA256withRSA).
     */
    suspend fun verify(message: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean

    /** Computes HMAC-SHA-256 over message using key. */
    suspend fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray

    /** Derives key material with HKDF-SHA-256 (RFC 5869). */
    suspend fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray


    /**
     * Encrypts plaintext using the recipient's public key.
     * Uses hybrid envelope encryption (AES-256-GCM with RSA-OAEP / PKCS1 key wrap)
     * so that private citations and messages of any length can be securely encrypted.
     */
    suspend fun encrypt(plainText: String, recipientPublicKeyBase64: String): String

    /**
     * Decrypts ciphertext using the user's private key.
     */
    suspend fun decrypt(cipherText: String, privateKeyBase64: String): String

    /**
     * Encrypts plaintext using versioned CryptoEnvelope with authenticated associated data (AAD).
     */
    suspend fun encryptEnvelope(
        plainText: String,
        recipientPublicKeyBase64: String,
        aad: String? = null,
        keyId: String? = null
    ): connects.quotes.security.CryptoEnvelope

    /**
     * Decrypts versioned CryptoEnvelope using private key, strictly verifying AEAD authentication.
     */
    suspend fun decryptEnvelope(
        envelope: connects.quotes.security.CryptoEnvelope,
        privateKeyBase64: String
    ): String

    /**
     * Symmetrically encrypts plaintext (e.g. serialized CryptoKeyPair) using user password.
     */
    suspend fun encryptSymmetric(plainText: String, password: String): EncryptedKeyStoreBlob

    /**
     * Symmetrically decrypts blob using user password.
     */
    suspend fun decryptSymmetric(blob: EncryptedKeyStoreBlob, password: String): String

    /**
     * Encrypts plaintext using the server's ephemeral public key for asymmetric channel transport over HTTP.
     * Returns the raw generated AES key bytes (for decrypting the server's response) and the EncryptedChannelPayload.
     */
    suspend fun encryptChannel(
        plainText: String,
        serverPublicKeyBase64: String,
        keyId: String
    ): Pair<ByteArray, connects.quotes.model.EncryptedChannelPayload>

    /**
     * Decrypts an encrypted response from the server using the shared AES key.
     */
    suspend fun decryptChannel(
        cipherTextBase64: String,
        ivBase64: String,
        aesKey: ByteArray, aad: String? = null
    ): String
}

/**
 * Symmetrically encrypted keystore blob for sovereign cloud backup.
 * 
 * Format:
 * - Version 2: legacy PBKDF2-HMAC-SHA-256 with 100,000 iterations
 * - Version 3+: current PBKDF2-HMAC-SHA256 contract with 600,000 iterations + AES-256-GCM
 */
@Serializable
data class EncryptedKeyStoreBlob(
    val encryptedPayload: String, // Base64-encoded ciphertext including GCM tag
    val salt: String,             // Base64-encoded salt
    val iv: String,               // Base64-encoded nonce
    val version: Int = KeystoreCryptoContract.CURRENT_BLOB_VERSION,
    val kdf: String = KeystoreCryptoContract.KDF,
    val kdfIterations: Int = KeystoreCryptoContract.KDF_ITERATIONS,
    val cipher: String = KeystoreCryptoContract.CIPHER
) {
    init {
        if (version !in setOf(KeystoreCryptoContract.LEGACY_BLOB_VERSION, KeystoreCryptoContract.CURRENT_BLOB_VERSION)) {
            throw UnsupportedKeystoreEnvelopeException("Unsupported keystore blob version: $version")
        }
        if (salt.isBlank()) throw InvalidKeystoreEnvelopeException("Salt must not be blank")
        if (iv.isBlank()) throw InvalidKeystoreEnvelopeException("Nonce must not be blank")
        if (encryptedPayload.isBlank()) throw InvalidKeystoreEnvelopeException("Encrypted payload must not be blank")
        when (version) {
            KeystoreCryptoContract.LEGACY_BLOB_VERSION -> {
                if (kdf != KeystoreCryptoContract.KDF) throw InvalidKeystoreEnvelopeException("Unsupported legacy KDF: $kdf")
                if (kdfIterations != KeystoreCryptoContract.LEGACY_KDF_ITERATIONS) {
                    throw InvalidKeystoreEnvelopeException("Invalid legacy PBKDF2 iteration count: $kdfIterations")
                }
                if (cipher != KeystoreCryptoContract.CIPHER) throw InvalidKeystoreEnvelopeException("Unsupported legacy cipher: $cipher")
            }
            KeystoreCryptoContract.CURRENT_BLOB_VERSION -> {
                if (kdf != KeystoreCryptoContract.KDF) throw InvalidKeystoreEnvelopeException("Unsupported KDF: $kdf")
                if (kdfIterations != KeystoreCryptoContract.KDF_ITERATIONS) {
                    throw InvalidKeystoreEnvelopeException("Invalid PBKDF2 iteration count: $kdfIterations")
                }
                if (cipher != KeystoreCryptoContract.CIPHER) throw InvalidKeystoreEnvelopeException("Unsupported cipher: $cipher")
            }
        }
    }
}
