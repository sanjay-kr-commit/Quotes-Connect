package connects.quotes.client

import connects.quotes.model.Tag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformTagCacheStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun getCacheFile(): File {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, ".quotes_connect")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "tags_cache.json")
    }

    actual fun saveTags(tags: List<Tag>) {
        try {
            val file = getCacheFile()
            file.writeText(json.encodeToString(tags))
        } catch (_: Exception) {}
    }

    actual fun loadTags(): List<Tag>? {
        return try {
            val file = getCacheFile()
            if (!file.exists()) return null
            val content = file.readText()
            json.decodeFromString<List<Tag>>(content)
        } catch (_: Exception) {
            null
        }
    }
}
