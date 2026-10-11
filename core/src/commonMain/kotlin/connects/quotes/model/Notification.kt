package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * An alert representing an inbound connection in the knowledge graph
 * (e.g. someone commented on, cited, or subscribed to a passage owned by the author).
 */
@Serializable
data class Notification(
    val id: Id = Id.generate(),
    val targetPassageId: Id,
    val subscriberPassageId: Id,
    val subscriberType: PassageType,
    val summary: String,
    val createdAt: Long,
    val isRead: Boolean = false
)
