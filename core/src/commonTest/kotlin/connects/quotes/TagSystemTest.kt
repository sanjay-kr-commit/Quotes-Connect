package connects.quotes

import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.model.Tag
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.CreatePassageUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagSystemTest {

    @Test
    fun testDefaultAestheticTagsSeededDuringCleanInitialization() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val topTags = repo.getTopTags(limit = 50)

        // Verify default aesthetic tags exist
        val tagNames = topTags.map { it.name }.toSet()
        val expectedAesthetic = listOf(
            "stoicism", "philosophy", "wisdom", "sovereign", "knowledge",
            "dag", "citation", "epistemology", "aphorism", "truth",
            "ethics", "logic", "reason", "virtue", "mind", "literature"
        )
        for (expected in expectedAesthetic) {
            assertTrue(tagNames.contains(expected), "Default aesthetic tag #$expected must be seeded")
        }
    }

    @Test
    fun testTagUsageCountIncrementsWhenPassagesArrive() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = CreatePassageUseCase(repo, repo, repo, repo)

        val initialStoicism = repo.suggestTags("stoicism").firstOrNull { it.name == "stoicism" }?.count ?: 1

        // Author creates a quote with #stoicism and new tag #ataraxia
        val passage = createUseCase(
            type = PassageType.QUOTE,
            content = "Freedom is the only worthy goal in life. #stoicism",
            tags = listOf("ataraxia"),
            parentId = null,
            currentTimeMillis = 5000L
        ).getOrThrow()

        // Verify #stoicism count incremented
        val updatedStoicism = repo.suggestTags("stoicism").first { it.name == "stoicism" }
        assertEquals(initialStoicism + 1, updatedStoicism.count, "#stoicism count must increment upon passage arrival")

        // Verify #ataraxia was recorded with count 1
        val ataraxiaTag = repo.suggestTags("ataraxia").firstOrNull { it.name == "ataraxia" }
        assertTrue(ataraxiaTag != null, "#ataraxia tag must be created")
        assertEquals(1, ataraxiaTag.count)

        // Create second passage using #ataraxia
        createUseCase(
            type = PassageType.PASSAGE,
            content = "Tranquility of mind and freedom from fear. #ataraxia",
            parentId = null,
            currentTimeMillis = 5010L
        ).getOrThrow()

        val updatedAtaraxia = repo.suggestTags("ataraxia").first { it.name == "ataraxia" }
        assertEquals(2, updatedAtaraxia.count, "#ataraxia count must be 2 after second usage")
    }

    @Test
    fun testSuggestTagsOrdersPrefixMatchesFirstFollowedBySubstringMatches() = runTest {
        val repo = InMemoryKnowledgeRepository()

        // Record custom tags:
        // "epistemology" (prefix for "epi")
        // "deepinsight" (contains "epi")
        repo.recordTags(listOf("epistemology", "epistemic", "deepinsight"))

        val suggestions = repo.suggestTags("epi", limit = 10)
        val names = suggestions.map { it.name }

        assertTrue(names.contains("epistemology"))
        assertTrue(names.contains("epistemic"))
        assertTrue(names.indexOf("epistemology") < names.indexOf("deepinsight"), "Prefix matches must precede substring matches")
    }

    @Test
    fun testTagNormalizationStripsHashtagAndInvalidCharacters() {
        assertEquals("stoicism", Tag.normalize("#stoicism"))
        assertEquals("philosophy", Tag.normalize("  ###philosophy  "))
        assertEquals("knowledge_graph", Tag.normalize("#knowledge_graph!@$"))
        assertEquals("epistemic-logic", Tag.normalize("Epistemic-Logic"))
    }
}
