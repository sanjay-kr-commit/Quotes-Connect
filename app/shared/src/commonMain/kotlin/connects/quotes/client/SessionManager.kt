package connects.quotes.client

import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.security.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the client's sovereign identity session.
 * Stores active session tokens and credentials in memory.
 * Coordinates with PlatformSessionStore for persistence of session tokens (without raw passphrases).
 */
object SessionManager {

    private val _currentIdentity = MutableStateFlow<Credentials?>(null)
    val currentIdentity: StateFlow<Credentials?> = _currentIdentity.asStateFlow()

    private val _currentSession = MutableStateFlow<AuthSession?>(null)
    val currentSession: StateFlow<AuthSession?> = _currentSession.asStateFlow()

    private val _isRememberMe = MutableStateFlow<Boolean>(true)
    val isRememberMe: StateFlow<Boolean> = _isRememberMe.asStateFlow()

    init {
        restorePersistedSession()
    }

    /**
     * Attempts to restore credentials/session persisted from a previous run.
     */
    fun restorePersistedSession(): PersistedSession? {
        val loaded = PlatformSessionStore.loadSession()
        if (loaded != null && loaded.rememberMe) {
            _isRememberMe.value = true
            val authSession = loaded.toAuthSession()
            if (authSession != null) {
                _currentSession.value = authSession
                val creds = loaded.toCredentials() ?: Credentials(authSession.authorId, "")
                _currentIdentity.value = creds
            } else {
                val creds = loaded.toCredentials()
                if (creds != null) {
                    _currentIdentity.value = creds
                }
            }
        }
        return loaded
    }

    /**
     * Sets the active session and optional in-memory credentials.
     */
    fun setSession(session: AuthSession, credentials: Credentials? = null, rememberMe: Boolean = _isRememberMe.value) {
        _currentSession.value = session
        val creds = credentials ?: Credentials(session.authorId, "")
        _currentIdentity.value = creds
        _isRememberMe.value = rememberMe
        if (rememberMe) {
            PlatformSessionStore.saveSession(PersistedSession.from(session, rememberMe = true))
        } else {
            PlatformSessionStore.clearSession()
        }
    }

    /**
     * Connects an in-memory identity (used during identity creation or legacy login).
     */
    fun setIdentity(credentials: Credentials, rememberMe: Boolean = _isRememberMe.value) {
        _currentIdentity.value = credentials
        _isRememberMe.value = rememberMe
        if (rememberMe) {
            val existing = _currentSession.value
            if (existing != null && existing.authorId == credentials.passageId) {
                PlatformSessionStore.saveSession(PersistedSession.from(existing, rememberMe = true))
            } else {
                PlatformSessionStore.saveSession(
                    PersistedSession(
                        authorId = credentials.passageId.value,
                        passageId = credentials.passageId.value,
                        passphrase = null, // Raw passphrase NEVER written to disk (SEC-006)
                        rememberMe = true
                    )
                )
            }
        } else {
            PlatformSessionStore.clearSession()
        }
    }

    /**
     * Disconnects the identity and session, wiping both in-memory and persistent storage.
     */
    fun clearIdentity() {
        _currentIdentity.value = null
        _currentSession.value = null
        PlatformSessionStore.clearSession()
    }

    fun setRememberMePreference(remember: Boolean) {
        _isRememberMe.value = remember
        val session = _currentSession.value
        if (session != null) {
            if (remember) {
                PlatformSessionStore.saveSession(PersistedSession.from(session, rememberMe = true))
            } else {
                PlatformSessionStore.clearSession()
            }
        } else {
            val creds = _currentIdentity.value
            if (creds != null) {
                if (remember) {
                    PlatformSessionStore.saveSession(PersistedSession.from(creds, rememberMe = true))
                } else {
                    PlatformSessionStore.clearSession()
                }
            }
        }
    }

    val currentAuthorId: Id?
        get() = _currentSession.value?.authorId ?: _currentIdentity.value?.passageId

    val currentAccessToken: String?
        get() = _currentSession.value?.accessToken
}
