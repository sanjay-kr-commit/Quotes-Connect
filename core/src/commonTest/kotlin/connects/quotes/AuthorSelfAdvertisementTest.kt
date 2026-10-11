package connects.quotes

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.CreatePassageUseCase
import connects.quotes.usecase.ToggleAdvertisementUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorSelfAdvertisementTest {

    @Test
    fun testAuthorPassageDefaultsToOffOnBillboardAndIsNotOrphan() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        // Create an author passage with default shouldAdvertise (false)
        val result = createUseCase(
            type = PassageType.AUTHOR,
            content = "Hypatia",
            parentId = null,
            currentTimeMillis = 1000L
        )

        assertTrue(result.isSuccess)
        val author = result.getOrThrow()

        // 1. Author should NOT be an orphan
        assertFalse(repo.isOrphan(author.id), "Author passage must not be considered an orphan")

        // 2. Author should NOT be on the billboard by default
        assertFalse(repo.isAdvertised(author.id), "Author passage advertisement must be off by default")
        val billboardItems = repo.getAdvertised()
        assertTrue(billboardItems.isEmpty(), "Billboard should have zero items by default for an author")
    }

    @Test
    fun testAuthorCanToggleBillboardAdvertisementOnAndOff() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)
        val toggleAdUseCase = ToggleAdvertisementUseCase(repo, repo)

        val result = createUseCase(
            type = PassageType.AUTHOR,
            content = "Marcus Aurelius",
            parentId = null,
            currentTimeMillis = 1000L
        )
        val author = result.getOrThrow()

        assertFalse(repo.isAdvertised(author.id))

        // Author toggles ON
        val toggleOn = toggleAdUseCase(author.id, requesterAuthorId = author.id)
        assertTrue(toggleOn.isSuccess)
        assertTrue(repo.isAdvertised(author.id), "Author should now be advertised on Billboard")

        val billboardAfterOn = repo.getAdvertised()
        assertEquals(1, billboardAfterOn.size)
        assertEquals(author.id, billboardAfterOn[0].passage.id)
        assertFalse(billboardAfterOn[0].isOrphan, "Author passage must not be marked as an orphan on Billboard")

        // Author toggles OFF
        val toggleOff = toggleAdUseCase(author.id, requesterAuthorId = author.id)
        assertTrue(toggleOff.isSuccess)
        assertFalse(repo.isAdvertised(author.id), "Author should now be unadvertised from Billboard")
        assertEquals(0, repo.getAdvertised().size)
    }

    @Test
    fun testUnauthorizedUserCannotToggleAuthorAdvertisement() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)
        val toggleAdUseCase = ToggleAdvertisementUseCase(repo, repo)

        val author = createUseCase(
            type = PassageType.AUTHOR,
            content = "Socrates",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()

        val strangerId = Id("stranger-99")

        // Stranger attempts to advertise Socrates
        val failureResult = toggleAdUseCase(author.id, requesterAuthorId = strangerId)
        assertTrue(failureResult.isFailure)
        assertFalse(repo.isAdvertised(author.id))

        // Anonymous attempt
        val anonFailure = toggleAdUseCase(author.id, requesterAuthorId = null)
        assertTrue(anonFailure.isFailure)
        assertFalse(repo.isAdvertised(author.id))
    }

    @Test
    fun testGetCreatedByAuthorReturnsAuthorPassageAndAuthoredPassages() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        // 1. Create Author passage
        val author = createUseCase(
            type = PassageType.AUTHOR,
            content = "Epictetus",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()

        // 2. Create passages authored by Epictetus
        val passage1 = createUseCase(
            type = PassageType.QUOTE,
            content = "We have two ears and one mouth so that we can listen twice as much as we speak.",
            parentId = author.id,
            currentTimeMillis = 2000L
        ).getOrThrow()

        val passage2 = createUseCase(
            type = PassageType.PASSAGE,
            content = "Freedom is the only worthy goal in life.",
            parentId = author.id,
            currentTimeMillis = 3000L
        ).getOrThrow()

        // 3. Create another passage authored by someone else
        val stranger = createUseCase(
            type = PassageType.AUTHOR,
            content = "Stranger",
            parentId = null,
            currentTimeMillis = 4000L
        ).getOrThrow()

        val strangerPassage = createUseCase(
            type = PassageType.PASSAGE,
            content = "Unrelated knowledge",
            parentId = stranger.id,
            currentTimeMillis = 5000L
        ).getOrThrow()

        // 4. Query created passages for Epictetus
        val createdByEpictetus = repo.getCreatedByAuthor(author.id)
        val createdIds = createdByEpictetus.map { it.id }.toSet()

        assertEquals(3, createdByEpictetus.size)
        assertTrue(createdIds.contains(author.id), "Should include the author's own passage")
        assertTrue(createdIds.contains(passage1.id), "Should include passage1")
        assertTrue(createdIds.contains(passage2.id), "Should include passage2")
        assertFalse(createdIds.contains(strangerPassage.id), "Should not include passages from other authors")
        assertFalse(createdIds.contains(stranger.id), "Should not include other author passages")
    }
}
