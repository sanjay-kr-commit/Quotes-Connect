package connects.quotes.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformSessionStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun getSessionFile(): File {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, ".quotes_connect")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "session.json")
    }

    actual fun saveSession(session: PersistedSession) {
        val file = getSessionFile()
        if (session.rememberMe) {
            file.writeText(json.encodeToString(session))
        } else {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    actual fun loadSession(): PersistedSession? {
        val file = getSessionFile()
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val session = json.decodeFromString<PersistedSession>(content)
            if (session.rememberMe) session else null
        } catch (_: Exception) {
            null
        }
    }

    actual fun clearSession() {
        val file = getSessionFile()
        if (file.exists()) {
            file.delete()
        }
    }

    actual fun isRememberMe(): Boolean {
        val file = getSessionFile()
        if (!file.exists()) return false
        return try {
            val content = file.readText()
            val session = json.decodeFromString<PersistedSession>(content)
            session.rememberMe
        } catch (_: Exception) {
            false
        }
    }
}
