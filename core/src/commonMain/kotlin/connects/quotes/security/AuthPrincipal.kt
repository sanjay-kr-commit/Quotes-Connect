package connects.quotes.security

import connects.quotes.model.Id
import connects.quotes.util.currentTimeMillis
import kotlinx.serialization.Serializable

/**
 * Server-authenticated request principal.
 * Attached to authorized HTTP / GraphQL requests; replaces caller-supplied unverified author IDs.
 */
@Serializable
data class AuthPrincipal(
    val authorId: Id,
    val sessionId: Id,
    val deviceId: Id? = null,
    val issuedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val scopes: Set<String> = setOf("author")
) {
    fun isExpired(now: Long = currentTimeMillis()): Boolean = expiresAt > 0L && now >= expiresAt
}

/**
 * Authenticated session issued to the client upon successful passphrase verification.
 * The client stores this opaque token, NOT the raw passphrase.
 */
@Serializable
data class AuthSession(
    val sessionId: Id,
    val authorId: Id,
    val accessToken: String,
    val refreshToken: String? = null,
    val issuedAt: Long = 0L,
    val expiresAt: Long = 0L
) {
    val token: String get() = accessToken
}

