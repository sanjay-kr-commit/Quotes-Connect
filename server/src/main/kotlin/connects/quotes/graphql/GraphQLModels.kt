package connects.quotes.graphql

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, JsonElement>? = null
)

@Serializable
data class GraphQLResponse(
    val data: JsonElement? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(
    val message: String
)
