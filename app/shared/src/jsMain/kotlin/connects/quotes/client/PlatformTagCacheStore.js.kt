package connects.quotes.client

import connects.quotes.model.Tag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PlatformTagCacheStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var inMemoryTags: List<Tag>? = null

    actual fun saveTags(tags: List<Tag>) {
        inMemoryTags = tags
        try {
            val str = json.encodeToString(tags)
            js("window.localStorage.setItem('qc_tags_cache', str)")
        } catch (_: Throwable) {}
    }

    actual fun loadTags(): List<Tag>? {
        try {
            val raw: String? = js("window.localStorage.getItem('qc_tags_cache')") as? String
            if (raw != null) {
                val list = json.decodeFromString<List<Tag>>(raw)
                inMemoryTags = list
                return list
            }
        } catch (_: Throwable) {}
        return inMemoryTags
    }
}
