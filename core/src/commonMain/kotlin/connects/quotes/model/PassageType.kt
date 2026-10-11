package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * Discriminator of polymorphic Passage types in Quotes Connect.
 */
@Serializable
enum class PassageType {
    PASSAGE,
    AUTHOR,
    QUOTE,
    COMMENT,
    RECORDABLE,
    SOURCE
}
