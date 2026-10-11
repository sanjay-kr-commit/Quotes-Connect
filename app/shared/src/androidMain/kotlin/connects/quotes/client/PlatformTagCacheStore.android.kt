package connects.quotes.client

import android.content.Context
import connects.quotes.model.Tag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformTagCacheStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getCacheFile(): File? {
        val ctx = appContext
        val dir = if (ctx != null) {
            ctx.filesDir
        } else {
            val tmp = System.getProperty("java.io.tmpdir")
            if (tmp != null) File(tmp) else null
        }
        return if (dir != null) File(dir, "quotes_connect_tags_cache.json") else null
    }

    actual fun saveTags(tags: List<Tag>) {
        val file = getCacheFile() ?: return
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(tags))
        } catch (_: Exception) {}
    }

    actual fun loadTags(): List<Tag>? {
        val file = getCacheFile() ?: return null
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            json.decodeFromString<List<Tag>>(content)
        } catch (_: Exception) {
            null
        }
    }
}
