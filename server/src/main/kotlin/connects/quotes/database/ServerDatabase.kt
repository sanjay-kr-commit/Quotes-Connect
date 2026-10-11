package connects.quotes.database

import java.sql.Connection
import java.sql.DriverManager
import connects.quotes.model.Id
import connects.quotes.security.PasswordHasher
import org.slf4j.LoggerFactory

/**
 * Manages database connections and schema migrations for both SQLite (Demo) and MySQL (Production).
 */
class ServerDatabase(private val config: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(ServerDatabase::class.java)

    init {
        when (config.mode) {
            DatabaseMode.DEMO -> {
                val dbFile = java.io.File(config.sqliteFilePath)
                dbFile.parentFile?.mkdirs()
                Class.forName("org.sqlite.JDBC")
                logger.info("Initialized SQLite database in process directory: ${dbFile.canonicalPath}")
            }
            DatabaseMode.PRODUCTION -> {
                Class.forName("com.mysql.cj.jdbc.Driver")
                logger.info("Initialized MySQL driver for PRODUCTION mode: ${config.jdbcUrl}")
            }
        }
        initSchema()
    }

    fun getConnection(): Connection {
        return when (config.mode) {
            DatabaseMode.DEMO -> {
                val dbFile = java.io.File(config.sqliteFilePath)
                dbFile.parentFile?.mkdirs()
                val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.canonicalPath}")
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute("PRAGMA foreign_keys = ON;")
                        stmt.execute("PRAGMA journal_mode = WAL;")
                    }
                } catch (_: Exception) {}
                conn
            }
            DatabaseMode.PRODUCTION -> DriverManager.getConnection(
                config.jdbcUrl ?: error("Missing MySQL JDBC URL"),
                config.dbUser ?: "",
                config.dbPassword ?: ""
            )
        }
    }

    private fun initSchema() {
        getConnection().use { conn ->
            val isSqlite = config.mode == DatabaseMode.DEMO
            val textType = if (isSqlite) "TEXT" else "VARCHAR(128)"
            val longTextType = if (isSqlite) "TEXT" else "LONGTEXT"

            val stmt = conn.createStatement()

            // 1. Passages table
            // Notice parent_id is server-private, never exposed to clients
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS passages (
                    id $textType PRIMARY KEY,
                    type VARCHAR(32) NOT NULL,
                    content $longTextType NOT NULL,
                    parent_id $textType,
                    is_recordable INT NOT NULL,
                    serialized_data $longTextType NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 2. Billboard table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS billboard (
                    passage_id $textType PRIMARY KEY,
                    is_orphan INT NOT NULL,
                    advertised_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 3. Global tags table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS global_tags (
                    tag_name $textType PRIMARY KEY,
                    frequency INT NOT NULL
                )
                """.trimIndent()
            )

            // Seed default aesthetic tags during clean database initialization if table is empty
            try {
                stmt.executeQuery("SELECT COUNT(*) AS cnt FROM global_tags").use { rs ->
                    if (rs.next() && rs.getInt("cnt") == 0) {
                        val defaultAestheticTags = listOf(
                            "stoicism", "philosophy", "wisdom", "sovereign", "knowledge",
                            "dag", "citation", "epistemology", "aphorism", "truth",
                            "ethics", "logic", "reason", "virtue", "mind", "literature"
                        )
                        for (tag in defaultAestheticTags) {
                            val insertSql = if (isSqlite) {
                                "INSERT OR IGNORE INTO global_tags (tag_name, frequency) VALUES ('$tag', 1)"
                            } else {
                                "INSERT IGNORE INTO global_tags (tag_name, frequency) VALUES ('$tag', 1)"
                            }
                            stmt.executeUpdate(insertSql)
                        }
                        logger.info("Seeded ${defaultAestheticTags.size} default aesthetic tags into global_tags")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Could not check/seed default tags: ${e.message}")
            }

            // 4. Subscriptions table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id $textType PRIMARY KEY,
                    source_id $textType NOT NULL,
                    target_id $textType NOT NULL,
                    created_at BIGINT NOT NULL
                )
                """.trimIndent()
            )



            // 6. Notifications table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS notifications (
                    id $textType PRIMARY KEY,
                    target_passage_id $textType NOT NULL,
                    subscriber_passage_id $textType NOT NULL,
                    subscriber_type VARCHAR(32) NOT NULL,
                    summary $longTextType NOT NULL,
                    created_at BIGINT NOT NULL,
                    is_read INT NOT NULL
                )
                """.trimIndent()
            )

            // 7. Personal Chat Sessions table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id $textType PRIMARY KEY,
                    initiator_id $textType NOT NULL,
                    recipient_id $textType NOT NULL,
                    type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    initiator_public_key $longTextType,
                    recipient_public_key $longTextType,
                    shared_alias $textType,
                    proposed_shared_alias $textType,
                    alias_proposer_id $textType,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // Safe migrations for chat_sessions columns
            try { stmt.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN shared_alias $textType") } catch (_: Throwable) {}
            try { stmt.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN proposed_shared_alias $textType") } catch (_: Throwable) {}
            try { stmt.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN alias_proposer_id $textType") } catch (_: Throwable) {}

            // 8. Unencrypted Chat Messages table
            // Sovereign Privacy: Ephemeral Encrypted messages are NEVER written to disk and only routed in-memory!
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS unencrypted_chat_messages (
                    id $textType PRIMARY KEY,
                    chat_id $textType NOT NULL,
                    sender_id $textType NOT NULL,
                    recipient_id $textType NOT NULL,
                    content $longTextType NOT NULL,
                    cited_passage_id $textType,
                    created_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 9. Encrypted Cloud Chat Messages table
            // Sovereign Privacy: Ciphertext is stored in cloud, but server has ZERO keys/access to plaintexts!
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS encrypted_cloud_chat_messages (
                    id $textType PRIMARY KEY,
                    chat_id $textType NOT NULL,
                    sender_id $textType NOT NULL,
                    recipient_id $textType NOT NULL,
                    ciphertext $longTextType NOT NULL,
                    cited_passage_id $textType,
                    created_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 10. Author Keystores table
            // Symmetric Key Cloud Backup: Private keys are encrypted client-side with user password before cloud backup.
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS author_keystores (
                    author_id $textType PRIMARY KEY,
                    encrypted_payload $longTextType NOT NULL,
                    salt $textType NOT NULL,
                    iv $textType NOT NULL,
                    version INT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 11. Author Settings & Inactivity Configuration table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS author_settings (
                    author_id $textType PRIMARY KEY,
                    last_active_at BIGINT NOT NULL,
                    inactivity_period_days INT NOT NULL,
                    discard_action VARCHAR(32) NOT NULL,
                    username $textType,
                    bio $longTextType,
                    alias_search_enabled INT NOT NULL DEFAULT 1,
                    updated_at BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // Safe migrations for author_settings columns
            try { stmt.executeUpdate("ALTER TABLE author_settings ADD COLUMN username $textType") } catch (_: Throwable) {}
            try { stmt.executeUpdate("ALTER TABLE author_settings ADD COLUMN bio $longTextType") } catch (_: Throwable) {}
            try { stmt.executeUpdate("ALTER TABLE author_settings ADD COLUMN alias_search_enabled INT NOT NULL DEFAULT 1") } catch (_: Throwable) {}

            // 12. Author Activity Logs table
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS author_activity_logs (
                    id $textType PRIMARY KEY,
                    author_id $textType NOT NULL,
                    activity_type VARCHAR(64) NOT NULL,
                    timestamp BIGINT NOT NULL
                )
                """.trimIndent()
            )



            // 14. Author Subscriptions table (Direct subscriptions to any passage without citing)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS author_subscriptions (
                    author_id $textType NOT NULL,
                    target_passage_id $textType NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (author_id, target_passage_id)
                )
                """.trimIndent()
            )

            // 15. Schema Migrations table (Deterministic, ordered migration tracking)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INT PRIMARY KEY,
                    description VARCHAR(128) NOT NULL,
                    applied_at BIGINT NOT NULL,
                    execution_ms BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // 16. Authentication Credentials table (Salted PBKDF2/Argon2 verifier records; NO raw passphrases)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS auth_credentials (
                    author_id $textType PRIMARY KEY,
                    verifier_scheme VARCHAR(32) NOT NULL,
                    verifier_version INT NOT NULL,
                    verifier_salt $textType NOT NULL,
                    verifier_hash $textType NOT NULL,
                    iterations INT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    failed_attempts INT NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // 17. Authentication Sessions table (Revocable bearer session tokens)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS auth_sessions (
                    session_id $textType PRIMARY KEY,
                    author_id $textType NOT NULL,
                    token_hash $textType NOT NULL,
                    device_id $textType,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    revoked_at BIGINT
                )
                """.trimIndent()
            )

            // 18. Security Audit Logs table (Structured audit trail with NO plaintext secrets)
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS security_audit_logs (
                    id $textType PRIMARY KEY,
                    event_type VARCHAR(64) NOT NULL,
                    actor_author_id $textType,
                    session_id $textType,
                    resource_type VARCHAR(64),
                    resource_id $textType,
                    outcome VARCHAR(32) NOT NULL,
                    details $longTextType,
                    timestamp BIGINT NOT NULL
                )
                """.trimIndent()
            )

            // Apply deterministic schema migrations & indexes
            applyMigrations(conn, isSqlite)

            logger.info("Database schema verified for mode: ${config.mode}")
        }
    }

    private fun applyMigrations(conn: Connection, isSqlite: Boolean) {
        val textType = if (isSqlite) "TEXT" else "VARCHAR(128)"
        val appliedVersions = mutableSetOf<Int>()
        try {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT version FROM schema_migrations").use { rs ->
                    while (rs.next()) {
                        appliedVersions.add(rs.getInt("version"))
                    }
                }
            }
        } catch (_: Exception) {}

        data class Migration(val version: Int, val description: String, val execute: (Connection) -> Unit)

        val migrations = listOf(
            Migration(1, "001_schema_baseline") { _ ->
                // Baseline schema tables created in initSchema
            },
            Migration(2, "002_drop_legacy_credentials") { c ->
                try {
                    val legacyRows = mutableListOf<Pair<String, String>>()
                    c.createStatement().use { s ->
                        s.executeQuery("SELECT passage_id, passphrase FROM credentials").use { rs ->
                            while (rs.next()) {
                                legacyRows.add(Pair(rs.getString("passage_id"), rs.getString("passphrase")))
                            }
                        }
                    }
                    for ((authorId, pass) in legacyRows) {
                        val verifier = PasswordHasher.hashPassphrase(Id(authorId), pass.toCharArray())
                        val insertSql = if (isSqlite) {
                            "INSERT OR IGNORE INTO auth_credentials (author_id, verifier_scheme, verifier_version, verifier_salt, verifier_hash, iterations, created_at, updated_at, failed_attempts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)"
                        } else {
                            "INSERT IGNORE INTO auth_credentials (author_id, verifier_scheme, verifier_version, verifier_salt, verifier_hash, iterations, created_at, updated_at, failed_attempts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)"
                        }
                        c.prepareStatement(insertSql).use { pstmt ->
                            pstmt.setString(1, authorId)
                            pstmt.setString(2, verifier.scheme)
                            pstmt.setInt(3, verifier.version)
                            pstmt.setString(4, verifier.saltBase64)
                            pstmt.setString(5, verifier.hashBase64)
                            pstmt.setInt(6, verifier.iterations)
                            pstmt.setLong(7, System.currentTimeMillis())
                            pstmt.setLong(8, System.currentTimeMillis())
                            pstmt.executeUpdate()
                        }
                    }
                } catch (_: Exception) {}
                try {
                    c.createStatement().use { s -> s.executeUpdate("DROP TABLE IF EXISTS credentials") }
                } catch (e: Exception) {
                    logger.warn("Could not drop legacy credentials table: ${e.message}")
                }
            },
            Migration(3, "003_remove_pending_chat_keys") { c ->
                try {
                    c.createStatement().use { s -> s.executeUpdate("DROP TABLE IF EXISTS pending_chat_keys") }
                } catch (e: Exception) {
                    logger.warn("Could not drop pending_chat_keys table: ${e.message}")
                }
            },
            Migration(4, "004_chat_mode_constraints") { c ->
                try { c.createStatement().use { it.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN shared_alias $textType") } } catch (_: Throwable) {}
                try { c.createStatement().use { it.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN proposed_shared_alias $textType") } } catch (_: Throwable) {}
                try { c.createStatement().use { it.executeUpdate("ALTER TABLE chat_sessions ADD COLUMN alias_proposer_id $textType") } } catch (_: Throwable) {}
            },
            Migration(5, "005_performance_indexes") { c ->
                val indexStatements = listOf(
                    "CREATE INDEX IF NOT EXISTS idx_passages_parent_created ON passages(parent_id, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_subscriptions_source_created ON subscriptions(source_id, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_subscriptions_target_created ON subscriptions(target_id, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_notifications_subscriber_read_created ON notifications(subscriber_passage_id, is_read, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_encrypted_cloud_messages_chat_created ON encrypted_cloud_chat_messages(chat_id, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_billboard_advertised ON billboard(advertised_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_auth_sessions_author_active_expiry ON auth_sessions(author_id, expires_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_author_activity_author_timestamp ON author_activity_logs(author_id, timestamp DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_security_audit_actor_timestamp ON security_audit_logs(actor_author_id, timestamp DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_chat_sessions_initiator_updated ON chat_sessions(initiator_id, updated_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_chat_sessions_recipient_updated ON chat_sessions(recipient_id, updated_at DESC)"
                )
                for (idxSql in indexStatements) {
                    try {
                        c.createStatement().use { it.executeUpdate(idxSql) }
                    } catch (e: Exception) {
                        logger.warn("Could not create index: $idxSql - ${e.message}")
                    }
                }
            }
        )

        for (m in migrations) {
            if (m.version !in appliedVersions) {
                val start = System.currentTimeMillis()
                m.execute(conn)
                val duration = System.currentTimeMillis() - start
                val insertMigSql = "INSERT INTO schema_migrations (version, description, applied_at, execution_ms) VALUES (?, ?, ?, ?)"
                try {
                    conn.prepareStatement(insertMigSql).use { stmt ->
                        stmt.setInt(1, m.version)
                        stmt.setString(2, m.description)
                        stmt.setLong(3, System.currentTimeMillis())
                        stmt.setLong(4, duration)
                        stmt.executeUpdate()
                    }
                    logger.info("Applied schema migration ${m.version}: ${m.description} in ${duration}ms")
                } catch (e: Exception) {
                    logger.warn("Could not record schema migration ${m.version}: ${e.message}")
                }
            }
        }
    }
}
