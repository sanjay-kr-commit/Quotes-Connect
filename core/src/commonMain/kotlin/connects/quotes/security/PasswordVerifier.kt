package connects.quotes.security

import connects.quotes.model.Id
import kotlinx.serialization.Serializable

/**
 * Server-side verifier record for sovereign credentials.
 * The raw passphrase is never stored; only the salted, computationally intensive hash.
 */
@Serializable
data class PasswordVerifierRecord(
    val authorId: Id,
    val scheme: String = SCHEME_PBKDF2_SHA256,
    val version: Int = 1,
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int = DEFAULT_ITERATIONS,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    companion object {
        const val SCHEME_PBKDF2_SHA256 = "PBKDF2-HMAC-SHA256"
        const val SCHEME_ARGON2ID = "ARGON2ID"
        const val DEFAULT_ITERATIONS = SecurityConfiguration.AUTH_PASSWORD_KDF_ITERATIONS
    }
}
