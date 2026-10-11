package connects.quotes.usecase

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.BillboardRepository
import connects.quotes.repository.PassageRepository

/**
 * Use case to toggle advertisement of a passage on the Billboard.
 *
 * Rules:
 * - If the passage is an orphan (parentId == null), it is PERMANENTLY advertised and can NEVER be unadvertised.
 * - If the passage is owned, only the owner (author whose ID matches the server-private parentId) can toggle it.
 */
class ToggleAdvertisementUseCase(
    private val passageRepository: PassageRepository,
    private val billboardRepository: BillboardRepository
) {
    suspend operator fun invoke(
        passageId: Id,
        requesterAuthorId: Id?
    ): Result<Boolean> {
        val passage = passageRepository.get(passageId)
            ?: return Result.failure(IllegalArgumentException("Passage $passageId not found"))

        // Check if passage is an author identity node owned by requester
        val isAuthorSelf = passage.type == PassageType.AUTHOR && requesterAuthorId == passageId
        val isOrphan = if (isAuthorSelf) false else billboardRepository.isOrphan(passageId)
        val parentId = passageRepository.getParentId(passageId)

        if (!isAuthorSelf && (isOrphan || parentId == null)) {
            return Result.failure(
                IllegalStateException("Orphan passage $passageId is permanently advertised and cannot be removed from the Billboard.")
            )
        }

        // Verify caller is the owner
        if (!isAuthorSelf && (requesterAuthorId == null || parentId != requesterAuthorId)) {
            return Result.failure(
                IllegalStateException("Only the owner of this passage can modify its advertisement status.")
            )
        }

        val currentlyAdvertised = billboardRepository.isAdvertised(passageId)
        val success = if (currentlyAdvertised) {
            billboardRepository.unadvertise(passageId)
        } else {
            billboardRepository.advertise(passageId, isOrphan = false)
        }

        return Result.success(success)
    }
}
