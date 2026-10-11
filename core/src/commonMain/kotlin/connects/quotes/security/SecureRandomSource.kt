package connects.quotes.security

/**
 * Interface providing cryptographically secure pseudo-random bytes and numbers.
 * Backed by operating system CSPRNG (e.g. java.security.SecureRandom, SecRandomCopyBytes, crypto.getRandomValues).
 */
interface SecureRandomSource {
    fun nextBytes(size: Int): ByteArray
    fun nextLong(): Long
    fun nextInt(bound: Int): Int
}

expect object PlatformSecureRandom : SecureRandomSource {
    override fun nextBytes(size: Int): ByteArray
    override fun nextLong(): Long
    override fun nextInt(bound: Int): Int
}
