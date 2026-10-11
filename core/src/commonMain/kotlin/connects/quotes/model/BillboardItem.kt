package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * An item broadcast on the public Billboard commons.
 * Can be an explicitly advertised owned passage or a permanently public orphan passage.
 */
@Serializable
data class BillboardItem(
    val passage: Passage,
    val isOrphan: Boolean,
    val advertisedAt: Long
)
