package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * Directed edge representing that a source passage subscribes to a target passage.
 * Used for building the Directed Acyclic Graph (DAG).
 */
@Serializable
data class Subscription(
    val id: Id = Id.generate(),
    val sourceId: Id, // The passage initiating the subscription (e.g. Comment)
    val targetId: Id, // The passage being subscribed to (e.g. Quote)
    val createdAt: Long
)
