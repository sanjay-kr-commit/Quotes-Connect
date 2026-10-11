package connects.quotes.model

import connects.quotes.util.currentTimeMillis
import kotlinx.serialization.Serializable

/**
 * Sovereign discard action executed when an author is inactive beyond their configured period
 * or when the author voluntarily decides to delete their identity.
 *
 * - ORPHAN: Preserves all unadvertised passages created by the author by stripping ownership
 *   (parentId = null) and placing them on the Billboard as public domain orphan knowledge.
 * - DELETE: Permanently deletes all unadvertised passages (except ledger-immutable RECORDABLE passages,
 *   which must be preserved as orphans under the ledger rule).
 */
@Serializable
enum class AuthorDiscardAction {
    ORPHAN,
    DELETE
}

/**
 * Author activity and inactivity configuration settings.
 */
@Serializable
data class AuthorSettings(
    val authorId: Id,
    val lastActiveAt: Long,
    val inactivityPeriodDays: Int = 30,
    val discardAction: AuthorDiscardAction = AuthorDiscardAction.ORPHAN,
    val username: String? = null,
    val bio: String? = null,
    val aliasSearchEnabled: Boolean = true,
    val updatedAt: Long = lastActiveAt
) {
    /**
     * Inactivity period converted to milliseconds.
     */
    val inactivityPeriodMs: Long
        get() = inactivityPeriodDays.toLong() * 24L * 60L * 60L * 1000L

    /**
     * Standard tag representing the author's inactivity period on their AuthorPassage.
     * e.g. "inactivity:30d"
     */
    val inactivityTag: String
        get() = "inactivity:${inactivityPeriodDays}d"

    /**
     * Returns true if the elapsed time since last activity exceeds the configured inactivity threshold.
     */
    fun isExpired(now: Long = currentTimeMillis()): Boolean {
        if (lastActiveAt <= 0L) return false
        return (now - lastActiveAt) > inactivityPeriodMs
    }
}

/**
 * Audit log recording each time an author becomes active.
 */
@Serializable
data class AuthorActivityLog(
    val id: String,
    val authorId: Id,
    val activityType: String,
    val timestamp: Long
)

/**
 * Results returned after the server performs an inactivity check and purge cycle.
 */
@Serializable
data class InactivityCheckResult(
    val purgedAuthorsCount: Int,
    val deletedChatsCount: Int,
    val orphanedPassagesCount: Int,
    val deletedPassagesCount: Int,
    val processedAuthorIds: List<String> = emptyList(),
    val timestamp: Long = currentTimeMillis()
)
