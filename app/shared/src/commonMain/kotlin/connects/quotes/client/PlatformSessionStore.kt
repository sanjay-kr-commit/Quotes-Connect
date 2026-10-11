package connects.quotes.client

import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.security.AuthSession
import kotlinx.serialization.Serializable

/**
 * Persisted session data representation.
 * Stores revocable session tokens issued by the server.
 * NEVER stores raw author credentials or private keys in plaintext (BUG-006).
 */
@Serializable
data class PersistedSession(
    val authorId: String = "",
    val accessToken: String = "",
    val sessionId: String = "",
    val expiresAt: Long = 0L,
    val rememberMe: Boolean = true,
    // Legacy compatibility fields (read-only for migration; never newly written)
    val passageId: String? = null,
    val passphrase: String? = null
) {
    val resolvedAuthorId: String
        get() = if (authorId.isNotBlank()) authorId else (passageId ?: "")

    fun toAuthSession(): AuthSession? {
        val resolved = resolvedAuthorId
        if (resolved.isBlank() || accessToken.isBlank()) return null
        return AuthSession(
            sessionId = Id(if (sessionId.isNotBlank()) sessionId else "sess-$resolved"),
            authorId = Id(resolved),
            accessToken = accessToken,
            refreshToken = null,
            issuedAt = 0L,
            expiresAt = if (expiresAt > 0) expiresAt else Long.MAX_VALUE
        )
    }

    fun toCredentials(): Credentials? {
        val resolved = resolvedAuthorId
        val pass = passphrase
        if (resolved.isBlank() || pass.isNullOrBlank()) return null
        return Credentials(Id(resolved), pass)
    }

    companion object {
        fun from(session: AuthSession, rememberMe: Boolean = true): PersistedSession =
            PersistedSession(
                authorId = session.authorId.value,
                accessToken = session.accessToken,
                sessionId = session.sessionId.value,
                expiresAt = session.expiresAt,
                rememberMe = rememberMe
            )

        fun from(credentials: Credentials, rememberMe: Boolean = true): PersistedSession =
            PersistedSession(
                authorId = credentials.passageId.value,
                accessToken = "",
                sessionId = "",
                expiresAt = 0L,
                rememberMe = rememberMe
            )
    }
}

/**
 * Platform-specific storage for persisting the sovereign identity session across app restarts.
 */
expect object PlatformSessionStore {
    fun saveSession(session: PersistedSession)
    fun loadSession(): PersistedSession?
    fun clearSession()
    fun isRememberMe(): Boolean
}
