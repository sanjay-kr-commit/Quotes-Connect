package connects.quotes.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PlatformSessionStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var inMemorySession: PersistedSession? = null

    actual fun saveSession(session: PersistedSession) {
        inMemorySession = if (session.rememberMe) session else null
        try {
            if (session.rememberMe) {
                val str = json.encodeToString(session)
                js("window.localStorage.setItem('qc_persisted_session', str)")
            } else {
                js("window.localStorage.removeItem('qc_persisted_session')")
            }
        } catch (_: Throwable) {}
    }

    actual fun loadSession(): PersistedSession? {
        try {
            val raw: String? = js("window.localStorage.getItem('qc_persisted_session')") as? String
            if (raw != null) {
                val session = json.decodeFromString<PersistedSession>(raw)
                if (session.rememberMe) {
                    inMemorySession = session
                    return session
                }
            }
        } catch (_: Throwable) {}
        return inMemorySession?.takeIf { it.rememberMe }
    }

    actual fun clearSession() {
        inMemorySession = null
        try {
            js("window.localStorage.removeItem('qc_persisted_session')")
        } catch (_: Throwable) {}
    }

    actual fun isRememberMe(): Boolean {
        try {
            val raw: String? = js("window.localStorage.getItem('qc_persisted_session')") as? String
            if (raw != null) {
                val session = json.decodeFromString<PersistedSession>(raw)
                return session.rememberMe
            }
        } catch (_: Throwable) {}
        return inMemorySession?.rememberMe ?: false
    }
}
