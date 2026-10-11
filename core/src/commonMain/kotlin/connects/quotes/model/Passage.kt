package connects.quotes.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Universal root entity in Quotes Connect.
 * Every piece of information—quotes, comments, authors, ideas, sources, and records—
 * is a polymorphic Passage identified strictly by an Id (UUID).
 *
 * Direct object references are forbidden; all relationships are formed via ID subscriptions.
 *
 * NOTE: As per architectural specification, the server-private `parentId` (creator lineage)
 * is omitted from this public model and serialization to ensure associative anonymity.
 */
@Serializable
sealed interface Passage {
    val id: Id
    val type: PassageType
    val content: String
    val tags: List<String>
    val isRecordable: Boolean
    val createdAt: Long
    val updatedAt: Long
    val subscribedToIds: List<Id>
    val subscriberIds: List<Id>
    val ownedPassageIds: List<Id>
}

/**
 * Standard independent knowledge passage.
 */
@Serializable
@SerialName("PASSAGE")
data class StandardPassage(
    override val id: Id,
    override val content: String,
    override val tags: List<String> = emptyList(),
    override val isRecordable: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = emptyList(),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.PASSAGE
}

/**
 * An Author passage representing a knowledge generator / identity.
 * People are also passages in Quotes Connect.
 */
@Serializable
@SerialName("AUTHOR")
data class AuthorPassage(
    override val id: Id,
    override val content: String, // Identity pseudonym / public moniker
    val moniker: String = content,
    override val tags: List<String> = emptyList(),
    override val isRecordable: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = emptyList(),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.AUTHOR
}

/**
 * A Quote passage preserving a specific quotation.
 */
@Serializable
@SerialName("QUOTE")
data class QuotePassage(
    override val id: Id,
    override val content: String,
    val authorName: String? = null,
    val sourceRef: String? = null,
    val pageNumber: String? = null,
    override val tags: List<String> = emptyList(),
    override val isRecordable: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = emptyList(),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.QUOTE
}

/**
 * A Comment passage that subscribes to and comments upon another passage.
 */
@Serializable
@SerialName("COMMENT")
data class CommentPassage(
    override val id: Id,
    override val content: String,
    val targetPassageId: Id,
    override val tags: List<String> = emptyList(),
    override val isRecordable: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = listOf(targetPassageId),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.COMMENT
}

/**
 * An immutable ledger / archival passage.
 * Once created, a RecordablePassage cannot be deleted and all subscriptions
 * to it become permanent historical citations.
 */
@Serializable
@SerialName("RECORDABLE")
data class RecordablePassage(
    override val id: Id,
    override val content: String,
    override val tags: List<String> = emptyList(),
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = emptyList(),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.RECORDABLE
    override val isRecordable: Boolean get() = true
}

/**
 * A Source passage representing books, papers, articles, websites, or interviews.
 */
@Serializable
@SerialName("SOURCE")
data class SourcePassage(
    override val id: Id,
    override val content: String, // Source title or description
    val sourceTitle: String = content,
    val sourceKind: String? = null, // e.g., Book, Article, Website, Research Paper
    val sourceUrl: String? = null,
    override val tags: List<String> = emptyList(),
    override val isRecordable: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long = createdAt,
    override val subscribedToIds: List<Id> = emptyList(),
    override val subscriberIds: List<Id> = emptyList(),
    override val ownedPassageIds: List<Id> = emptyList(),
) : Passage {
    override val type: PassageType get() = PassageType.SOURCE
}
