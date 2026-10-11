package connects.quotes.usecase

import connects.quotes.graph.DagValidator
import connects.quotes.model.*
import connects.quotes.repository.BillboardRepository
import connects.quotes.repository.NotificationRepository
import connects.quotes.repository.PassageRepository
import connects.quotes.repository.TagRepository

/**
 * Use case enforcing the foundational creation rules of Quotes Connect:
 * 1. Creation-time immutable ownership: A parent can only claim ownership at creation.
 * 2. Mandatory auto-billboard for orphan passages: If parentId is null, it is automatically
 *    and permanently advertised on the Billboard, or else it cannot exist.
 * 3. Global tag extraction and indexing.
 * 4. Initial subscription linking with DAG cycle check.
 */
class CreatePassageUseCase(
    private val passageRepository: PassageRepository,
    private val billboardRepository: BillboardRepository,
    private val tagRepository: TagRepository,
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        type: PassageType,
        content: String,
        tags: List<String> = emptyList(),
        parentId: Id? = null,
        isRecordable: Boolean = false,
        shouldAdvertise: Boolean = false,
        subscribeToId: Id? = null,
        currentTimeMillis: Long
    ): Result<Passage> {
        val newId = Id.generate()
        val allTags = Tag.extractTags(content, tags)

        // Check DAG cycle if an initial subscription is requested
        if (subscribeToId != null) {
            val createsCycle = DagValidator.wouldCreateCycle(
                sourceId = newId,
                targetId = subscribeToId,
                getOutgoing = { id ->
                    // Synchronously or through repository
                    emptyList() // newId is not yet in the graph, so cannot be reached by targetId
                }
            )
            if (createsCycle) {
                return Result.failure(IllegalStateException("Subscription would create a circular dependency in the graph"))
            }
        }

        val initialSubscribedTo = if (subscribeToId != null) listOf(subscribeToId) else emptyList()

        val passage: Passage = when (type) {
            PassageType.PASSAGE -> StandardPassage(
                id = newId,
                content = content,
                tags = allTags,
                isRecordable = isRecordable,
                createdAt = currentTimeMillis,
                subscribedToIds = initialSubscribedTo
            )
            PassageType.AUTHOR -> AuthorPassage(
                id = newId,
                content = content,
                tags = allTags,
                createdAt = currentTimeMillis,
                subscribedToIds = initialSubscribedTo
            )
            PassageType.QUOTE -> QuotePassage(
                id = newId,
                content = content,
                tags = allTags,
                isRecordable = isRecordable,
                createdAt = currentTimeMillis,
                subscribedToIds = initialSubscribedTo
            )
            PassageType.COMMENT -> {
                val target = subscribeToId ?: return Result.failure(
                    IllegalArgumentException("A Comment passage must specify a target passage to subscribe to")
                )
                CommentPassage(
                    id = newId,
                    content = content,
                    targetPassageId = target,
                    tags = allTags,
                    createdAt = currentTimeMillis
                )
            }
            PassageType.RECORDABLE -> RecordablePassage(
                id = newId,
                content = content,
                tags = allTags,
                createdAt = currentTimeMillis,
                subscribedToIds = initialSubscribedTo
            )
            PassageType.SOURCE -> SourcePassage(
                id = newId,
                content = content,
                tags = allTags,
                createdAt = currentTimeMillis,
                subscribedToIds = initialSubscribedTo
            )
        }

        // 1. Save passage with server-private parentId
        val effectiveParentId = if (type == PassageType.AUTHOR) (parentId ?: newId) else parentId
        passageRepository.save(passage, parentId = effectiveParentId)

        // 2. Creation-time ownership: If owned, update the parent's ownedPassageIds list
        if (parentId != null) {
            val parent = passageRepository.get(parentId)
            if (parent != null) {
                val updatedParent = when (parent) {
                    is StandardPassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                    is AuthorPassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                    is QuotePassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                    is CommentPassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                    is RecordablePassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                    is SourcePassage -> parent.copy(ownedPassageIds = parent.ownedPassageIds + newId)
                }
                val parentOfParent = passageRepository.getParentId(parentId)
                passageRepository.save(updatedParent, parentId = parentOfParent)
            }
        }

        // 3. Subscription linking: link subscribeToId target's subscriberIds
        if (subscribeToId != null) {
            passageRepository.addSubscription(sourceId = newId, targetId = subscribeToId)

            // Notify target author if target has an owner
            val targetParentId = passageRepository.getParentId(subscribeToId)
            if (targetParentId != null && targetParentId != parentId) {
                notificationRepository.addNotification(
                    Notification(
                        targetPassageId = subscribeToId,
                        subscriberPassageId = newId,
                        subscriberType = type,
                        summary = "New $type connected to your passage: \"${content.take(40)}...\"",
                        createdAt = currentTimeMillis
                    )
                )
            }
        }

        // 4. Billboard advertisement:
        // RULE: "And if a parent never owns a passage, it is automatically advertised, or else it cannot be created."
        // An AUTHOR passage represents a human profile, which is self-owned and defaults to NOT advertised on the billboard.
        val isAuthor = (type == PassageType.AUTHOR)
        val isOrphan = !isAuthor && (parentId == null)
        if (isOrphan) {
            // Mandatory & permanent auto-billboard advertisement for orphan passages
            billboardRepository.advertise(passageId = newId, isOrphan = true)
        } else if (shouldAdvertise) {
            // Voluntary advertisement by owner (or author if opted in)
            billboardRepository.advertise(passageId = newId, isOrphan = false)
        }

        // 5. Global tag indexing
        if (allTags.isNotEmpty()) {
            tagRepository.recordTags(allTags)
        }

        return Result.success(passage)
    }
}
