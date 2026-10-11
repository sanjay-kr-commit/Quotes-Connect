package connects.quotes.security

import connects.quotes.model.Id
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Server-side password verifier and token generator.
 * Implements salted PBKDF2-HMAC-SHA256 with 100,000 iterations and constant-time equality checks.
 * Raw passphrases and plain session tokens are never stored in the database.
 */
object PasswordHasher {
    private val secureRandom = SecureRandom()
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hashPassphrase(authorId: Id, passphrase: CharArray): PasswordVerifierRecord {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        val now = System.currentTimeMillis()
        return PasswordVerifierRecord(
            authorId = authorId,
            scheme = PasswordVerifierRecord.SCHEME_PBKDF2_SHA256,
            version = 1,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = ITERATIONS,
            createdAt = now,
            updatedAt = now
        )
    }

    fun verifyPassphrase(passphrase: CharArray, record: PasswordVerifierRecord): Boolean {
        return try {
            val salt = Base64.getDecoder().decode(record.saltBase64)
            val expectedHash = Base64.getDecoder().decode(record.hashBase64)
            val spec = PBEKeySpec(passphrase, salt, record.iterations, expectedHash.size * 8)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val actualHash = factory.generateSecret(spec).encoded
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Generates a 256-bit cryptographically secure session token (64 hex characters).
     */
    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v ushr 4, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }

    /**
     * Hashes an opaque session token using SHA-256 before database storage.
     */
    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
