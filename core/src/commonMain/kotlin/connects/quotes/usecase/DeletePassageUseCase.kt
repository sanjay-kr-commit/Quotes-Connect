package connects.quotes.usecase

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.PassageRepository

/**
 * Use case to delete a passage.
 *
 * Rules:
 * - If the passage is recordable (isRecordable == true or type == RECORDABLE), deletion is strictly PROHIBITED.
 * - If any subscriber to this passage is recordable, deletion is strictly PROHIBITED to maintain historical ledger integrity.
 * - Only the owner (parentId == requesterAuthorId) can request deletion.
 */
class DeletePassageUseCase(
    private val passageRepository: PassageRepository
) {
    suspend operator fun invoke(
        passageId: Id,
        requesterAuthorId: Id?
    ): Result<Boolean> {
        val passage = passageRepository.get(passageId)
            ?: return Result.failure(IllegalArgumentException("Passage $passageId not found"))

        // Recordable immutability check
        if (passage.isRecordable || passage.type == PassageType.RECORDABLE) {
            return Result.failure(
                IllegalStateException("Passage $passageId is an immutable Recordable passage and cannot be deleted.")
            )
        }

        // Check if any subscriber is recordable
        val subscribers = passageRepository.getSubscribers(passageId)
        for (subId in subscribers) {
            if (passageRepository.isRecordable(subId)) {
                return Result.failure(
                    IllegalStateException("Cannot delete passage $passageId because it is cited by recordable passage $subId.")
                )
            }
        }

        // Verify ownership
        val parentId = passageRepository.getParentId(passageId)
        if (parentId == null) {
            return Result.failure(
                IllegalStateException("Orphan passages are public domain and cannot be deleted by users.")
            )
        }

        if (requesterAuthorId == null || parentId != requesterAuthorId) {
            return Result.failure(
                IllegalStateException("Only the owner of this passage can delete it.")
            )
        }

        val deleted = passageRepository.delete(passageId)
        return Result.success(deleted)
    }
}
