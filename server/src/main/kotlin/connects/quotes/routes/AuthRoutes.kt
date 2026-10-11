package connects.quotes.routes

import connects.quotes.database.JdbcPassageRepository
import connects.quotes.model.Id
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LoginRequest(
    val authorId: String,
    val passphrase: String,
    val deviceId: String? = null
)

@Serializable
data class LogoutRequest(
    val token: String? = null
)

fun Route.authRoutes(repository: JdbcPassageRepository) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    route("/api/auth") {
        post("/login") {
            try {
                val req = call.receive<LoginRequest>()
                if (req.authorId.isBlank() || req.passphrase.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing authorId or passphrase"))
                    return@post
                }
                val resolvedId = repository.resolveAuthorByUsernameOrId(req.authorId.trim()) ?: Id(req.authorId.trim())
                val isValid = repository.verifyCredentials(resolvedId, req.passphrase)
                if (!isValid) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid author identity or passphrase"))
                    return@post
                }
                val session = repository.createSession(resolvedId, req.deviceId?.let { Id(it) })
                call.respond(session)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Login failed: ${e.message}"))
            }
        }

        post("/logout") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val bearerToken = authHeader?.removePrefix("Bearer ")?.trim()
            val token = if (!bearerToken.isNullOrBlank()) {
                bearerToken
            } else {
                try {
                    val req = call.receiveNullable<LogoutRequest>()
                    req?.token
                } catch (_: Exception) {
                    null
                }
            }
            if (token != null) {
                repository.revokeSession(token)
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        get("/session") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val token = authHeader?.removePrefix("Bearer ")?.trim()
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing Authorization Bearer token"))
                return@get
            }
            val principal = repository.validateSession(token)
            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session token is invalid or expired"))
                return@get
            }
            call.respond(principal)
        }

        post("/refresh") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val token = authHeader?.removePrefix("Bearer ")?.trim()
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing Authorization Bearer token"))
                return@post
            }
            val principal = repository.validateSession(token)
            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session token is invalid or expired"))
                return@post
            }
            // Revoke old token and create fresh session
            repository.revokeSession(token)
            val newSession = repository.createSession(principal.authorId, principal.deviceId)
            call.respond(newSession)
        }
    }
}
