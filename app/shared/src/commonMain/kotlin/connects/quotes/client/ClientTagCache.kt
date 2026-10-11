package connects.quotes.client

import connects.quotes.model.Tag

/**
 * Sovereign Client-Side Tag Cache.
 * Caches tags locally for Desktop and Android to reduce server call wastage and provide
 * instant zero-latency prefix filtering and autocorrect for tag suggestions.
 */
object ClientTagCache {
    private var inMemoryTags: List<Tag> = emptyList()
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            val loaded = try {
                PlatformTagCacheStore.loadTags()
            } catch (_: Throwable) {
                null
            }
            if (!loaded.isNullOrEmpty()) {
                inMemoryTags = loaded
            }
            initialized = true
        }
    }

    /**
     * Returns all currently cached tags.
     */
    fun getTags(): List<Tag> {
        ensureInitialized()
        return inMemoryTags
    }

    /**
     * Updates the local tag cache with latest tags from server and saves to persistent storage.
     */
    fun updateTags(tags: List<Tag>) {
        if (tags.isEmpty()) return
        ensureInitialized()
        val tagMap = inMemoryTags.associateBy { it.name }.toMutableMap()
        for (tag in tags) {
            tagMap[tag.name] = tag
        }
        val merged = tagMap.values.sortedByDescending { it.count }
        inMemoryTags = merged
        try {
            PlatformTagCacheStore.saveTags(merged)
        } catch (_: Throwable) {}
    }

    /**
     * Instantly suggests matching tags for autocorrect/autocomplete based on query.
     * Prioritizes prefix matches first, followed by substring matches, ordered by usage count.
     */
    fun suggestTags(query: String, limit: Int = 10): List<Tag> {
        ensureInitialized()
        val q = Tag.normalize(query)
        if (q.isBlank()) {
            return inMemoryTags.take(limit)
        }
        val prefixMatches = inMemoryTags.filter { it.name.startsWith(q) }.sortedByDescending { it.count }
        val containsMatches = inMemoryTags.filter { !it.name.startsWith(q) && it.name.contains(q) }.sortedByDescending { it.count }
        return (prefixMatches + containsMatches).take(limit)
    }

    /**
     * Clears all cached tags in memory and persistent storage.
     */
    fun clear() {
        inMemoryTags = emptyList()
        initialized = true
        try {
            PlatformTagCacheStore.saveTags(emptyList())
        } catch (_: Throwable) {}
    }
}
