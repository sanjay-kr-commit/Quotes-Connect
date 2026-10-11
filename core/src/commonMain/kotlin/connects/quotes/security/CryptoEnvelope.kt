package connects.quotes.security

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Versioned cryptographic envelope format for end-to-end encryption.
 * Encapsulates algorithm suite, key identities, nonce/IV, authenticated associated data (AAD),
 * ciphertext, and optional digital signature.
 */
@OptIn(ExperimentalEncodingApi::class)
@Serializable
data class CryptoEnvelope(
    val version: Int = 1,
    val suite: String = SUITE_RSA2048_AES256GCM,
    val keyId: String? = null,
    val senderKeyId: String? = null,
    val recipientKeyId: String? = null,
    val nonce: String,              // Base64-encoded IV / nonce (12 bytes for AES-GCM)
    val aad: String? = null,        // UTF-8 associated data string bound to ciphertext
    val ciphertext: String,         // Base64-encoded ciphertext (including GCM tag)
    val signature: String? = null   // Base64-encoded optional signature by identity key
) {
    init {
        require(version == 1) { "Unsupported envelope version: $version" }
        require(suite == SUITE_RSA2048_AES256GCM || suite == SUITE_CHANNEL_EPHEMERAL) { "Unsupported cryptographic suite: $suite" }
        
        // Base64 and length validation for nonce
        require(nonce.isNotBlank()) { "Nonce must not be blank" }
        require(!nonce.contains(Regex("\\s"))) { "Nonce Base64 must not contain whitespace" }
        val decodedNonce = try {
            Base64.decode(nonce)
        } catch (e: Exception) {
            throw IllegalArgumentException("Nonce must be valid Base64", e)
        }
        if (suite == SUITE_RSA2048_AES256GCM || suite == SUITE_CHANNEL_EPHEMERAL) {
            require(decodedNonce.size == 12) { "AES-GCM nonce must be exactly 12 bytes" }
        }

        // Base64 and structure validation for ciphertext
        require(ciphertext.isNotBlank()) { "Ciphertext must not be blank" }
        require(!ciphertext.contains(Regex("\\s"))) { "Ciphertext Base64 must not contain whitespace" }
        
        if (suite == SUITE_RSA2048_AES256GCM) {
            val parts = ciphertext.split(":")
            require(parts.size == 2) { "Asymmetric ciphertext must be in format Base64(wrappedKey):Base64(gcmPayload)" }
            try {
                Base64.decode(parts[0])
                Base64.decode(parts[1])
            } catch (e: Exception) {
                throw IllegalArgumentException("Ciphertext components must be valid Base64", e)
            }
        } else {
            try {
                Base64.decode(ciphertext)
            } catch (e: Exception) {
                throw IllegalArgumentException("Ciphertext must be valid Base64", e)
            }
        }
    }

    companion object {
        const val SUITE_RSA2048_AES256GCM = SecurityConfiguration.RSA_HYBRID_SUITE_ID
        const val SUITE_CHANNEL_EPHEMERAL = SecurityConfiguration.CHANNEL_HYBRID_SUITE_ID
    }
}
