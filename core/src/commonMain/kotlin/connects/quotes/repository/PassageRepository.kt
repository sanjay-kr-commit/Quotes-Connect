package connects.quotes.repository

import connects.quotes.model.Id
import connects.quotes.model.Passage

/**
 * Core repository interface managing the persistence and retrieval of passages and relationships.
 */
interface PassageRepository {

    suspend fun get(id: Id): Passage?

    suspend fun getAll(limit: Int = 50, offset: Int = 0): List<Passage>

    /**
     * Saves a passage.
     * @param passage The public polymorphic passage.
     * @param parentId Optional creator/author UUID. Stored privately on server, omitted from public passage.
     */
    suspend fun save(passage: Passage, parentId: Id? = null)

    /**
     * Deletes a passage if permitted.
     * Returns false if the passage is recordable or cannot be deleted.
     */
    suspend fun delete(id: Id): Boolean

    suspend fun isRecordable(id: Id): Boolean

    /**
     * Server-private retrieval of the creator/parent ID.
     */
    suspend fun getParentId(id: Id): Id?

    suspend fun search(query: String, limit: Int = 30): List<Passage>

    suspend fun getByTag(tag: String, limit: Int = 30): List<Passage>

    suspend fun addSubscription(sourceId: Id, targetId: Id): Boolean

    suspend fun removeSubscription(sourceId: Id, targetId: Id): Boolean

    suspend fun getSubscribedTo(id: Id): List<Id>

    suspend fun getSubscribers(id: Id): List<Id>

    /**
     * Retrieves all passages created by an author, including the author passage itself.
     */
    suspend fun getCreatedByAuthor(authorId: Id): List<Passage>

    /**
     * Lazily evaluates and retrieves the feed for an author, including directly subscribed passages
     * and their children (passages created by subscribed authors and citations attached to subscribed passages).
     */
    suspend fun getFeed(authorId: Id, depth: Int = 1, limit: Int = 50, offset: Int = 0): List<Passage>
}
