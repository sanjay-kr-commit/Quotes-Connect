package connects.quotes

import connects.quotes.model.AuthorDiscardAction
import connects.quotes.model.AuthorPassage
import connects.quotes.model.AuthorSettings
import connects.quotes.model.ChatType
import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.model.Tag
import connects.quotes.repository.InMemoryKnowledgeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatUniquenessAndAliasTest {

    @Test
    fun testSingleChatSessionInstancePerType() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val authorA = Id("author-alice")
        val authorB = Id("author-bob")

        // 1. Create first session: UNENCRYPTED from A to B
        val session1 = repo.createChatSession(
            initiatorId = authorA,
            recipientId = authorB,
            type = ChatType.UNENCRYPTED
        )
        assertNotNull(session1)

        // 2. Reverse direction request with same type from B to A should return existing session1
        val session2 = repo.createChatSession(
            initiatorId = authorB,
            recipientId = authorA,
            type = ChatType.UNENCRYPTED
        )
        assertEquals(session1.id, session2.id, "Second request must return existing chat session instead of duplicating")

        val authorASessions = repo.getChatSessionsForAuthor(authorA)
        val unencryptedSessions = authorASessions.filter { it.type == ChatType.UNENCRYPTED }
        assertEquals(1, unencryptedSessions.size, "Must have exactly 1 UNENCRYPTED session between A and B")

        // 3. Different type (ENCRYPTED) between A and B creates a separate distinct session
        val sessionEncrypted = repo.createChatSession(
            initiatorId = authorA,
            recipientId = authorB,
            type = ChatType.ENCRYPTED,
            initiatorPublicKey = "PUB_KEY_A"
        )
        assertEquals(ChatType.ENCRYPTED, sessionEncrypted.type)

        val allSessions = repo.getChatSessionsForAuthor(authorA)
        assertEquals(2, allSessions.size, "Must have exactly 1 UNENCRYPTED and 1 ENCRYPTED session")
    }

    @Test
    fun testAliasResolutionRespectsAuthorPrivacySettings() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val createUseCase = connects.quotes.usecase.CreatePassageUseCase(repo, repo, repo, repo)

        val authorPassage = createUseCase(
            type = PassageType.AUTHOR,
            content = "Socrates",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()
        val authorId = authorPassage.id

        val settingsEnabled = AuthorSettings(
            authorId = authorId,
            lastActiveAt = 1000L,
            username = "socrates",
            aliasSearchEnabled = true
        )
        repo.saveAuthorSettings(settingsEnabled)

        // 1. With aliasSearchEnabled = true, alias query resolves to UUID
        val resolvedWithAt = repo.resolveAuthorByUsernameOrId("@socrates")
        assertEquals(authorId, resolvedWithAt)

        val resolvedWithoutAt = repo.resolveAuthorByUsernameOrId("socrates")
        assertEquals(authorId, resolvedWithoutAt)

        // 2. With aliasSearchEnabled = false, alias lookup returns null
        val settingsDisabled = settingsEnabled.copy(aliasSearchEnabled = false)
        repo.saveAuthorSettings(settingsDisabled)

        val resolvedDisabled = repo.resolveAuthorByUsernameOrId("@socrates")
        assertNull(resolvedDisabled, "Alias search must return null when aliasSearchEnabled is false")

        // 3. Sovereign UUID direct lookup is ALWAYS valid regardless of alias setting
        val resolvedDirectUuid = repo.resolveAuthorByUsernameOrId(authorId.value)
        assertEquals(authorId, resolvedDirectUuid, "Sovereign direct UUID lookup must always resolve")
    }

    @Test
    fun testSpaceBasedTagChippingExtraction() {
        val input = "stoicism philosophy logic   reason,epistemology\nwisdom"
        val parts = input.split(" ", ",", "\n")
        val normalizedTags = parts
            .map { Tag.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()

        assertEquals(
            listOf("stoicism", "philosophy", "logic", "reason", "epistemology", "wisdom"),
            normalizedTags
        )
    }
}
