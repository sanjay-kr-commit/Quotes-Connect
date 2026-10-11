package connects.quotes.security

import java.security.SecureRandom

actual object PlatformSecureRandom : SecureRandomSource {
    private val random = SecureRandom()

    actual override fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    actual override fun nextLong(): Long = random.nextLong()

    actual override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
