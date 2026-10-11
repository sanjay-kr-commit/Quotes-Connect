package connects.quotes.graphql

/**
 * Lightweight, zero-dependency GraphQL operation and root-field parser.
 * Eliminates substring matching vulnerabilities (BUG-013) by parsing
 * the top-level selection set of queries and mutations.
 */
data class ParsedRootField(
    val name: String,
    val alias: String? = null,
    val argsString: String? = null,
    val selectionSet: String? = null
)

data class ParsedOperation(
    val type: OperationType,
    val name: String? = null,
    val rootFields: List<ParsedRootField>
)

enum class OperationType {
    QUERY, MUTATION, SUBSCRIPTION
}

object GraphQLDocumentParser {

    fun parse(document: String): ParsedOperation {
        val sanitized = stripComments(document).trim()
        if (sanitized.isEmpty()) {
            return ParsedOperation(OperationType.QUERY, null, emptyList())
        }

        val type: OperationType = when {
            sanitized.startsWith("mutation", ignoreCase = true) -> OperationType.MUTATION
            sanitized.startsWith("subscription", ignoreCase = true) -> OperationType.SUBSCRIPTION
            else -> OperationType.QUERY
        }

        // Find the top-level opening brace
        val openBraceIdx = findTopLevelOpenBrace(sanitized)
        if (openBraceIdx == -1) {
            return ParsedOperation(type, null, emptyList())
        }

        // Extract operation name if provided: e.g. "query MyQuery($var: String) {"
        val header = sanitized.substring(0, openBraceIdx).trim()
        val opName = extractOperationName(header)

        // Parse root fields inside the top-level selection set
        val rootFields = parseRootFields(sanitized, openBraceIdx)

        return ParsedOperation(type, opName, rootFields)
    }

    private fun extractOperationName(header: String): String? {
        if (header.isEmpty()) return null
        val parts = header.split(Regex("""[\s(]""")).filter { it.isNotBlank() }
        return if (parts.size >= 2) parts[1] else null
    }

    private fun stripComments(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        var inString = false
        var inBlockString = false

        while (i < source.length) {
            val c = source[i]
            if (!inString && !inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = true
                sb.append("\"\"\"")
                i += 3
                continue
            }
            if (inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = false
                sb.append("\"\"\"")
                i += 3
                continue
            }
            if (!inBlockString && c == '"' && (i == 0 || source[i - 1] != '\\')) {
                inString = !inString
                sb.append(c)
                i++
                continue
            }
            if (!inString && !inBlockString && c == '#') {
                // Skip until end of line
                while (i < source.length && source[i] != '\n' && source[i] != '\r') {
                    sb.append(' ')
                    i++
                }
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findTopLevelOpenBrace(source: String): Int {
        var inString = false
        var inBlockString = false
        var i = 0
        while (i < source.length) {
            if (!inString && !inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = true
                i += 3
                continue
            }
            if (inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = false
                i += 3
                continue
            }
            if (!inBlockString && source[i] == '"' && (i == 0 || source[i - 1] != '\\')) {
                inString = !inString
                i++
                continue
            }
            if (!inString && !inBlockString && source[i] == '{') {
                return i
            }
            i++
        }
        return -1
    }

    private fun parseRootFields(source: String, openBraceIdx: Int): List<ParsedRootField> {
        val fields = mutableListOf<ParsedRootField>()
        var i = openBraceIdx + 1

        while (i < source.length) {
            i = skipWhitespaceAndCommas(source, i)
            if (i >= source.length || source[i] == '}') {
                break
            }

            // Skip fragments: ... on Type or ...FragmentName
            if (source.startsWith("...", i)) {
                i += 3
                // skip until whitespace/brace
                while (i < source.length && source[i] != '{' && source[i] != '}' && source[i] != '\n') {
                    i++
                }
                if (i < source.length && source[i] == '{') {
                    i = skipBalanced(source, i, '{', '}')
                }
                continue
            }

            // Read identifier
            val firstToken = readIdentifier(source, i)
            if (firstToken.isEmpty()) {
                i++
                continue
            }
            i += firstToken.length
            i = skipWhitespaceAndCommas(source, i)

            var alias: String? = null
            var fieldName = firstToken

            if (i < source.length && source[i] == ':') {
                alias = firstToken
                i++ // consume ':'
                i = skipWhitespaceAndCommas(source, i)
                val secondToken = readIdentifier(source, i)
                if (secondToken.isNotEmpty()) {
                    fieldName = secondToken
                    i += secondToken.length
                }
            }

            i = skipWhitespaceAndCommas(source, i)

            // Optional arguments ( ... )
            var argsString: String? = null
            if (i < source.length && source[i] == '(') {
                val startArgs = i + 1
                val endArgs = findMatchingParen(source, i)
                if (endArgs != -1) {
                    argsString = source.substring(startArgs, endArgs)
                    i = endArgs + 1
                }
            }

            i = skipWhitespaceAndCommas(source, i)

            // Optional directives @...
            while (i < source.length && source[i] == '@') {
                i++
                val dirName = readIdentifier(source, i)
                i += dirName.length
                i = skipWhitespaceAndCommas(source, i)
                if (i < source.length && source[i] == '(') {
                    val endDirArgs = findMatchingParen(source, i)
                    if (endDirArgs != -1) {
                        i = endDirArgs + 1
                    }
                }
                i = skipWhitespaceAndCommas(source, i)
            }

            // Optional selection set { ... }
            var selectionSet: String? = null
            if (i < source.length && source[i] == '{') {
                val endSelection = findMatchingBrace(source, i)
                if (endSelection != -1) {
                    selectionSet = source.substring(i, endSelection + 1)
                    i = endSelection + 1
                }
            }

            fields.add(
                ParsedRootField(
                    name = fieldName,
                    alias = alias,
                    argsString = argsString?.trim(),
                    selectionSet = selectionSet?.trim()
                )
            )
        }

        return fields
    }

    private fun skipWhitespaceAndCommas(source: String, start: Int): Int {
        var i = start
        while (i < source.length && (source[i].isWhitespace() || source[i] == ',')) {
            i++
        }
        return i
    }

    private fun readIdentifier(source: String, start: Int): String {
        var i = start
        while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '$')) {
            i++
        }
        return source.substring(start, i)
    }

    private fun findMatchingParen(source: String, openIdx: Int): Int {
        return findMatching(source, openIdx, '(', ')')
    }

    private fun findMatchingBrace(source: String, openIdx: Int): Int {
        return findMatching(source, openIdx, '{', '}')
    }

    private fun skipBalanced(source: String, openIdx: Int, openChar: Char, closeChar: Char): Int {
        val end = findMatching(source, openIdx, openChar, closeChar)
        return if (end != -1) end + 1 else source.length
    }

    private fun findMatching(source: String, openIdx: Int, openChar: Char, closeChar: Char): Int {
        var depth = 0
        var inString = false
        var inBlockString = false
        var i = openIdx

        while (i < source.length) {
            if (!inString && !inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = true
                i += 3
                continue
            }
            if (inBlockString && i + 2 < source.length && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                inBlockString = false
                i += 3
                continue
            }
            if (!inBlockString && source[i] == '"' && (i == 0 || source[i - 1] != '\\')) {
                inString = !inString
                i++
                continue
            }
            if (!inString && !inBlockString) {
                if (source[i] == openChar) {
                    depth++
                } else if (source[i] == closeChar) {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }
}
