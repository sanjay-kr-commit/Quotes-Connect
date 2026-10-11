package connects.quotes.client

import connects.quotes.model.Tag

/**
 * Platform-specific storage for caching tags on the client (Desktop & Android)
 * to reduce server round-trips and provide instant zero-latency autocomplete/autocorrect.
 */
expect object PlatformTagCacheStore {
    fun saveTags(tags: List<Tag>)
    fun loadTags(): List<Tag>?
}
