package connects.quotes.repository

import connects.quotes.model.AuthorActivityLog
import connects.quotes.model.AuthorDiscardAction
import connects.quotes.model.AuthorSettings
import connects.quotes.model.Id
import connects.quotes.model.InactivityCheckResult
import connects.quotes.util.currentTimeMillis

/**
 * Repository interface governing author activity tracking, inactivity period configuration,
 * discard actions, and server-side inactivity purge checks.
 */
interface AuthorRepository {

    /**
     * Retrieves the inactivity settings and last-active record for an author.
     */
    suspend fun getAuthorSettings(authorId: Id): AuthorSettings?

    /**
     * Updates an author's inactivity settings, period, and discard action,
     * and synchronizes the inactivity tag on the author's passage.
     */
    suspend fun saveAuthorSettings(settings: AuthorSettings): Boolean

    /**
     * Logs an activity occurrence for an author and updates their last-active timestamp.
     */
    suspend fun logAuthorActivity(authorId: Id, activityType: String): Boolean

    /**
     * Retrieves historical activity logs for an author.
     */
    suspend fun getAuthorActivityLogs(authorId: Id, limit: Int = 20): List<AuthorActivityLog>

    /**
     * Server function performing an inactivity sweep across all registered authors.
     * If an author's inactivity period is exceeded:
     * 1. Deletes all chat messages sent from the author's side.
     * 2. Executes their configured discard action on all unadvertised passages (ORPHAN or DELETE).
     */
    suspend fun performInactivityCheck(now: Long = currentTimeMillis()): InactivityCheckResult

    /**
     * Executes the author discard action on demand (e.g. when an author decides to delete their identity).
     * @param authorId The author UUID.
     * @param explicitAction Optional override for the discard action (defaults to author's configured setting).
     */
    suspend fun executeAuthorDiscard(authorId: Id, explicitAction: AuthorDiscardAction? = null): Boolean

    /**
     * Resolves an author ID from either a UUID or a unique @username.
     */
    suspend fun resolveAuthorByUsernameOrId(query: String): Id?

    /**
     * Checks if a desired username is available.
     */
    suspend fun checkUsernameAvailable(username: String, currentAuthorId: Id? = null): Boolean
}
