package connects.quotes.client

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformSessionStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var appContext: Context? = null
    private var inMemorySession: PersistedSession? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val sess = loadSession()
        if (sess != null) {
            val auth = sess.toAuthSession()
            if (auth != null) {
                SessionManager.setSession(auth, credentials = sess.toCredentials(), rememberMe = true)
            } else {
                val creds = sess.toCredentials()
                if (creds != null) {
                    SessionManager.setIdentity(creds, rememberMe = true)
                }
            }
        }
    }

    private fun getSessionFile(): File? {
        val ctx = appContext
        val dir = if (ctx != null) {
            ctx.filesDir
        } else {
            val tmp = System.getProperty("java.io.tmpdir")
            if (tmp != null) File(tmp) else null
        }
        return if (dir != null) File(dir, "quotes_connect_session.json") else null
    }

    actual fun saveSession(session: PersistedSession) {
        inMemorySession = if (session.rememberMe) session else null

        val file = getSessionFile() ?: return
        try {
            if (session.rememberMe) {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(session))
            } else {
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Ignore error gracefully
        }
    }

    actual fun loadSession(): PersistedSession? {
        val file = getSessionFile()
        if (file != null && file.exists()) {
            try {
                val content = file.readText()
                val session = json.decodeFromString<PersistedSession>(content)
                if (session.rememberMe) {
                    inMemorySession = session
                    return session
                }
            } catch (_: Exception) {
                // Ignore error gracefully
            }
        }
        return inMemorySession?.takeIf { it.rememberMe }
    }

    actual fun clearSession() {
        inMemorySession = null
        val file = getSessionFile() ?: return
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    actual fun isRememberMe(): Boolean {
        val file = getSessionFile()
        if (file != null && file.exists()) {
            return try {
                val content = file.readText()
                val session = json.decodeFromString<PersistedSession>(content)
                session.rememberMe
            } catch (_: Exception) {
                false
            }
        }
        return inMemorySession?.rememberMe ?: false
    }
}
