package connects.quotes.security

import connects.quotes.database.ServerDatabase
import connects.quotes.model.Id
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Audit logger enforcing SECURITY RULE KE-001 and defensive sanitization.
 * BANS logging of private keys, passphrases, secrets, ciphertexts, signatures, nonces, and keystore payloads.
 */
object SecurityAuditLogger {
    private val logger = LoggerFactory.getLogger(SecurityAuditLogger::class.java)

    private val SENSITIVE_PATTERN = Regex(
        "(?i)(privateKey|secret|passphrase|password|ciphertext|plaintext|publicKey|signature|nonce|ephemeral|keystorePayload)[^&=,;\\s]*[=:]\\s*([^\\s,;\"'}&]+)"
    )

    fun sanitize(input: String?): String? {
        if (input == null) return null
        return SENSITIVE_PATTERN.replace(input) { matchResult ->
            val field = matchResult.groupValues[1]
            "$field=[REDACTED]"
        }
    }

    fun logEvent(
        db: ServerDatabase,
        eventType: String,
        actorAuthorId: Id? = null,
        sessionId: Id? = null,
        resourceType: String? = null,
        resourceId: String? = null,
        outcome: String = "SUCCESS",
        details: String? = null
    ) {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sanitizedDetails = sanitize(details)

        logger.info("SECURITY_EVENT id=$eventId type=$eventType actor=${actorAuthorId?.value ?: "ANON"} outcome=$outcome resource=$resourceType:$resourceId")

        try {
            db.getConnection().use { conn ->
                val sql = """
                    INSERT INTO security_audit_logs (
                        id, event_type, actor_author_id, session_id, resource_type, resource_id, outcome, details, timestamp
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, eventId)
                    stmt.setString(2, eventType)
                    stmt.setString(3, actorAuthorId?.value)
                    stmt.setString(4, sessionId?.value)
                    stmt.setString(5, resourceType)
                    stmt.setString(6, resourceId)
                    stmt.setString(7, outcome)
                    stmt.setString(8, sanitizedDetails)
                    stmt.setLong(9, now)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not persist security audit log to database: ${e.message}")
        }
    }
}
