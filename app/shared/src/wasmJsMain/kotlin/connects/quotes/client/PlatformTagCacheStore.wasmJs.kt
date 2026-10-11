package connects.quotes.client

import connects.quotes.model.Tag

actual object PlatformTagCacheStore {
    private var inMemoryTags: List<Tag>? = null

    actual fun saveTags(tags: List<Tag>) {
        inMemoryTags = tags
    }

    actual fun loadTags(): List<Tag>? = inMemoryTags
}
