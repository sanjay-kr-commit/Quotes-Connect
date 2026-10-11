package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * Representation of a topic / classification tag in Quotes Connect.
 */
@Serializable
data class Tag(
    val name: String,
    val count: Int = 1
) {
    companion object {
        private val cleanRegex = Regex("[^a-zA-Z0-9_-]")

        /**
         * Normalizes a tag string by stripping leading '#', whitespace, and converting to lowercase.
         */
        fun normalize(raw: String): String {
            return raw.trim()
                .removePrefix("#")
                .lowercase()
                .replace(cleanRegex, "")
        }

        /**
         * Extracts hashtags or comma-separated tags from raw text.
         */
        fun extractTags(text: String, additionalTags: List<String> = emptyList()): List<String> {
            val hashtagRegex = Regex("#([a-zA-Z0-9_-]+)")
            val foundInText = hashtagRegex.findAll(text).map { normalize(it.groupValues[1]) }.toList()
            val extra = additionalTags.map { normalize(it) }.filter { it.isNotBlank() }
            return (foundInText + extra).distinct()
        }
    }
}
