package connects.quotes.model

import connects.quotes.security.PlatformSecureRandom
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Unique identifier for every entity in Quotes Connect.
 * Formatted as a standard UUID string generated with cryptographically secure randomness.
 */
@Serializable
@JvmInline
value class Id(val value: String) {
    override fun toString(): String = value

    companion object {
        /**
         * Generates a multiplatform RFC 4122 version 4 UUID.
         * Backed by operating system CSPRNG (SecureRandom / WebCrypto / /dev/urandom).
         */
        fun generate(): Id {
            val randomBytes = PlatformSecureRandom.nextBytes(16)
            // Set version 4
            randomBytes[6] = ((randomBytes[6].toInt() and 0x0F) or 0x40).toByte()
            // Set variant (RFC 4122)
            randomBytes[8] = ((randomBytes[8].toInt() and 0x3F) or 0x80).toByte()

            val hexChars = "0123456789abcdef"
            val sb = StringBuilder(36)
            for (i in 0 until 16) {
                if (i == 4 || i == 6 || i == 8 || i == 10) {
                    sb.append('-')
                }
                val b = randomBytes[i].toInt() and 0xFF
                sb.append(hexChars[b ushr 4])
                sb.append(hexChars[b and 0x0F])
            }
            return Id(sb.toString())
        }

        fun from(raw: String): Id = Id(raw.trim())
    }
}
