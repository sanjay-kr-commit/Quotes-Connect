package connects.quotes

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.CreatePassageUseCase
import connects.quotes.usecase.DeletePassageUseCase
import connects.quotes.usecase.ToggleAdvertisementUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassageRulesTest {

    @Test
    fun testOrphanPassageIsAutomaticallyAdvertisedOnBillboard() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        // Create an unparented orphan passage
        val result = createUseCase(
            type = PassageType.PASSAGE,
            content = "Anonymous philosophical truth",
            tags = listOf("truth", "anonymous"),
            parentId = null, // Orphan!
            currentTimeMillis = 1000L
        )

        assertTrue(result.isSuccess)
        val passage = result.getOrThrow()

        // Verify it is automatically on the billboard
        assertTrue(repo.isAdvertised(passage.id))
        assertTrue(repo.isOrphan(passage.id))

        val billboardItems = repo.getAdvertised()
        assertEquals(1, billboardItems.size)
        assertEquals(passage.id, billboardItems[0].passage.id)
        assertTrue(billboardItems[0].isOrphan)
    }

    @Test
    fun testOrphanPassageCannotBeUnadvertised() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)
        val toggleAdUseCase = ToggleAdvertisementUseCase(repo, repo)

        val result = createUseCase(
            type = PassageType.QUOTE,
            content = "Orphan quote #stoic",
            parentId = null, // Orphan
            currentTimeMillis = 1000L
        )
        val passage = result.getOrThrow()

        // Attempt to un-advertise
        val toggleResult = toggleAdUseCase(passage.id, requesterAuthorId = null)
        assertTrue(toggleResult.isFailure)
        assertTrue(repo.isAdvertised(passage.id)) // Still advertised
    }

    @Test
    fun testOwnerCanToggleAdvertisement() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)
        val toggleAdUseCase = ToggleAdvertisementUseCase(repo, repo)

        val authorId = Id("author-1")

        val result = createUseCase(
            type = PassageType.PASSAGE,
            content = "Author owned thought",
            parentId = authorId,
            shouldAdvertise = false,
            currentTimeMillis = 1000L
        )
        val passage = result.getOrThrow()

        // Initially not advertised
        assertFalse(repo.isAdvertised(passage.id))

        // Owner toggles it ON
        val onResult = toggleAdUseCase(passage.id, requesterAuthorId = authorId)
        assertTrue(onResult.isSuccess)
        assertTrue(repo.isAdvertised(passage.id))

        // Non-owner cannot toggle it
        val unauthorizedResult = toggleAdUseCase(passage.id, requesterAuthorId = Id("intruder"))
        assertTrue(unauthorizedResult.isFailure)

        // Owner toggles it OFF
        val offResult = toggleAdUseCase(passage.id, requesterAuthorId = authorId)
        assertTrue(offResult.isSuccess)
        assertFalse(repo.isAdvertised(passage.id))
    }

    @Test
    fun testRecordablePassageCannotBeDeleted() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)
        val deleteUseCase = DeletePassageUseCase(repo)

        val authorId = Id("author-1")

        val result = createUseCase(
            type = PassageType.RECORDABLE,
            content = "Permanent historical axiom",
            parentId = authorId,
            currentTimeMillis = 1000L
        )
        val passage = result.getOrThrow()

        // Deletion must fail
        val deleteResult = deleteUseCase(passage.id, requesterAuthorId = authorId)
        assertTrue(deleteResult.isFailure)
        assertTrue(repo.get(passage.id) != null) // Still exists
    }

    @Test
    fun testCreationTimeOwnershipIsEstablished() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        val author = createUseCase(
            type = PassageType.AUTHOR,
            content = "Marcus",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()

        val quote = createUseCase(
            type = PassageType.QUOTE,
            content = "You have power over your mind - not outside events.",
            parentId = author.id,
            currentTimeMillis = 1001L
        ).getOrThrow()

        val updatedAuthor = repo.get(author.id)
        assertTrue(updatedAuthor != null)
        assertTrue(updatedAuthor.ownedPassageIds.contains(quote.id))
    }
}
