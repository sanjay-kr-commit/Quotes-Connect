package connects.quotes.repository

import connects.quotes.model.Tag

/**
 * Repository interface managing globally indexed tags.
 */
interface TagRepository {

    suspend fun recordTags(tags: List<String>)

    suspend fun getTopTags(limit: Int = 30): List<Tag>

    suspend fun suggestTags(prefix: String, limit: Int = 10): List<Tag>
}
