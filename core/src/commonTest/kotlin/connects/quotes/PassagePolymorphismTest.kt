package connects.quotes

import connects.quotes.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassagePolymorphismTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testPolymorphicSerialization() {
        val passages: List<Passage> = listOf(
            StandardPassage(
                id = Id("p-1"),
                content = "Knowledge is connected.",
                tags = listOf("philosophy"),
                createdAt = 1000L
            ),
            AuthorPassage(
                id = Id("a-1"),
                content = "Socrates",
                moniker = "Socrates",
                tags = listOf("thinker"),
                createdAt = 1000L
            ),
            QuotePassage(
                id = Id("q-1"),
                content = "The only true wisdom is in knowing you know nothing.",
                authorName = "Socrates",
                tags = listOf("wisdom", "stoic"),
                createdAt = 1000L
            ),
            CommentPassage(
                id = Id("c-1"),
                content = "This reflects profound humility.",
                targetPassageId = Id("q-1"),
                createdAt = 1000L
            ),
            RecordablePassage(
                id = Id("r-1"),
                content = "Foundational Axiom 1",
                createdAt = 1000L
            ),
            SourcePassage(
                id = Id("s-1"),
                content = "Apology",
                sourceTitle = "Apology",
                sourceKind = "Dialogue",
                createdAt = 1000L
            )
        )

        for (original in passages) {
            val serialized = json.encodeToString<Passage>(original)
            assertTrue(serialized.contains("\"type\":"))

            val deserialized = json.decodeFromString<Passage>(serialized)
            assertEquals(original.id, deserialized.id)
            assertEquals(original.type, deserialized.type)
            assertEquals(original.content, deserialized.content)
            assertEquals(original.isRecordable, deserialized.isRecordable)
        }
    }
}
