package connects.quotes.usecase

import connects.quotes.graph.DagValidator
import connects.quotes.model.Id
import connects.quotes.model.Notification
import connects.quotes.repository.NotificationRepository
import connects.quotes.repository.PassageRepository

/**
 * Use case to establish a subscription edge between two passages.
 * Enforces DAG constraints to prevent circular loops.
 */
class SubscribePassageUseCase(
    private val passageRepository: PassageRepository,
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        sourceId: Id,
        targetId: Id,
        currentTimeMillis: Long
    ): Result<Boolean> {
        val source = passageRepository.get(sourceId)
            ?: return Result.failure(IllegalArgumentException("Source passage $sourceId not found"))
        val target = passageRepository.get(targetId)
            ?: return Result.failure(IllegalArgumentException("Target passage $targetId not found"))

        // Check if already subscribed
        if (source.subscribedToIds.contains(targetId)) {
            return Result.success(true)
        }

        // Validate DAG acyclic property
        val createsCycle = DagValidator.wouldCreateCycle(
            sourceId = sourceId,
            targetId = targetId,
            getOutgoing = { id ->
                passageRepository.getSubscribedTo(id)
            }
        )

        if (createsCycle) {
            return Result.failure(
                IllegalStateException("Subscription from $sourceId to $targetId would create a cycle in the DAG")
            )
        }

        // Add subscription
        val added = passageRepository.addSubscription(sourceId = sourceId, targetId = targetId)
        if (added) {
            // Notify target author
            val targetParent = passageRepository.getParentId(targetId)
            val sourceParent = passageRepository.getParentId(sourceId)
            if (targetParent != null && targetParent != sourceParent) {
                notificationRepository.addNotification(
                    Notification(
                        targetPassageId = targetId,
                        subscriberPassageId = sourceId,
                        subscriberType = source.type,
                        summary = "A ${source.type} subscribed to your passage",
                        createdAt = currentTimeMillis
                    )
                )
            }
        }

        return Result.success(added)
    }
}
