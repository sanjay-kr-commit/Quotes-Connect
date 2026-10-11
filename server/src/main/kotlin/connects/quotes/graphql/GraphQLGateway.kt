package connects.quotes.graphql

import connects.quotes.chat.LiveChatHub
import connects.quotes.database.JdbcPassageRepository
import connects.quotes.model.*
import connects.quotes.security.AuthPrincipal
import connects.quotes.usecase.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * GraphQL API Gateway for Quotes Connect.
 * Dispatches queries and mutations to core use cases and repositories using AST root-field parsing.
 * Enforces server-side authorization matrix against AuthPrincipal.
 * Enforces schema-level privacy: server-private `parentId` is NEVER included in responses.
 */
class GraphQLGateway(
    private val repository: JdbcPassageRepository
) {
    private val logger = LoggerFactory.getLogger(GraphQLGateway::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val createPassageUseCase = CreatePassageUseCase(repository, repository, repository, repository)
    private val subscribePassageUseCase = SubscribePassageUseCase(repository, repository)
    private val toggleAdvertisementUseCase = ToggleAdvertisementUseCase(repository, repository)
    private val deletePassageUseCase = DeletePassageUseCase(repository)
    private val traverseGraphUseCase = TraverseGraphUseCase(repository)

    suspend fun execute(request: GraphQLRequest, principal: AuthPrincipal? = null): GraphQLResponse {
        val rawQuery = request.query.trim()
        val vars = request.variables ?: emptyMap()

        return try {
            val operation = GraphQLDocumentParser.parse(rawQuery)
            val (data, errors) = when (operation.type) {
                OperationType.MUTATION -> executeMutation(operation, vars, principal)
                else -> executeQuery(operation, vars, principal)
            }
            GraphQLResponse(data = data, errors = errors)
        } catch (e: Exception) {
            logger.error("GraphQL execution error", e)
            GraphQLResponse(data = null, errors = listOf(GraphQLError(e.message ?: "Unknown error")))
        }
    }

    private suspend fun executeQuery(
        operation: ParsedOperation,
        variables: Map<String, JsonElement>,
        principal: AuthPrincipal?
    ): Pair<JsonElement, List<GraphQLError>?> {
        val result = mutableMapOf<String, JsonElement>()
        val errors = mutableListOf<GraphQLError>()

        for (field in operation.rootFields) {
            val fieldKey = field.alias ?: field.name
            val args = field.argsString ?: ""

            when (field.name) {
                "health" -> {
                    result[fieldKey] = JsonPrimitive("OK")
                }

                "billboard" -> {
                    val limit = getIntParam("limit", args, variables) ?: 50
                    val offset = getIntParam("offset", args, variables) ?: 0
                    val tag = getStringParam("tag", args, variables)
                    val items = repository.getAdvertised(limit = limit, offset = offset, tag = tag)
                    result[fieldKey] = json.encodeToJsonElement(items)
                }

                "feed" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        val authorId = Id(authorIdStr)
                        val depth = getIntParam("depth", args, variables) ?: 1
                        val limit = getIntParam("limit", args, variables) ?: 50
                        val offset = getIntParam("offset", args, variables) ?: 0
                        val passages = repository.getFeed(authorId, depth = depth, limit = limit, offset = offset)
                        result[fieldKey] = json.encodeToJsonElement(passages)
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "passage" -> {
                    val idStr = getStringParam("id", args, variables)
                    if (idStr != null) {
                        val passage = repository.get(Id(idStr))
                        result[fieldKey] = if (passage != null) json.encodeToJsonElement(passage) else JsonNull
                    } else {
                        result[fieldKey] = JsonNull
                    }
                }

                "tags" -> {
                    val search = getStringParam("query", args, variables)
                    val limit = getIntParam("limit", args, variables) ?: 30
                    val tags = if (search.isNullOrBlank()) {
                        repository.getTopTags(limit)
                    } else {
                        repository.suggestTags(search, limit)
                    }
                    result[fieldKey] = json.encodeToJsonElement(tags)
                }

                "notifications" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access notifications"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else if (principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: cannot access notifications for another author"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else {
                            val notifs = repository.getNotifications(Id(authorIdStr))
                            result[fieldKey] = json.encodeToJsonElement(notifs)
                        }
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "search" -> {
                    val q = getStringParam("query", args, variables) ?: ""
                    val limit = getIntParam("limit", args, variables) ?: 30
                    val matches = repository.search(q, limit)
                    result[fieldKey] = json.encodeToJsonElement(matches)
                }

                "shortestPath" -> {
                    val start = getStringParam("startId", args, variables)
                    val end = getStringParam("endId", args, variables)
                    if (start != null && end != null) {
                        val path = traverseGraphUseCase.findPath(Id(start), Id(end))
                        result[fieldKey] = json.encodeToJsonElement(path.map { it.value })
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "subscribers" -> {
                    val idStr = getStringParam("id", args, variables)
                    if (idStr != null) {
                        val subIds = repository.getSubscribers(Id(idStr))
                        val passages = subIds.mapNotNull { repository.get(it) }
                        result[fieldKey] = json.encodeToJsonElement(passages)
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "subscribedTo" -> {
                    val idStr = getStringParam("id", args, variables)
                    if (idStr != null) {
                        val targetIds = repository.getSubscribedTo(Id(idStr))
                        val passages = targetIds.mapNotNull { repository.get(it) }
                        result[fieldKey] = json.encodeToJsonElement(passages)
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "isOrphan" -> {
                    val idStr = getStringParam("id", args, variables)
                    val isOrphan = if (idStr != null) repository.isOrphan(Id(idStr)) else false
                    result[fieldKey] = JsonPrimitive(isOrphan)
                }

                "isAdvertised" -> {
                    val idStr = getStringParam("id", args, variables)
                    val isAdv = if (idStr != null) repository.isAdvertised(Id(idStr)) else false
                    result[fieldKey] = JsonPrimitive(isAdv)
                }

                "descendantTree", "tree" -> {
                    val rootIdStr = getStringParam("rootId", args, variables) ?: getStringParam("id", args, variables)
                    val maxDepth = getIntParam("maxDepth", args, variables) ?: 4
                    if (rootIdStr != null) {
                        val tree = traverseGraphUseCase.getDescendantTree(Id(rootIdStr), maxDepth)
                        result[fieldKey] = if (tree != null) json.encodeToJsonElement(tree) else JsonNull
                    } else {
                        result[fieldKey] = JsonNull
                    }
                }

                "createdPassages" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        val created = repository.getCreatedByAuthor(Id(authorIdStr))
                        result[fieldKey] = json.encodeToJsonElement(created)
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "chats" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access chats"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else if (principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: cannot access chats of another author"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else {
                            val sessions = repository.getChatSessionsForAuthor(Id(authorIdStr))
                            result[fieldKey] = json.encodeToJsonElement(sessions)
                        }
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "chat" -> {
                    val chatIdStr = getStringParam("id", args, variables) ?: getStringParam("chatId", args, variables)
                    if (chatIdStr != null) {
                        val session = repository.getChatSession(Id(chatIdStr))
                        if (session == null) {
                            result[fieldKey] = JsonNull
                        } else if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access chat"))
                            result[fieldKey] = JsonNull
                        } else if (session.initiatorId != principal.authorId && session.recipientId != principal.authorId) {
                            errors.add(GraphQLError("Forbidden: not a participant of this chat"))
                            result[fieldKey] = JsonNull
                        } else {
                            result[fieldKey] = json.encodeToJsonElement(session)
                        }
                    } else {
                        result[fieldKey] = JsonNull
                    }
                }

                "chatMessages" -> {
                    val chatIdStr = getStringParam("chatId", args, variables)
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (chatIdStr != null && authorIdStr != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access chat messages"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else if (principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: authorId does not match authenticated caller"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else {
                            val session = repository.getChatSession(Id(chatIdStr))
                            if (session != null && session.initiatorId != principal.authorId && session.recipientId != principal.authorId) {
                                errors.add(GraphQLError("Forbidden: caller is not a participant of this chat"))
                                result[fieldKey] = JsonArray(emptyList())
                            } else {
                                val messages = repository.getChatMessages(Id(chatIdStr), Id(authorIdStr))
                                result[fieldKey] = json.encodeToJsonElement(messages)
                            }
                        }
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "keystore" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access keystore"))
                            result[fieldKey] = JsonNull
                        } else if (principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: cannot access keystore of another author"))
                            result[fieldKey] = JsonNull
                        } else {
                            val ks = repository.getKeyStore(Id(authorIdStr))
                            result[fieldKey] = if (ks != null) json.encodeToJsonElement(ks) else JsonNull
                        }
                    } else {
                        result[fieldKey] = JsonNull
                    }
                }

                "authorSettings" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        val settings = repository.getAuthorSettings(Id(authorIdStr))
                        result[fieldKey] = if (settings != null) json.encodeToJsonElement(settings) else JsonNull
                    } else {
                        result[fieldKey] = JsonNull
                    }
                }

                "subscribed" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        val targetIds = repository.getAuthorSubscriptions(Id(authorIdStr))
                        val passages = targetIds.mapNotNull { repository.get(it) }
                        result[fieldKey] = json.encodeToJsonElement(passages)
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "authorActivityLogs" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val limit = getIntParam("limit", args, variables) ?: 20
                    if (authorIdStr != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to access activity logs"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else if (principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: cannot access activity logs of another author"))
                            result[fieldKey] = JsonArray(emptyList())
                        } else {
                            val logs = repository.getAuthorActivityLogs(Id(authorIdStr), limit)
                            result[fieldKey] = json.encodeToJsonElement(logs)
                        }
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "advertisedPassageIds" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorIdStr != null) {
                        val advIds = repository.getAdvertisedPassageIds(Id(authorIdStr))
                        result[fieldKey] = json.encodeToJsonElement(advIds.map { it.value })
                    } else {
                        result[fieldKey] = JsonArray(emptyList())
                    }
                }

                "performInactivityCheck" -> {
                    val sweep = repository.performInactivityCheck(System.currentTimeMillis())
                    result[fieldKey] = json.encodeToJsonElement(sweep)
                }

                "__schema", "__type" -> {
                    result[fieldKey] = buildIntrospectionSchema()
                }

                else -> {
                    errors.add(GraphQLError("Unknown query field: '${field.name}'"))
                }
            }
        }

        return JsonObject(result) to (if (errors.isNotEmpty()) errors else null)
    }

    private suspend fun executeMutation(
        operation: ParsedOperation,
        variables: Map<String, JsonElement>,
        principal: AuthPrincipal?
    ): Pair<JsonElement, List<GraphQLError>?> {
        val result = mutableMapOf<String, JsonElement>()
        val errors = mutableListOf<GraphQLError>()

        for (field in operation.rootFields) {
            val fieldKey = field.alias ?: field.name
            val args = field.argsString ?: ""

            when (field.name) {
                "createIdentity" -> {
                    val moniker = getStringParam("moniker", args, variables) ?: "Thinker"
                    val shouldAdvertise = getBoolParam("shouldAdvertise", args, variables) ?: false
                    val now = System.currentTimeMillis()
                    val authorPassage = createPassageUseCase(
                        type = PassageType.AUTHOR,
                        content = moniker,
                        tags = listOf("author", "identity", "inactivity:30d"),
                        parentId = null,
                        shouldAdvertise = shouldAdvertise,
                        currentTimeMillis = now
                    ).getOrThrow()

                    val passphrase = Credentials.generatePassphrase()
                    repository.saveCredentials(Credentials(authorPassage.id, passphrase))
                    repository.saveAuthorSettings(
                        AuthorSettings(
                            authorId = authorPassage.id,
                            lastActiveAt = now,
                            inactivityPeriodDays = 30,
                            discardAction = AuthorDiscardAction.ORPHAN,
                            updatedAt = now
                        )
                    )
                    val session = repository.createSession(authorPassage.id)

                    result[fieldKey] = buildJsonObject {
                        put("passageId", authorPassage.id.value)
                        put("passphrase", passphrase)
                        put("token", session.accessToken)
                        put("accessToken", session.accessToken)
                        put("sessionToken", session.accessToken)
                    }
                }

                "login" -> {
                    val authorId = getStringParam("authorId", args, variables)
                    val passphrase = getStringParam("passphrase", args, variables)
                    if (authorId.isNullOrBlank() || passphrase.isNullOrBlank()) {
                        errors.add(GraphQLError("Missing authorId or passphrase"))
                    } else {
                        val resolvedId = repository.resolveAuthorByUsernameOrId(authorId.trim()) ?: Id(authorId.trim())
                        if (repository.verifyCredentials(resolvedId, passphrase)) {
                            val session = repository.createSession(resolvedId)
                            result[fieldKey] = buildJsonObject {
                                put("token", session.accessToken)
                                put("accessToken", session.accessToken)
                                put("sessionId", session.sessionId.value)
                                put("authorId", resolvedId.value)
                                put("expiresAt", session.expiresAt)
                            }
                        } else {
                            errors.add(GraphQLError("Invalid author identity or passphrase"))
                        }
                    }
                }

                "createPassage" -> {
                    val typeStr = getStringParam("type", args, variables) ?: "PASSAGE"
                    val type = try { PassageType.valueOf(typeStr.uppercase()) } catch (_: Exception) { PassageType.PASSAGE }
                    val content = getStringParam("content", args, variables) ?: ""
                    val tagsList = getListParam("tags", args, variables)
                    val parentIdStr = getStringParam("parentId", args, variables) ?: principal?.authorId?.value
                    val isRecordable = getBoolParam("isRecordable", args, variables) ?: false
                    val shouldAdvertise = getBoolParam("shouldAdvertise", args, variables) ?: false
                    val subscribeToIdStr = getStringParam("subscribeToId", args, variables)

                    if (!parentIdStr.isNullOrBlank() && (principal == null || parentIdStr != principal.authorId.value)) {
                        errors.add(GraphQLError("Forbidden: cannot create owned passage under another author's parentId"))
                    } else {
                        val passageResult = createPassageUseCase(
                            type = type,
                            content = content,
                            tags = tagsList,
                            parentId = if (!parentIdStr.isNullOrBlank()) Id(parentIdStr) else null,
                            isRecordable = isRecordable,
                            shouldAdvertise = shouldAdvertise,
                            subscribeToId = if (!subscribeToIdStr.isNullOrBlank()) Id(subscribeToIdStr) else null,
                            currentTimeMillis = System.currentTimeMillis()
                        )

                        if (passageResult.isSuccess) {
                            result[fieldKey] = json.encodeToJsonElement(passageResult.getOrThrow())
                        } else {
                            errors.add(GraphQLError(passageResult.exceptionOrNull()?.message ?: "Failed to create passage"))
                        }
                    }
                }

                "subscribePassage" -> {
                    val sourceId = getStringParam("sourceId", args, variables)
                    val targetId = getStringParam("targetId", args, variables)
                    if (sourceId != null && targetId != null) {
                        val subResult = subscribePassageUseCase(
                            sourceId = Id(sourceId),
                            targetId = Id(targetId),
                            currentTimeMillis = System.currentTimeMillis()
                        )
                        if (subResult.isSuccess) {
                            result[fieldKey] = JsonPrimitive(subResult.getOrThrow())
                        } else {
                            errors.add(GraphQLError(subResult.exceptionOrNull()?.message ?: "Failed to subscribe"))
                        }
                    } else {
                        errors.add(GraphQLError("Missing sourceId or targetId"))
                    }
                }

                "toggleBillboard" -> {
                    val passageId = getStringParam("passageId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (passageId != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to toggle billboard"))
                        } else if (authorId != null && principal.authorId.value != authorId) {
                            errors.add(GraphQLError("Forbidden: cannot toggle billboard for another author"))
                        } else {
                            val toggleResult = toggleAdvertisementUseCase(
                                passageId = Id(passageId),
                                requesterAuthorId = principal.authorId
                            )
                            if (toggleResult.isSuccess) {
                                LiveChatHub.broadcastGraphChanged("BILLBOARD_VISIBILITY", passageId)
                                result[fieldKey] = JsonPrimitive(toggleResult.getOrThrow())
                            } else {
                                errors.add(GraphQLError(toggleResult.exceptionOrNull()?.message ?: "Failed to toggle advertisement"))
                            }
                        }
                    } else {
                        errors.add(GraphQLError("Missing passageId"))
                    }
                }

                "deletePassage" -> {
                    val passageId = getStringParam("id", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (passageId != null) {
                        if (principal == null) {
                            errors.add(GraphQLError("Unauthorized: Authentication required to delete passage"))
                        } else if (authorId != null && principal.authorId.value != authorId) {
                            errors.add(GraphQLError("Forbidden: cannot delete passage on behalf of another author"))
                        } else {
                            val delResult = deletePassageUseCase(
                                passageId = Id(passageId),
                                requesterAuthorId = principal.authorId
                            )
                            if (delResult.isSuccess) {
                                result[fieldKey] = JsonPrimitive(delResult.getOrThrow())
                            } else {
                                errors.add(GraphQLError(delResult.exceptionOrNull()?.message ?: "Cannot delete passage"))
                            }
                        }
                    } else {
                        errors.add(GraphQLError("Missing passage id"))
                    }
                }

                "changePassphrase" -> {
                    val authorIdStr = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val oldPass = getStringParam("oldPassphrase", args, variables)
                    val newPass = getStringParam("newPassphrase", args, variables)
                    if (authorIdStr != null && oldPass != null && newPass != null) {
                        if (principal != null && principal.authorId.value != authorIdStr) {
                            errors.add(GraphQLError("Forbidden: cannot change passphrase of another author"))
                        } else if (newPass.isBlank() || newPass.length < 6) {
                            errors.add(GraphQLError("New passphrase must be at least 6 characters long."))
                        } else {
                            val updated = repository.updatePassphrase(Id(authorIdStr), oldPass, newPass)
                            if (updated) {
                                result[fieldKey] = JsonPrimitive(true)
                            } else {
                                errors.add(GraphQLError("Invalid current passphrase or update failed."))
                            }
                        }
                    } else {
                        errors.add(GraphQLError("Missing authorId, oldPassphrase, or newPassphrase"))
                    }
                }

                "requestChat" -> {
                    val initiatorId = getStringParam("initiatorId", args, variables) ?: principal?.authorId?.value
                    val recipientId = getStringParam("recipientId", args, variables)?.trim()
                    val typeStr = getStringParam("type", args, variables) ?: "UNENCRYPTED"
                    val type = try { ChatType.valueOf(typeStr.uppercase()) } catch (_: Exception) { ChatType.UNENCRYPTED }
                    val initiatorPublicKey = getStringParam("initiatorPublicKey", args, variables)

                    if (initiatorId == null || recipientId == null) {
                        errors.add(GraphQLError("Missing initiatorId or recipientId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to request chat"))
                    } else if (principal.authorId.value != initiatorId) {
                        errors.add(GraphQLError("Forbidden: initiatorId does not match authenticated identity"))
                    } else {
                        val isAlias = recipientId.startsWith("@")
                        val resolved = repository.resolveAuthorByUsernameOrId(recipientId)
                        val finalRecipientId = when {
                            resolved != null -> resolved
                            !isAlias && isValidIdFormat(recipientId) -> Id(recipientId)
                            else -> null
                        }

                        if (finalRecipientId == null) {
                            if (isAlias) {
                                errors.add(GraphQLError("Invalid recipient: Alias '${recipientId}' not found or alias search is disabled"))
                            } else {
                                errors.add(GraphQLError("Invalid recipient: '${recipientId}' is not a valid author UUID"))
                            }
                        } else {
                            val existing = repository.getChatSessionsForAuthor(Id(initiatorId)).firstOrNull {
                                ((it.initiatorId == Id(initiatorId) && it.recipientId == finalRecipientId) ||
                                 (it.initiatorId == finalRecipientId && it.recipientId == Id(initiatorId))) &&
                                it.type == type
                            }
                            if (existing != null) {
                                errors.add(GraphQLError("Duplicate chat: A ${type.name.lowercase().replace('_', ' ')} chat session already exists with this author"))
                            } else {
                                val session = repository.createChatSession(
                                    initiatorId = Id(initiatorId),
                                    recipientId = finalRecipientId,
                                    type = type,
                                    initiatorPublicKey = initiatorPublicKey
                                )
                                LiveChatHub.broadcastChatRequested(session)
                                result[fieldKey] = json.encodeToJsonElement(session)
                            }
                        }
                    }
                }

                "deleteChat" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (chatId == null || authorId == null) {
                        errors.add(GraphQLError("Missing chatId or authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to delete chat"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot delete chat for another author"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        val ok = repository.deleteChatSession(Id(chatId), Id(authorId))
                        if (ok && session != null) {
                            LiveChatHub.broadcastChatDeleted(
                                chatId = chatId,
                                deletedBy = authorId,
                                initiatorId = session.initiatorId.value,
                                recipientId = session.recipientId.value
                            )
                        }
                        result[fieldKey] = JsonPrimitive(ok)
                    }
                }

                "respondChat" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val accept = getBoolParam("accept", args, variables) ?: false
                    val recipientPublicKey = getStringParam("recipientPublicKey", args, variables)

                    if (chatId == null || authorId == null) {
                        errors.add(GraphQLError("Missing chatId or authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to respond to chat"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: authorId does not match authenticated identity"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null) {
                            errors.add(GraphQLError("Chat session not found"))
                        } else if (session.recipientId.value != authorId) {
                            errors.add(GraphQLError("Only the chat recipient can respond to this request"))
                        } else {
                            val newStatus = if (accept) ChatStatus.ACCEPTED else ChatStatus.REJECTED
                            repository.updateChatStatus(Id(chatId), newStatus, recipientPublicKey)
                            val updatedSession = repository.getChatSession(Id(chatId))
                            if (updatedSession != null) {
                                LiveChatHub.broadcastSessionUpdated(updatedSession)
                                result[fieldKey] = json.encodeToJsonElement(updatedSession)
                            } else {
                                result[fieldKey] = JsonNull
                            }
                        }
                    }
                }

                "sendChatMessage" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val senderId = getStringParam("senderId", args, variables) ?: principal?.authorId?.value
                    val recipientId = getStringParam("recipientId", args, variables)
                    val content = getStringParam("content", args, variables) ?: ""
                    val isEncrypted = getBoolParam("isEncrypted", args, variables) ?: false
                    val citedPassageId = getStringParam("citedPassageId", args, variables)

                    if (chatId == null || senderId == null || recipientId == null) {
                        errors.add(GraphQLError("Missing chatId, senderId, or recipientId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to send chat message"))
                    } else if (principal.authorId.value != senderId) {
                        errors.add(GraphQLError("Forbidden: senderId does not match authenticated identity"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null) {
                            errors.add(GraphQLError("Chat session not found"))
                        } else if (session.initiatorId.value != senderId && session.recipientId.value != senderId) {
                            errors.add(GraphQLError("Forbidden: sender is not a participant of this chat"))
                        } else {
                            val msgId = Id("msg-" + java.util.UUID.randomUUID().toString().take(12))
                            val now = System.currentTimeMillis()
                            val chatMessage = ChatMessage(
                                id = msgId,
                                chatId = Id(chatId),
                                senderId = Id(senderId),
                                recipientId = Id(recipientId),
                                content = content,
                                isEncrypted = isEncrypted,
                                citedPassageId = if (!citedPassageId.isNullOrBlank()) Id(citedPassageId) else null,
                                timestamp = now
                            )
                            val decision = connects.quotes.security.ChatSecurityPolicy.validateSend(session, chatMessage)
                            if (decision is connects.quotes.security.SecurityDecision.Rejected) {
                                errors.add(GraphQLError("Security Policy Violation: ${decision.reason}"))
                            } else {
                                val sent = repository.sendChatMessage(chatMessage)
                                if (sent) {
                                    LiveChatHub.broadcastMessage(chatMessage)
                                    result[fieldKey] = json.encodeToJsonElement(chatMessage)
                                } else {
                                    errors.add(GraphQLError("Failed to send message: Chat is not active or unauthorized"))
                                }
                            }
                        }
                    }
                }

                "renewChatKey" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val newPublicKey = getStringParam("newPublicKey", args, variables)

                    if (chatId == null || authorId == null || newPublicKey == null) {
                        errors.add(GraphQLError("Missing chatId, authorId, or newPublicKey"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to renew chat key"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: authorId does not match authenticated identity"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
                            errors.add(GraphQLError("Forbidden: author is not a participant of this chat"))
                        } else {
                            val renewedSession = repository.renewChatKey(Id(chatId), Id(authorId), newPublicKey)
                            if (renewedSession != null) {
                                LiveChatHub.broadcastKeyChanged(renewedSession, Id(authorId), newPublicKey)
                                result[fieldKey] = json.encodeToJsonElement(renewedSession)
                            } else {
                                errors.add(GraphQLError("Failed to renew chat key: session not found or unauthorized"))
                            }
                        }
                    }
                }

                "backupKeyStore" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val encryptedPayload = getStringParam("encryptedPayload", args, variables)
                    val salt = getStringParam("salt", args, variables)
                    val iv = getStringParam("iv", args, variables)
                    val version = getIntParam("version", args, variables)

                    if (authorId == null || encryptedPayload == null || salt == null || iv == null || version == null) {
                        errors.add(GraphQLError("Missing authorId, encryptedPayload, salt, iv, or version"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to backup keystore"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot backup keystore for another author"))
                    } else {
                        val validationError = validateEncryptedKeyStorePayload(
                            encryptedPayload = encryptedPayload,
                            salt = salt,
                            iv = iv,
                            version = version
                        )
                        if (validationError != null) {
                            errors.add(GraphQLError(validationError))
                        } else {
                            val ks = KeyStorePayload(
                                authorId = Id(authorId),
                                encryptedPayload = encryptedPayload,
                                salt = salt,
                                iv = iv,
                                version = version,
                                updatedAt = System.currentTimeMillis()
                            )
                            val ok = repository.backupKeyStore(ks)
                            result[fieldKey] = JsonPrimitive(ok)
                        }
                    }
                }

                "deleteKeyStore" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorId == null) {
                        errors.add(GraphQLError("Missing authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to delete keystore"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot delete keystore for another author"))
                    } else {
                        val ok = repository.deleteKeyStore(Id(authorId))
                        result[fieldKey] = JsonPrimitive(ok)
                    }
                }

                "setTyping" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val isTyping = getBoolParam("isTyping", args, variables) ?: false

                    if (chatId == null || authorId == null) {
                        errors.add(GraphQLError("Missing chatId or authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to set typing status"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot spoof typing status"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
                            errors.add(GraphQLError("Forbidden: author is not a participant of this chat"))
                        } else {
                            LiveChatHub.broadcastTyping(chatId, authorId, isTyping)
                            result[fieldKey] = JsonPrimitive(true)
                        }
                    }
                }

                "requestChatResync" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val requesterId = getStringParam("requesterId", args, variables) ?: principal?.authorId?.value
                    if (chatId == null || requesterId == null) {
                        errors.add(GraphQLError("Missing chatId or requesterId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to request chat resync"))
                    } else if (principal.authorId.value != requesterId) {
                        errors.add(GraphQLError("Forbidden: cannot spoof resync request"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null || (session.initiatorId.value != requesterId && session.recipientId.value != requesterId)) {
                            errors.add(GraphQLError("Forbidden: requester is not a participant of this chat"))
                        } else {
                            LiveChatHub.broadcastResyncRequest(chatId, requesterId)
                            result[fieldKey] = JsonPrimitive(true)
                        }
                    }
                }

                "updateAuthorSettings" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val days = getIntParam("inactivityPeriodDays", args, variables) ?: 30
                    val actionStr = getStringParam("discardAction", args, variables) ?: "ORPHAN"
                    val action = try { AuthorDiscardAction.valueOf(actionStr.uppercase()) } catch (_: Exception) { AuthorDiscardAction.ORPHAN }
                    val username = getStringParam("username", args, variables)?.trim()?.removePrefix("@")
                    val bio = getStringParam("bio", args, variables)
                    val aliasSearchEnabled = getBoolParam("aliasSearchEnabled", args, variables)

                    if (authorId == null) {
                        errors.add(GraphQLError("Missing authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to update settings"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot update settings of another author"))
                    } else {
                        val now = System.currentTimeMillis()
                        val current = repository.getAuthorSettings(Id(authorId))
                        val cleanUsername = if (!username.isNullOrBlank()) {
                            if (!repository.checkUsernameAvailable(username, Id(authorId))) {
                                errors.add(GraphQLError("Username @$username is already taken"))
                                return@executeMutation JsonObject(result) to errors
                            }
                            username
                        } else current?.username

                        val updated = AuthorSettings(
                            authorId = Id(authorId),
                            lastActiveAt = current?.lastActiveAt ?: now,
                            inactivityPeriodDays = days,
                            discardAction = action,
                            username = cleanUsername,
                            bio = bio ?: current?.bio,
                            aliasSearchEnabled = aliasSearchEnabled ?: current?.aliasSearchEnabled ?: true,
                            updatedAt = now
                        )
                        repository.saveAuthorSettings(updated)
                        repository.logAuthorActivity(Id(authorId), "UPDATE_SETTINGS")
                        result[fieldKey] = json.encodeToJsonElement(updated)
                    }
                }

                "pingAuthorActive" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (authorId == null) {
                        errors.add(GraphQLError("Missing authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to ping activity"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot ping on behalf of another author"))
                    } else {
                        repository.logAuthorActivity(Id(authorId), "ACTIVE_PING")
                        result[fieldKey] = JsonPrimitive(true)
                    }
                }

                "performInactivityCheck" -> {
                    val time = getLongParam("currentTimeMillis", args, variables) ?: System.currentTimeMillis()
                    val sweepResult = repository.performInactivityCheck(time)
                    result[fieldKey] = json.encodeToJsonElement(sweepResult)
                }

                "discardAuthor" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val actionStr = getStringParam("action", args, variables)
                    val action = if (actionStr != null) {
                        try { AuthorDiscardAction.valueOf(actionStr.uppercase()) } catch (_: Exception) { null }
                    } else null

                    if (authorId == null) {
                        errors.add(GraphQLError("Missing authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to discard author"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot discard another author's identity"))
                    } else {
                        val ok = repository.executeAuthorDiscard(Id(authorId), action)
                        result[fieldKey] = JsonPrimitive(ok)
                    }
                }

                "subscribeAuthorPassage" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val targetPassageId = getStringParam("targetPassageId", args, variables)
                    if (authorId == null || targetPassageId == null) {
                        errors.add(GraphQLError("Missing authorId or targetPassageId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to subscribe"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot subscribe on behalf of another author"))
                    } else {
                        val ok = repository.addAuthorSubscription(Id(authorId), Id(targetPassageId))
                        LiveChatHub.broadcastGraphChanged("SUBSCRIPTION", targetPassageId)
                        result[fieldKey] = JsonPrimitive(ok)
                    }
                }

                "unsubscribeAuthorPassage" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val targetPassageId = getStringParam("targetPassageId", args, variables)
                    if (authorId == null || targetPassageId == null) {
                        errors.add(GraphQLError("Missing authorId or targetPassageId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to unsubscribe"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot unsubscribe on behalf of another author"))
                    } else {
                        val ok = repository.removeAuthorSubscription(Id(authorId), Id(targetPassageId))
                        LiveChatHub.broadcastGraphChanged("SUBSCRIPTION", targetPassageId)
                        result[fieldKey] = JsonPrimitive(ok)
                    }
                }

                "proposeChatAlias" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val alias = getStringParam("alias", args, variables)
                    if (chatId == null || authorId == null || alias.isNullOrBlank()) {
                        errors.add(GraphQLError("Missing chatId, authorId, or alias"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to propose alias"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: authorId does not match authenticated identity"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
                            errors.add(GraphQLError("Forbidden: author is not a participant of this chat"))
                        } else {
                            val updated = repository.proposeSharedAlias(Id(chatId), Id(authorId), alias)
                            if (updated != null) {
                                LiveChatHub.broadcastAliasProposal(updated, authorId, alias)
                                result[fieldKey] = json.encodeToJsonElement(updated)
                            } else {
                                errors.add(GraphQLError("Unable to propose alias"))
                            }
                        }
                    }
                }

                "acceptChatAlias" -> {
                    val chatId = getStringParam("chatId", args, variables)
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    if (chatId == null || authorId == null) {
                        errors.add(GraphQLError("Missing chatId or authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to accept alias"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: authorId does not match authenticated identity"))
                    } else {
                        val session = repository.getChatSession(Id(chatId))
                        if (session == null || (session.initiatorId.value != authorId && session.recipientId.value != authorId)) {
                            errors.add(GraphQLError("Forbidden: author is not a participant of this chat"))
                        } else {
                            val updated = repository.acceptSharedAlias(Id(chatId), Id(authorId))
                            if (updated != null) {
                                LiveChatHub.broadcastAliasAccepted(updated, updated.sharedAlias ?: "")
                                result[fieldKey] = json.encodeToJsonElement(updated)
                            } else {
                                errors.add(GraphQLError("Unable to accept alias"))
                            }
                        }
                    }
                }

                "setPassageTypeAdvertisement" -> {
                    val authorId = getStringParam("authorId", args, variables) ?: principal?.authorId?.value
                    val typeStr = getStringParam("type", args, variables)
                    val type = if (!typeStr.isNullOrBlank() && !typeStr.equals("ALL", ignoreCase = true)) {
                        try { PassageType.valueOf(typeStr.uppercase()) } catch (_: Exception) { null }
                    } else null
                    val advertise = getBoolParam("advertise", args, variables) ?: true

                    if (authorId == null) {
                        errors.add(GraphQLError("Missing authorId"))
                    } else if (principal == null) {
                        errors.add(GraphQLError("Unauthorized: Authentication required to change advertisement"))
                    } else if (principal.authorId.value != authorId) {
                        errors.add(GraphQLError("Forbidden: cannot change advertisement for another author"))
                    } else {
                        val updatedCount = repository.setPassageTypeAdvertisement(Id(authorId), type, advertise)
                        LiveChatHub.broadcastGraphChanged("BILLBOARD_VISIBILITY", authorId)
                        result[fieldKey] = JsonPrimitive(updatedCount)
                    }
                }

                else -> {
                    errors.add(GraphQLError("Unknown mutation field: '${field.name}'"))
                }
            }
        }

        return JsonObject(result) to (if (errors.isNotEmpty()) errors else null)
    }

    /**
     * Resolves a string parameter from arguments string or variables map.
     * Supports:
     * - `param: "literal"`
     * - `param: $variableName` (looks up variables[variableName])
     * - `variables[param]` fallback
     */
    private fun getStringParam(paramName: String, args: String, vars: Map<String, JsonElement>): String? {
        // 1. Variable reference: e.g. param: $varName
        val varRefRegex = Regex("""$paramName\s*:\s*\$([a-zA-Z0-9_]+)""")
        val varMatch = varRefRegex.find(args)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            vars[varName]?.jsonPrimitive?.contentOrNull?.let { return it }
        }

        // 2. String literal in args: param: "value" (supports escaped quotes)
        val strRegex = Regex("""$paramName\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = strRegex.find(args)
        if (match != null) {
            val raw = match.groupValues[1]
            return unescapeString(raw)
        }

        // 3. Fallback: direct variables map lookup by paramName
        vars[paramName]?.jsonPrimitive?.contentOrNull?.let { return it }

        return null
    }

    private fun getIntParam(paramName: String, args: String, vars: Map<String, JsonElement>): Int? {
        val varRefRegex = Regex("""$paramName\s*:\s*\$([a-zA-Z0-9_]+)""")
        val varMatch = varRefRegex.find(args)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            vars[varName]?.jsonPrimitive?.intOrNull?.let { return it }
        }

        val numRegex = Regex("""$paramName\s*:\s*([0-9]+)""")
        numRegex.find(args)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        return vars[paramName]?.jsonPrimitive?.intOrNull
    }

    private fun getLongParam(paramName: String, args: String, vars: Map<String, JsonElement>): Long? {
        val varRefRegex = Regex("""$paramName\s*:\s*\$([a-zA-Z0-9_]+)""")
        val varMatch = varRefRegex.find(args)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            vars[varName]?.jsonPrimitive?.longOrNull?.let { return it }
        }

        val numRegex = Regex("""$paramName\s*:\s*([0-9]+)""")
        numRegex.find(args)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }

        return vars[paramName]?.jsonPrimitive?.longOrNull
    }

    private fun getBoolParam(paramName: String, args: String, vars: Map<String, JsonElement>): Boolean? {
        val varRefRegex = Regex("""$paramName\s*:\s*\$([a-zA-Z0-9_]+)""")
        val varMatch = varRefRegex.find(args)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            vars[varName]?.jsonPrimitive?.booleanOrNull?.let { return it }
        }

        val boolRegex = Regex("""$paramName\s*:\s*(true|false)""")
        boolRegex.find(args)?.groupValues?.get(1)?.toBooleanStrictOrNull()?.let { return it }

        return vars[paramName]?.jsonPrimitive?.booleanOrNull
    }

    private fun getListParam(paramName: String, args: String, vars: Map<String, JsonElement>): List<String> {
        val varRefRegex = Regex("""$paramName\s*:\s*\$([a-zA-Z0-9_]+)""")
        val varMatch = varRefRegex.find(args)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            val varArray = vars[varName]?.jsonArray
            if (varArray != null) return varArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }

        val listRegex = Regex("""$paramName\s*:\s*\[([^\]]*)\]""")
        val match = listRegex.find(args)?.groupValues?.get(1)
        if (match != null) {
            return match.split(",")
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
        }

        val directArray = vars[paramName]?.jsonArray
        if (directArray != null) return directArray.mapNotNull { it.jsonPrimitive.contentOrNull }

        return emptyList()
    }

    private fun unescapeString(str: String): String {
        return str
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun isValidIdFormat(id: String): Boolean {
        if (id.length < 3 || id.length > 64) return false
        return id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    /**
     * Builds a minimal GraphQL Introspection Schema representation
     * so GraphiQL and developer tooling can introspect available queries and mutations.
     */
    private fun buildIntrospectionSchema(): JsonObject {
        return buildJsonObject {
            putJsonObject("queryType") { put("name", "Query") }
            putJsonObject("mutationType") { put("name", "Mutation") }
            putJsonArray("types") {
                addJsonObject {
                    put("name", "Query")
                    put("kind", "OBJECT")
                    putJsonArray("fields") {
                        add(fieldObj("health", "String"))
                        add(fieldObj("billboard", "BillboardItem", isList = true))
                        add(fieldObj("feed", "Passage", isList = true))
                        add(fieldObj("passage", "Passage"))
                        add(fieldObj("tags", "Tag", isList = true))
                        add(fieldObj("notifications", "Notification", isList = true))
                        add(fieldObj("search", "Passage", isList = true))
                        add(fieldObj("shortestPath", "String", isList = true))
                        add(fieldObj("subscribers", "Passage", isList = true))
                        add(fieldObj("subscribedTo", "Passage", isList = true))
                        add(fieldObj("isOrphan", "Boolean"))
                        add(fieldObj("isAdvertised", "Boolean"))
                        add(fieldObj("createdPassages", "Passage", isList = true))
                        add(fieldObj("chats", "ChatSession", isList = true))
                        add(fieldObj("chat", "ChatSession"))
                        add(fieldObj("chatMessages", "ChatMessage", isList = true))
                        add(fieldObj("keystore", "KeyStorePayload"))
                        add(fieldObj("authorSettings", "AuthorSettings"))
                        add(fieldObj("authorActivityLogs", "AuthorActivityLog", isList = true))
                    }
                }
                addJsonObject {
                    put("name", "Mutation")
                    put("kind", "OBJECT")
                    putJsonArray("fields") {
                        add(fieldObj("createIdentity", "IdentityResult"))
                        add(fieldObj("createPassage", "Passage"))
                        add(fieldObj("subscribePassage", "Boolean"))
                        add(fieldObj("toggleBillboard", "Boolean"))
                        add(fieldObj("deletePassage", "Boolean"))
                        add(fieldObj("changePassphrase", "Boolean"))
                        add(fieldObj("requestChat", "ChatSession"))
                        add(fieldObj("respondChat", "ChatSession"))
                        add(fieldObj("sendChatMessage", "ChatMessage"))
                        add(fieldObj("renewChatKey", "ChatSession"))
                        add(fieldObj("backupKeyStore", "Boolean"))
                        add(fieldObj("deleteKeyStore", "Boolean"))
                        add(fieldObj("deleteChat", "Boolean"))
                        add(fieldObj("setTyping", "Boolean"))
                        add(fieldObj("requestChatResync", "Boolean"))
                        add(fieldObj("updateAuthorSettings", "AuthorSettings"))
                        add(fieldObj("pingAuthorActive", "Boolean"))
                        add(fieldObj("performInactivityCheck", "InactivityCheckResult"))
                        add(fieldObj("discardAuthor", "Boolean"))
                        add(fieldObj("subscribeAuthorPassage", "Boolean"))
                        add(fieldObj("unsubscribeAuthorPassage", "Boolean"))
                        add(fieldObj("proposeChatAlias", "ChatSession"))
                        add(fieldObj("acceptChatAlias", "ChatSession"))
                        add(fieldObj("setPassageTypeAdvertisement", "Int"))
                    }
                }
            }
        }
    }

    /**
     * Validates the serialized encrypted keystore envelope without decrypting it.
     *
     * The server is deliberately a blind ciphertext store. It may validate shape and
     * supported version, but it must never derive a password key or inspect plaintext.
     */
    private fun validateEncryptedKeyStorePayload(
        encryptedPayload: String,
        salt: String,
        iv: String,
        version: Int
    ): String? {
        if (version !in setOf(2, 3)) {
            return "Unsupported keystore envelope version: $version"
        }
        if (encryptedPayload.isBlank() || salt.isBlank() || iv.isBlank()) {
            return "Keystore envelope fields must not be blank"
        }

        return try {
            val ciphertext = java.util.Base64.getDecoder().decode(encryptedPayload)
            val saltBytes = java.util.Base64.getDecoder().decode(salt)
            val ivBytes = java.util.Base64.getDecoder().decode(iv)

            if (saltBytes.size != 16) {
                return "Invalid keystore salt length"
            }
            if (ivBytes.size != 12) {
                return "Invalid keystore nonce length"
            }
            if (ciphertext.size < 16) {
                return "Invalid keystore ciphertext length"
            }
            null
        } catch (_: IllegalArgumentException) {
            "Keystore envelope contains invalid Base64"
        }
    }

    private fun fieldObj(name: String, typeName: String, isList: Boolean = false): JsonObject {
        return buildJsonObject {
            put("name", name)
            putJsonObject("type") {
                if (isList) {
                    put("kind", "LIST")
                    putJsonObject("ofType") {
                        put("kind", "OBJECT")
                        put("name", typeName)
                    }
                } else {
                    put("kind", "OBJECT")
                    put("name", typeName)
                }
            }
        }
    }
}
