package connects.quotes.model

import connects.quotes.security.PlatformSecureRandom
import kotlinx.serialization.Serializable

/**
 * Sovereign bearer credentials for an Author passage.
 * No email, phone, or PII is ever collected.
 * The creator receives the Author UUID and a randomized alphanumeric passphrase for high security.
 */
@Serializable
data class Credentials(
    val passageId: Id,
    val passphrase: String
) {
    companion object {
        private const val ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /**
         * Generates a strong random alphanumeric passphrase (32 characters default).
         * Generated with cryptographically secure randomness and designed to provide high entropy (~190 bits).
         */
        fun generatePassphrase(length: Int = 32): String {
            val sb = StringBuilder(length)
            for (i in 0 until length) {
                val index = PlatformSecureRandom.nextInt(ALPHANUMERIC_CHARS.length)
                sb.append(ALPHANUMERIC_CHARS[index])
            }
            return sb.toString()
        }
    }
}
