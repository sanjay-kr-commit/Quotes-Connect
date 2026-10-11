package connects.quotes.repository

import connects.quotes.model.BillboardItem
import connects.quotes.model.Id
import connects.quotes.model.PassageType

/**
 * Repository interface managing the public Billboard commons.
 */
interface BillboardRepository {

    /**
     * Retrieves advertised billboard items, optionally filtered by tag.
     */
    suspend fun getAdvertised(limit: Int = 50, offset: Int = 0, tag: String? = null): List<BillboardItem>

    /**
     * Advertises a passage on the billboard.
     * @param passageId The UUID of the passage.
     * @param isOrphan True if the passage is an unparented orphan (making it permanently advertised).
     */
    suspend fun advertise(passageId: Id, isOrphan: Boolean): Boolean

    /**
     * Removes an advertisement from the billboard.
     * Must fail if the passage is an orphan (orphans are permanently advertised).
     */
    suspend fun unadvertise(passageId: Id): Boolean

    suspend fun isAdvertised(passageId: Id): Boolean

    suspend fun isOrphan(passageId: Id): Boolean

    /**
     * Retrieves all passage IDs owned by an author that are currently advertised on the Billboard.
     */
    suspend fun getAdvertisedPassageIds(authorId: Id): Set<Id>

    /**
     * Sets the billboard advertisement status for all passages owned by an author of a specific type (or all types if null).
     * Returns the count of passages modified.
     */
    suspend fun setPassageTypeAdvertisement(authorId: Id, type: PassageType?, advertise: Boolean): Int
}
