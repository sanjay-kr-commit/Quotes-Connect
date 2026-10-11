package connects.quotes.client

actual object PlatformSessionStore {
    private var inMemorySession: PersistedSession? = null

    actual fun saveSession(session: PersistedSession) {
        inMemorySession = if (session.rememberMe) session else null
    }

    actual fun loadSession(): PersistedSession? = inMemorySession?.takeIf { it.rememberMe }

    actual fun clearSession() {
        inMemorySession = null
    }

    actual fun isRememberMe(): Boolean = inMemorySession?.rememberMe ?: false
}
