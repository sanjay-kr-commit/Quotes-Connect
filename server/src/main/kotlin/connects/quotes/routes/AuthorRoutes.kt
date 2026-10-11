package connects.quotes.routes

import connects.quotes.database.JdbcPassageRepository
import connects.quotes.model.AuthorDiscardAction
import connects.quotes.model.AuthorSettings
import connects.quotes.model.Id
import connects.quotes.model.PassageType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun ApplicationCall.extractPrincipal(repository: JdbcPassageRepository): connects.quotes.security.AuthPrincipal? {
    val authHeader = request.header(HttpHeaders.Authorization)
    val token = authHeader?.removePrefix("Bearer ")?.trim()
    return if (!token.isNullOrBlank()) repository.validateSession(token) else null
}

/**
 * Registers author activity pinging, settings management, inactivity sweeps, and sovereign discard actions.
 */
fun Route.authorRoutes(repository: JdbcPassageRepository) {
    // Log author active / heartbeat ping
    post("/api/author/ping") {
        val principal = call.extractPrincipal(repository)
        val authorId = call.receiveParameters()["authorId"]
            ?: call.request.queryParameters["authorId"]
            ?: principal?.authorId?.value

        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@post
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot ping for another author"))
            return@post
        }
        repository.logAuthorActivity(Id(authorId), "PING")
        call.respond(HttpStatusCode.OK, """{"status":"ok"}""")
    }

    // Get author settings & inactivity details
    get("/api/author/settings") {
        val authorId = call.request.queryParameters["authorId"]
        if (!authorId.isNullOrBlank()) {
            val settings = repository.getAuthorSettings(Id(authorId))
            if (settings != null) {
                call.respond(settings)
            } else {
                call.respond(HttpStatusCode.NotFound, "Author settings not found")
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
        }
    }

    // Update author settings & inactivity configuration
    post("/api/author/settings") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val days = params["inactivityPeriodDays"]?.toIntOrNull() ?: 30
        val actionStr = params["discardAction"] ?: "ORPHAN"
        val action = try { AuthorDiscardAction.valueOf(actionStr.uppercase()) } catch (_: Exception) { AuthorDiscardAction.ORPHAN }

        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@post
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot update settings of another author"))
            return@post
        }
        val now = System.currentTimeMillis()
        val current = repository.getAuthorSettings(Id(authorId))
        val rawUsername = params["username"]?.trim()?.removePrefix("@")
        val newUsername = if (!rawUsername.isNullOrBlank()) {
            if (!repository.checkUsernameAvailable(rawUsername, Id(authorId))) {
                call.respond(HttpStatusCode.Conflict, """{"error":"Username @$rawUsername is already taken"}""")
                return@post
            }
            rawUsername
        } else current?.username
        val newBio = params["bio"] ?: current?.bio
        val aliasSearchEnabled = params["aliasSearchEnabled"]?.toBooleanStrictOrNull() ?: current?.aliasSearchEnabled ?: true

        val updated = AuthorSettings(
            authorId = Id(authorId),
            lastActiveAt = current?.lastActiveAt ?: now,
            inactivityPeriodDays = days,
            discardAction = action,
            username = newUsername,
            bio = newBio,
            aliasSearchEnabled = aliasSearchEnabled,
            updatedAt = now
        )
        repository.saveAuthorSettings(updated)
        repository.logAuthorActivity(Id(authorId), "UPDATE_SETTINGS")
        call.respond(updated)
    }

    // Resolve author by either UUID or @username
    get("/api/author/resolve") {
        val query = call.request.queryParameters["query"] ?: call.request.queryParameters["username"]
        if (!query.isNullOrBlank()) {
            val resolvedId = repository.resolveAuthorByUsernameOrId(query)
            if (resolvedId != null) {
                val passage = repository.get(resolvedId)
                val settings = repository.getAuthorSettings(resolvedId)
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "found" to true,
                        "authorId" to resolvedId.value,
                        "moniker" to ((passage as? connects.quotes.model.AuthorPassage)?.moniker ?: resolvedId.value),
                        "username" to (settings?.username ?: ""),
                        "bio" to (settings?.bio ?: "")
                    )
                )
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("found" to false, "error" to "Author not found"))
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing query")
        }
    }

    // Check username availability
    get("/api/author/check-username") {
        val username = call.request.queryParameters["username"]
        val authorId = call.request.queryParameters["authorId"]?.let { Id(it) }
        if (!username.isNullOrBlank()) {
            val available = repository.checkUsernameAvailable(username, authorId)
            call.respond(HttpStatusCode.OK, mapOf("available" to available, "username" to username.trim().removePrefix("@")))
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing username")
        }
    }

    // Author direct subscriptions management
    get("/api/author/subscriptions") {
        val authorId = call.request.queryParameters["authorId"]
        if (!authorId.isNullOrBlank()) {
            val targetIds = repository.getAuthorSubscriptions(Id(authorId))
            val passages = targetIds.mapNotNull { repository.get(it) }
            call.respond(passages)
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
        }
    }

    post("/api/author/subscriptions/add") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val targetPassageId = params["targetPassageId"]
        if (authorId.isNullOrBlank() || targetPassageId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId or targetPassageId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot modify subscriptions for another author"))
            return@post
        }
        val ok = repository.addAuthorSubscription(Id(authorId), Id(targetPassageId))
        repository.logAuthorActivity(Id(authorId), "SUBSCRIBE")
        connects.quotes.chat.LiveChatHub.broadcastGraphChanged("SUBSCRIPTION", targetPassageId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "success" to ok))
    }

    post("/api/author/subscriptions/remove") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val targetPassageId = params["targetPassageId"]
        if (authorId.isNullOrBlank() || targetPassageId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId or targetPassageId")
            return@post
        }
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }
        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot modify subscriptions for another author"))
            return@post
        }
        val ok = repository.removeAuthorSubscription(Id(authorId), Id(targetPassageId))
        repository.logAuthorActivity(Id(authorId), "UNSUBSCRIBE")
        connects.quotes.chat.LiveChatHub.broadcastGraphChanged("SUBSCRIPTION", targetPassageId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "success" to ok))
    }

    // Server function to perform inactivity check based on last logged time
    post("/api/author/inactivity-check") {
        val params = try { call.receiveParameters() } catch (_: Throwable) { io.ktor.http.Parameters.Empty }
        val time = params["currentTimeMillis"]?.toLongOrNull()
            ?: call.request.queryParameters["currentTimeMillis"]?.toLongOrNull()
            ?: System.currentTimeMillis()
        val result = repository.performInactivityCheck(time)
        call.respond(result)
    }

    // Execute author discard on demand (delete/orphan according to author selection)
    post("/api/author/discard") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val actionStr = params["action"]
        val action = if (!actionStr.isNullOrBlank()) {
            try { AuthorDiscardAction.valueOf(actionStr.uppercase()) } catch (_: Exception) { null }
        } else null

        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@post
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot discard another author's identity"))
            return@post
        }
        val ok = repository.executeAuthorDiscard(Id(authorId), action)
        call.respond(HttpStatusCode.OK, """{"status":"ok","success":$ok}""")
    }

    // Author advertised passage IDs
    get("/api/author/advertised-passages") {
        val authorId = call.request.queryParameters["authorId"]
        if (!authorId.isNullOrBlank()) {
            val advIds = repository.getAdvertisedPassageIds(Id(authorId))
            call.respond(HttpStatusCode.OK, advIds.map { it.value })
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
        }
    }

    // Toggle or bulk change passage visibility
    post("/api/author/passage-visibility") {
        val principal = call.extractPrincipal(repository)
        val params = call.receiveParameters()
        val authorId = params["authorId"] ?: principal?.authorId?.value
        val passageId = params["passageId"]
        val typeStr = params["type"]
        val advertiseStr = params["advertise"]
        val advertise = advertiseStr?.toBooleanStrictOrNull()

        if (authorId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing authorId")
            return@post
        }

        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@post
        }

        if (principal.authorId.value != authorId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden: Cannot change visibility for another author"))
            return@post
        }

        if (!passageId.isNullOrBlank()) {
            val toggleUseCase = connects.quotes.usecase.ToggleAdvertisementUseCase(repository, repository)
            val res = toggleUseCase(Id(passageId), Id(authorId))
            if (res.isSuccess) {
                connects.quotes.chat.LiveChatHub.broadcastGraphChanged("BILLBOARD_VISIBILITY", passageId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "success" to true, "isAdvertised" to res.getOrThrow()))
            } else {
                call.respond(HttpStatusCode.BadRequest, res.exceptionOrNull()?.message ?: "Failed to toggle advertisement")
            }
        } else {
            val type = if (!typeStr.isNullOrBlank() && !typeStr.equals("ALL", ignoreCase = true)) {
                try { PassageType.valueOf(typeStr.uppercase()) } catch (_: Exception) { null }
            } else null
            val shouldAdv = advertise ?: true
            val count = repository.setPassageTypeAdvertisement(Id(authorId), type, shouldAdv)
            connects.quotes.chat.LiveChatHub.broadcastGraphChanged("BILLBOARD_VISIBILITY", authorId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "count" to count))
        }
    }
}
