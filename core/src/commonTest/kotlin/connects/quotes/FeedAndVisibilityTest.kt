package connects.quotes

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.CreatePassageUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedAndVisibilityTest {

    @Test
    fun testLazyFeedIncludesChildrenOfSubscribedAuthorAndSubscribedQuote() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        // 1. Create Viewer Author
        val viewer = createUseCase(
            type = PassageType.AUTHOR,
            content = "Viewer",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()

        // 2. Create Target Author (Bob)
        val bob = createUseCase(
            type = PassageType.AUTHOR,
            content = "Bob",
            parentId = null,
            currentTimeMillis = 1010L
        ).getOrThrow()

        // 3. Bob creates an owned quote
        val bobsQuote = createUseCase(
            type = PassageType.QUOTE,
            content = "The unexamined life is not worth living. -- Socrates",
            parentId = bob.id,
            currentTimeMillis = 1020L
        ).getOrThrow()

        // 4. Alice (independent) creates a standalone quote
        val alice = createUseCase(
            type = PassageType.AUTHOR,
            content = "Alice",
            parentId = null,
            currentTimeMillis = 1030L
        ).getOrThrow()

        val alicesQuote = createUseCase(
            type = PassageType.QUOTE,
            content = "I think, therefore I am. -- Descartes",
            parentId = alice.id,
            currentTimeMillis = 1040L
        ).getOrThrow()

        // 5. Charlie attaches a comment/citation to Alice's quote
        val charliesComment = createUseCase(
            type = PassageType.COMMENT,
            content = "Foundational epistemological axiom.",
            parentId = null,
            subscribeToId = alicesQuote.id,
            currentTimeMillis = 1050L
        ).getOrThrow()

        // Viewer subscribes directly to:
        // - Bob (author subscription)
        // - alicesQuote (quote subscription)
        repo.addAuthorSubscription(viewer.id, bob.id)
        repo.addAuthorSubscription(viewer.id, alicesQuote.id)

        // Evaluate Lazy Feed at Depth 1
        val feedDepth1 = repo.getFeed(authorId = viewer.id, depth = 1)
        val feedDepth1Ids = feedDepth1.map { it.id }.toSet()

        // Depth 1 should contain:
        // - Directly subscribed nodes: Bob and alicesQuote
        assertTrue(feedDepth1Ids.contains(bob.id), "Feed depth 1 must contain subscribed author Bob")
        assertTrue(feedDepth1Ids.contains(alicesQuote.id), "Feed depth 1 must contain subscribed quote alicesQuote")
        // - Direct creations by Bob (bobsQuote)
        assertTrue(feedDepth1Ids.contains(bobsQuote.id), "Feed depth 1 must contain creations by subscribed author Bob")
        // - Direct citations attaching to alicesQuote (charliesComment)
        assertTrue(feedDepth1Ids.contains(charliesComment.id), "Feed depth 1 must contain citations attaching to subscribed quote")

        // 6. Dave attaches a secondary citation to charliesComment
        val davesRemark = createUseCase(
            type = PassageType.COMMENT,
            content = "Agreed, radically skeptical.",
            parentId = null,
            subscribeToId = charliesComment.id,
            currentTimeMillis = 1060L
        ).getOrThrow()

        // At Depth 1, davesRemark (depth 2 from alicesQuote) should NOT yet appear
        assertFalse(feedDepth1Ids.contains(davesRemark.id), "Feed depth 1 must NOT contain depth 2 citations")

        // At Depth 2, davesRemark should be lazily discovered
        val feedDepth2 = repo.getFeed(authorId = viewer.id, depth = 2)
        val feedDepth2Ids = feedDepth2.map { it.id }.toSet()
        assertTrue(feedDepth2Ids.contains(davesRemark.id), "Feed depth 2 must lazily discover extended child citations")
    }

    @Test
    fun testPassageVisibilityAndBulkTypeAdvertisementRespectingOrphanImmutability() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        val author = createUseCase(
            type = PassageType.AUTHOR,
            content = "Stoic Sage",
            parentId = null,
            currentTimeMillis = 2000L
        ).getOrThrow()

        // Create 2 owned QUOTEs and 1 owned PASSAGE
        val quote1 = createUseCase(
            type = PassageType.QUOTE,
            content = "Waste no more time arguing about what a good man should be. Be one. -- Marcus Aurelius",
            parentId = author.id,
            currentTimeMillis = 2010L
        ).getOrThrow()

        val quote2 = createUseCase(
            type = PassageType.QUOTE,
            content = "You have power over your mind - not outside events. -- Marcus Aurelius",
            parentId = author.id,
            currentTimeMillis = 2020L
        ).getOrThrow()

        val passage1 = createUseCase(
            type = PassageType.PASSAGE,
            content = "Reflective note on Meditations.",
            parentId = author.id,
            currentTimeMillis = 2030L
        ).getOrThrow()

        // Initially, owned passages are not advertised
        val initialAdvertised = repo.getAdvertisedPassageIds(author.id)
        assertTrue(initialAdvertised.isEmpty())

        // Bulk advertise only QUOTEs
        val quotesAdvertisedCount = repo.setPassageTypeAdvertisement(author.id, PassageType.QUOTE, true)
        assertEquals(2, quotesAdvertisedCount)

        val afterQuotesAd = repo.getAdvertisedPassageIds(author.id)
        assertTrue(afterQuotesAd.contains(quote1.id))
        assertTrue(afterQuotesAd.contains(quote2.id))
        assertFalse(afterQuotesAd.contains(passage1.id), "PASSAGE should not have been advertised by QUOTE bulk action")

        // Bulk advertise ALL
        repo.setPassageTypeAdvertisement(author.id, null, true)
        val afterAllAd = repo.getAdvertisedPassageIds(author.id)
        assertTrue(afterAllAd.contains(quote1.id))
        assertTrue(afterAllAd.contains(quote2.id))
        assertTrue(afterAllAd.contains(passage1.id))

        // Bulk unadvertise QUOTEs
        repo.setPassageTypeAdvertisement(author.id, PassageType.QUOTE, false)
        val afterUnadQuotes = repo.getAdvertisedPassageIds(author.id)
        assertFalse(afterUnadQuotes.contains(quote1.id))
        assertFalse(afterUnadQuotes.contains(quote2.id))
        assertTrue(afterUnadQuotes.contains(passage1.id), "PASSAGE must remain advertised")

        // Test Orphan Immutability:
        // Create an orphan passage (parentId = null)
        val orphan = createUseCase(
            type = PassageType.PASSAGE,
            content = "Public domain axiom",
            parentId = null,
            currentTimeMillis = 2040L
        ).getOrThrow()

        assertTrue(repo.isAdvertised(orphan.id), "Orphans are permanently advertised")
        assertTrue(repo.isOrphan(orphan.id))

        // Even if an author tries to unadvertise passages, orphans cannot be deleted from billboard
        repo.setPassageTypeAdvertisement(author.id, null, false)
        assertTrue(repo.isAdvertised(orphan.id), "Orphan must remain advertised on Billboard")
    }

    @Test
    fun testNotificationsStrictlyScopedToChatsAndCitationsToOwnedPassages() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        val authorA = createUseCase(
            type = PassageType.AUTHOR,
            content = "Author A",
            parentId = null,
            currentTimeMillis = 3000L
        ).getOrThrow()

        val authorB = createUseCase(
            type = PassageType.AUTHOR,
            content = "Author B",
            parentId = null,
            currentTimeMillis = 3010L
        ).getOrThrow()

        // Author A creates an owned quote
        val aQuote = createUseCase(
            type = PassageType.QUOTE,
            content = "He who has a why to live can bear almost any how. -- Nietzsche",
            parentId = authorA.id,
            currentTimeMillis = 3020L
        ).getOrThrow()

        // Author B creates a comment citing Author A's quote
        val bComment = createUseCase(
            type = PassageType.COMMENT,
            content = "Deep insight into human endurance.",
            parentId = authorB.id,
            subscribeToId = aQuote.id,
            currentTimeMillis = 3030L
        ).getOrThrow()

        // Author A checks notifications: should contain citation alert for their owned quote
        val notificationsForA = repo.getNotifications(authorA.id)
        assertEquals(1, notificationsForA.size, "Author A must receive notification for citation to owned quote")
        assertEquals(aQuote.id, notificationsForA[0].targetPassageId)
        assertEquals(bComment.id, notificationsForA[0].subscriberPassageId)

        // Author B checks notifications: should have zero notifications
        val notificationsForB = repo.getNotifications(authorB.id)
        assertTrue(notificationsForB.isEmpty(), "Author B should have no incoming citations")
    }
}
