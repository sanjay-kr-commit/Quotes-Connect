package connects.quotes.database

import connects.quotes.model.*
import connects.quotes.repository.AuthorRepository
import connects.quotes.repository.BillboardRepository
import connects.quotes.repository.ChatRepository
import connects.quotes.repository.NotificationRepository
import connects.quotes.repository.PassageRepository
import connects.quotes.repository.TagRepository
import connects.quotes.security.AuthPrincipal
import connects.quotes.security.AuthSession
import connects.quotes.security.PasswordHasher
import connects.quotes.security.PasswordVerifierRecord
import connects.quotes.security.SecurityAuditLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust JDBC implementation of the Quotes Connect repositories.
 * Works seamlessly across both SQLite (Demo) and MySQL (Production).
 * Strictly guarantees that server-private parentId is stored safely in the database
 * but never injected into public Passage serializations.
 */
class JdbcPassageRepository(
    private val db: ServerDatabase
) : PassageRepository, BillboardRepository, TagRepository, NotificationRepository, ChatRepository, AuthorRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val logger = org.slf4j.LoggerFactory.getLogger(JdbcPassageRepository::class.java)

    // ================= PassageRepository =================

    override suspend fun get(id: Id): Passage? {
        val sql = "SELECT serialized_data FROM passages WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val raw = rs.getString("serialized_data")
                    return json.decodeFromString<Passage>(raw)
                }
            }
        }
        return null
    }

    override suspend fun getAll(limit: Int, offset: Int): List<Passage> {
        val sql = "SELECT serialized_data FROM passages ORDER BY created_at DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<Passage>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.setInt(2, offset)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(json.decodeFromString<Passage>(rs.getString("serialized_data")))
                }
            }
        }
        return list
    }

    override suspend fun getCreatedByAuthor(authorId: Id): List<Passage> {
        val sql = "SELECT serialized_data FROM passages WHERE parent_id = ? OR id = ? ORDER BY created_at DESC"
        val list = mutableListOf<Passage>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(json.decodeFromString<Passage>(rs.getString("serialized_data")))
                }
            }
        }
        return list
    }

    override suspend fun getFeed(authorId: Id, depth: Int, limit: Int, offset: Int): List<Passage> {
        val safeDepth = depth.coerceIn(1, 5)
        val initialSql = """
            SELECT target_passage_id AS tid FROM author_subscriptions WHERE author_id = ?
            UNION
            SELECT target_id AS tid FROM subscriptions WHERE source_id = ? OR source_id IN (SELECT id FROM passages WHERE parent_id = ?)
        """.trimIndent()

        var currentTargets = mutableSetOf<String>()
        db.getConnection().use { conn ->
            conn.prepareStatement(initialSql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                stmt.setString(3, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val tid = rs.getString("tid")
                    if (tid != authorId.value) {
                        currentTargets.add(tid)
                    }
                }
            }
        }

        if (currentTargets.isEmpty()) return emptyList()

        val allDiscoveredIds = currentTargets.toMutableSet()

        for (d in 1..safeDepth) {
            if (currentTargets.isEmpty()) break
            val nextTargets = mutableSetOf<String>()
            val placeholders = currentTargets.joinToString(",") { "?" }

            // 1. Children created by current targets: passages WHERE parent_id IN (...)
            val createdSql = "SELECT id FROM passages WHERE parent_id IN ($placeholders)"
            // 2. Passages attaching to current targets: subscriptions WHERE target_id IN (...)
            val citingSql = "SELECT source_id FROM subscriptions WHERE target_id IN ($placeholders)"

            db.getConnection().use { conn ->
                conn.prepareStatement(createdSql).use { stmt ->
                    var idx = 1
                    for (t in currentTargets) {
                        stmt.setString(idx++, t)
                    }
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val cid = rs.getString("id")
                        if (cid != authorId.value && allDiscoveredIds.add(cid)) {
                            nextTargets.add(cid)
                        }
                    }
                }

                conn.prepareStatement(citingSql).use { stmt ->
                    var idx = 1
                    for (t in currentTargets) {
                        stmt.setString(idx++, t)
                    }
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val sid = rs.getString("source_id")
                        if (sid != authorId.value && allDiscoveredIds.add(sid)) {
                            nextTargets.add(sid)
                        }
                    }
                }
            }
            currentTargets = nextTargets
        }

        if (allDiscoveredIds.isEmpty()) return emptyList()

        val placeholders = allDiscoveredIds.joinToString(",") { "?" }
        val fetchSql = """
            SELECT serialized_data FROM passages
            WHERE id IN ($placeholders)
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val list = mutableListOf<Passage>()
        db.getConnection().use { conn ->
            conn.prepareStatement(fetchSql).use { stmt ->
                var idx = 1
                for (id in allDiscoveredIds) {
                    stmt.setString(idx++, id)
                }
                stmt.setInt(idx++, limit)
                stmt.setInt(idx, offset)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(json.decodeFromString<Passage>(rs.getString("serialized_data")))
                }
            }
        }
        return list
    }

    override suspend fun save(passage: Passage, parentId: Id?) {
        val serialized = json.encodeToString<Passage>(passage)
        val sql = """
            INSERT INTO passages (id, type, content, parent_id, is_recordable, serialized_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                content = excluded.content,
                is_recordable = excluded.is_recordable,
                serialized_data = excluded.serialized_data,
                updated_at = excluded.updated_at
        """.trimIndent()

        // Fallback for MySQL which uses ON DUPLICATE KEY UPDATE instead of ON CONFLICT
        val query = if (isMysql()) {
            """
            INSERT INTO passages (id, type, content, parent_id, is_recordable, serialized_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                content = VALUES(content),
                is_recordable = VALUES(is_recordable),
                serialized_data = VALUES(serialized_data),
                updated_at = VALUES(updated_at)
            """.trimIndent()
        } else sql

        db.getConnection().use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, passage.id.value)
                stmt.setString(2, passage.type.name)
                stmt.setString(3, passage.content)
                stmt.setString(4, parentId?.value)
                stmt.setInt(5, if (passage.isRecordable) 1 else 0)
                stmt.setString(6, serialized)
                stmt.setLong(7, passage.createdAt)
                stmt.setLong(8, passage.updatedAt)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun delete(id: Id): Boolean {
        // Verify not recordable
        if (isRecordable(id)) return false

        db.getConnection().use { conn ->
            // Delete from billboard
            conn.prepareStatement("DELETE FROM billboard WHERE passage_id = ?").use {
                it.setString(1, id.value)
                it.executeUpdate()
            }
            // Delete subscriptions
            conn.prepareStatement("DELETE FROM subscriptions WHERE source_id = ? OR target_id = ?").use {
                it.setString(1, id.value)
                it.setString(2, id.value)
                it.executeUpdate()
            }
            // Delete passage
            conn.prepareStatement("DELETE FROM passages WHERE id = ?").use {
                it.setString(1, id.value)
                return it.executeUpdate() > 0
            }
        }
    }

    override suspend fun isRecordable(id: Id): Boolean {
        val sql = "SELECT is_recordable FROM passages WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getInt("is_recordable") == 1
                }
            }
        }
        return false
    }

    override suspend fun getParentId(id: Id): Id? {
        val sql = "SELECT parent_id FROM passages WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val raw = rs.getString("parent_id")
                    if (!raw.isNullOrBlank()) return Id(raw)
                }
            }
        }
        return null
    }

    override suspend fun search(query: String, limit: Int): List<Passage> {
        val pattern = "%${query.trim()}%"
        val sql = "SELECT serialized_data FROM passages WHERE content LIKE ? OR id LIKE ? LIMIT ?"
        val list = mutableListOf<Passage>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, pattern)
                stmt.setString(2, pattern)
                stmt.setInt(3, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(json.decodeFromString<Passage>(rs.getString("serialized_data")))
                }
            }
        }
        return list
    }

    override suspend fun getByTag(tag: String, limit: Int): List<Passage> {
        val pattern = "%\"${Tag.normalize(tag)}\"%"
        val sql = "SELECT serialized_data FROM passages WHERE serialized_data LIKE ? LIMIT ?"
        val list = mutableListOf<Passage>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, pattern)
                stmt.setInt(2, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(json.decodeFromString<Passage>(rs.getString("serialized_data")))
                }
            }
        }
        return list
    }

    override suspend fun addSubscription(sourceId: Id, targetId: Id): Boolean {
        val source = get(sourceId) ?: return false
        val target = get(targetId) ?: return false

        // Record in subscriptions table
        val edgeSql = if (isMysql()) {
            "INSERT IGNORE INTO subscriptions (id, source_id, target_id, created_at) VALUES (?, ?, ?, ?)"
        } else {
            "INSERT OR IGNORE INTO subscriptions (id, source_id, target_id, created_at) VALUES (?, ?, ?, ?)"
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(edgeSql).use { stmt ->
                stmt.setString(1, Id.generate().value)
                stmt.setString(2, sourceId.value)
                stmt.setString(3, targetId.value)
                stmt.setLong(4, System.currentTimeMillis())
                stmt.executeUpdate()
            }
        }

        // Update passage models
        val out = getSubscribedTo(sourceId)
        val inc = getSubscribers(targetId)

        val updatedSource = when (source) {
            is StandardPassage -> source.copy(subscribedToIds = out)
            is AuthorPassage -> source.copy(subscribedToIds = out)
            is QuotePassage -> source.copy(subscribedToIds = out)
            is CommentPassage -> source.copy(subscribedToIds = out)
            is RecordablePassage -> source.copy(subscribedToIds = out)
            is SourcePassage -> source.copy(subscribedToIds = out)
        }
        val sourceParent = getParentId(sourceId)
        save(updatedSource, sourceParent)

        val updatedTarget = when (target) {
            is StandardPassage -> target.copy(subscriberIds = inc)
            is AuthorPassage -> target.copy(subscriberIds = inc)
            is QuotePassage -> target.copy(subscriberIds = inc)
            is CommentPassage -> target.copy(subscriberIds = inc)
            is RecordablePassage -> target.copy(subscriberIds = inc)
            is SourcePassage -> target.copy(subscriberIds = inc)
        }
        val targetParent = getParentId(targetId)
        save(updatedTarget, targetParent)

        return true
    }

    override suspend fun removeSubscription(sourceId: Id, targetId: Id): Boolean {
        if (isRecordable(targetId) || isRecordable(sourceId)) return false

        db.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM subscriptions WHERE source_id = ? AND target_id = ?").use {
                it.setString(1, sourceId.value)
                it.setString(2, targetId.value)
                it.executeUpdate()
            }
        }
        return true
    }

    override suspend fun getSubscribedTo(id: Id): List<Id> {
        val sql = "SELECT target_id FROM subscriptions WHERE source_id = ?"
        val list = mutableListOf<Id>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(Id(rs.getString("target_id")))
                }
            }
        }
        return list
    }

    override suspend fun getSubscribers(id: Id): List<Id> {
        val sql = "SELECT source_id FROM subscriptions WHERE target_id = ?"
        val list = mutableListOf<Id>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(Id(rs.getString("source_id")))
                }
            }
        }
        return list
    }

    // ================= BillboardRepository =================

    override suspend fun getAdvertised(limit: Int, offset: Int, tag: String?): List<BillboardItem> {
        val sql = """
            SELECT b.is_orphan, b.advertised_at, p.serialized_data
            FROM billboard b
            JOIN passages p ON b.passage_id = p.id
            ORDER BY b.advertised_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val list = mutableListOf<BillboardItem>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.setInt(2, offset)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val passage = json.decodeFromString<Passage>(rs.getString("serialized_data"))
                    if (tag.isNullOrBlank() || passage.tags.any { Tag.normalize(it) == Tag.normalize(tag) }) {
                        list.add(
                            BillboardItem(
                                passage = passage,
                                isOrphan = rs.getInt("is_orphan") == 1,
                                advertisedAt = rs.getLong("advertised_at")
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    override suspend fun advertise(passageId: Id, isOrphan: Boolean): Boolean {
        val sql = if (isMysql()) {
            """
            INSERT INTO billboard (passage_id, is_orphan, advertised_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE is_orphan = VALUES(is_orphan), advertised_at = VALUES(advertised_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO billboard (passage_id, is_orphan, advertised_at)
            VALUES (?, ?, ?)
            ON CONFLICT(passage_id) DO UPDATE SET is_orphan = excluded.is_orphan, advertised_at = excluded.advertised_at
            """.trimIndent()
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, passageId.value)
                stmt.setInt(2, if (isOrphan) 1 else 0)
                stmt.setLong(3, System.currentTimeMillis())
                stmt.executeUpdate()
            }
        }
        return true
    }

    override suspend fun unadvertise(passageId: Id): Boolean {
        if (isOrphan(passageId)) return false

        db.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM billboard WHERE passage_id = ?").use { stmt ->
                stmt.setString(1, passageId.value)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun isAdvertised(passageId: Id): Boolean {
        val sql = "SELECT 1 FROM billboard WHERE passage_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, passageId.value)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    override suspend fun isOrphan(passageId: Id): Boolean {
        val sql = "SELECT is_orphan FROM billboard WHERE passage_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, passageId.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getInt("is_orphan") == 1
                }
            }
        }
        val passage = get(passageId)
        if (passage?.type == PassageType.AUTHOR) return false
        return getParentId(passageId) == null
    }

    override suspend fun getAdvertisedPassageIds(authorId: Id): Set<Id> {
        val sql = """
            SELECT b.passage_id
            FROM billboard b
            INNER JOIN passages p ON b.passage_id = p.id
            WHERE p.parent_id = ? OR p.id = ?
        """.trimIndent()
        val set = mutableSetOf<Id>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    set.add(Id(rs.getString("passage_id")))
                }
            }
        }
        return set
    }

    override suspend fun setPassageTypeAdvertisement(authorId: Id, type: PassageType?, advertise: Boolean): Int {
        val selectSql = if (type != null) {
            "SELECT id FROM passages WHERE (parent_id = ? OR id = ?) AND type = ?"
        } else {
            "SELECT id FROM passages WHERE parent_id = ? OR id = ?"
        }
        val passageIds = mutableListOf<String>()
        db.getConnection().use { conn ->
            conn.prepareStatement(selectSql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                if (type != null) {
                    stmt.setString(3, type.name)
                }
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    passageIds.add(rs.getString("id"))
                }
            }
        }

        if (passageIds.isEmpty()) return 0

        var count = 0
        val now = System.currentTimeMillis()
        db.getConnection().use { conn ->
            if (advertise) {
                val insertSql = if (isMysql()) {
                    "INSERT IGNORE INTO billboard (passage_id, is_orphan, advertised_at) VALUES (?, 0, ?)"
                } else {
                    "INSERT OR IGNORE INTO billboard (passage_id, is_orphan, advertised_at) VALUES (?, 0, ?)"
                }
                conn.prepareStatement(insertSql).use { stmt ->
                    for (pid in passageIds) {
                        stmt.setString(1, pid)
                        stmt.setLong(2, now)
                        stmt.addBatch()
                    }
                    val results = stmt.executeBatch()
                    count = results.count { it > 0 || it == java.sql.Statement.SUCCESS_NO_INFO }
                }
            } else {
                val placeholders = passageIds.joinToString(",") { "?" }
                val delSql = "DELETE FROM billboard WHERE passage_id IN ($placeholders) AND is_orphan = 0"
                conn.prepareStatement(delSql).use { stmt ->
                    for (i in passageIds.indices) {
                        stmt.setString(i + 1, passageIds[i])
                    }
                    count = stmt.executeUpdate()
                }
            }
        }
        return count
    }

    // ================= TagRepository =================

    override suspend fun recordTags(tags: List<String>) {
        if (tags.isEmpty()) return
        val sql = if (isMysql()) {
            """
            INSERT INTO global_tags (tag_name, frequency)
            VALUES (?, 1)
            ON DUPLICATE KEY UPDATE frequency = frequency + 1
            """.trimIndent()
        } else {
            """
            INSERT INTO global_tags (tag_name, frequency)
            VALUES (?, 1)
            ON CONFLICT(tag_name) DO UPDATE SET frequency = frequency + 1
            """.trimIndent()
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                for (tag in tags) {
                    val norm = Tag.normalize(tag)
                    if (norm.isNotBlank()) {
                        stmt.setString(1, norm)
                        stmt.addBatch()
                    }
                }
                stmt.executeBatch()
            }
        }
    }

    override suspend fun getTopTags(limit: Int): List<Tag> {
        val sql = "SELECT tag_name, frequency FROM global_tags ORDER BY frequency DESC LIMIT ?"
        val list = mutableListOf<Tag>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(Tag(rs.getString("tag_name"), rs.getInt("frequency")))
                }
            }
        }
        return list
    }

    override suspend fun suggestTags(prefix: String, limit: Int): List<Tag> {
        val norm = Tag.normalize(prefix)
        if (norm.isBlank()) return getTopTags(limit)

        val prefixPattern = "$norm%"
        val containsPattern = "%$norm%"
        val sql = """
            SELECT tag_name, frequency FROM global_tags
            WHERE tag_name LIKE ? OR tag_name LIKE ?
            ORDER BY CASE WHEN tag_name LIKE ? THEN 0 ELSE 1 END, frequency DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<Tag>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, prefixPattern)
                stmt.setString(2, containsPattern)
                stmt.setString(3, prefixPattern)
                stmt.setInt(4, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(Tag(rs.getString("tag_name"), rs.getInt("frequency")))
                }
            }
        }
        return list
    }

    // ================= NotificationRepository =================

    override suspend fun getNotifications(authorId: Id, limit: Int): List<Notification> {
        val sql = """
            SELECT id, target_passage_id, subscriber_passage_id, subscriber_type, summary, created_at, is_read
            FROM notifications
            WHERE target_passage_id = ? OR target_passage_id IN (SELECT id FROM passages WHERE parent_id = ?)
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<Notification>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                stmt.setInt(3, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        Notification(
                            id = Id(rs.getString("id")),
                            targetPassageId = Id(rs.getString("target_passage_id")),
                            subscriberPassageId = Id(rs.getString("subscriber_passage_id")),
                            subscriberType = PassageType.valueOf(rs.getString("subscriber_type")),
                            summary = rs.getString("summary"),
                            createdAt = rs.getLong("created_at"),
                            isRead = rs.getInt("is_read") == 1
                        )
                    )
                }
            }
        }
        return list
    }

    override suspend fun addNotification(notification: Notification) {
        val sql = """
            INSERT INTO notifications (id, target_passage_id, subscriber_passage_id, subscriber_type, summary, created_at, is_read)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, notification.id.value)
                stmt.setString(2, notification.targetPassageId.value)
                stmt.setString(3, notification.subscriberPassageId.value)
                stmt.setString(4, notification.subscriberType.name)
                stmt.setString(5, notification.summary)
                stmt.setLong(6, notification.createdAt)
                stmt.setInt(7, if (notification.isRead) 1 else 0)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun markAsRead(notificationId: Id) {
        val sql = "UPDATE notifications SET is_read = 1 WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, notificationId.value)
                stmt.executeUpdate()
            }
        }
    }

    // ================= Credentials & Authentication =================

    fun saveCredentials(credentials: Credentials) {
        val verifier = PasswordHasher.hashPassphrase(credentials.passageId, credentials.passphrase.toCharArray())
        val sql = if (isMysql()) {
            """
            INSERT INTO auth_credentials (
                author_id, verifier_scheme, verifier_version, verifier_salt, verifier_hash, iterations, created_at, updated_at, failed_attempts
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE
                verifier_salt = VALUES(verifier_salt),
                verifier_hash = VALUES(verifier_hash),
                iterations = VALUES(iterations),
                updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO auth_credentials (
                author_id, verifier_scheme, verifier_version, verifier_salt, verifier_hash, iterations, created_at, updated_at, failed_attempts
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(author_id) DO UPDATE SET
                verifier_salt = excluded.verifier_salt,
                verifier_hash = excluded.verifier_hash,
                iterations = excluded.iterations,
                updated_at = excluded.updated_at
            """.trimIndent()
        }
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, verifier.authorId.value)
                stmt.setString(2, verifier.scheme)
                stmt.setInt(3, verifier.version)
                stmt.setString(4, verifier.saltBase64)
                stmt.setString(5, verifier.hashBase64)
                stmt.setInt(6, verifier.iterations)
                stmt.setLong(7, verifier.createdAt)
                stmt.setLong(8, verifier.updatedAt)
                stmt.executeUpdate()
            }
        }
        SecurityAuditLogger.logEvent(db, "AUTH_CREDENTIALS_SAVED", credentials.passageId, outcome = "SUCCESS")
    }

    fun verifyCredentials(authorIdOrQuery: Id, passphrase: String): Boolean {
        val resolvedId = kotlinx.coroutines.runBlocking { resolveAuthorByUsernameOrId(authorIdOrQuery.value) } ?: authorIdOrQuery
        
        // 1. Check hardened auth_credentials table
        val authSql = "SELECT verifier_scheme, verifier_version, verifier_salt, verifier_hash, iterations FROM auth_credentials WHERE author_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(authSql).use { stmt ->
                stmt.setString(1, resolvedId.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val record = PasswordVerifierRecord(
                        authorId = resolvedId,
                        scheme = rs.getString("verifier_scheme"),
                        version = rs.getInt("verifier_version"),
                        saltBase64 = rs.getString("verifier_salt"),
                        hashBase64 = rs.getString("verifier_hash"),
                        iterations = rs.getInt("iterations")
                    )
                    val isValid = PasswordHasher.verifyPassphrase(passphrase.toCharArray(), record)
                    if (isValid) {
                        SecurityAuditLogger.logEvent(db, "AUTH_VERIFY_SUCCESS", resolvedId, outcome = "SUCCESS")
                        return true
                    } else {
                        SecurityAuditLogger.logEvent(db, "AUTH_VERIFY_FAILED", resolvedId, outcome = "FAILURE")
                        return false
                    }
                }
            }
        }
        SecurityAuditLogger.logEvent(db, "AUTH_VERIFY_FAILED", resolvedId, outcome = "FAILURE")
        return false
    }

    fun updatePassphrase(passageId: Id, oldPassphrase: String, newPassphrase: String): Boolean {
        val resolvedId = kotlinx.coroutines.runBlocking { resolveAuthorByUsernameOrId(passageId.value) } ?: passageId
        if (!verifyCredentials(resolvedId, oldPassphrase)) {
            return false
        }
        val verifier = PasswordHasher.hashPassphrase(resolvedId, newPassphrase.toCharArray())
        val sql = "UPDATE auth_credentials SET verifier_salt = ?, verifier_hash = ?, iterations = ?, updated_at = ? WHERE author_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, verifier.saltBase64)
                stmt.setString(2, verifier.hashBase64)
                stmt.setInt(3, verifier.iterations)
                stmt.setLong(4, verifier.updatedAt)
                stmt.setString(5, resolvedId.value)
                val rows = stmt.executeUpdate()
                SecurityAuditLogger.logEvent(db, "AUTH_PASSPHRASE_UPDATED", resolvedId, outcome = "SUCCESS")
                return rows > 0
            }
        }
    }

    // ================= Session Management =================

    fun createSession(authorId: Id, deviceId: Id? = null, ttlMs: Long = 30L * 24L * 60L * 60L * 1000L): AuthSession {
        val sessionId = Id.generate()
        val token = PasswordHasher.generateSessionToken()
        val tokenHash = PasswordHasher.hashToken(token)
        val now = System.currentTimeMillis()
        val expiresAt = now + ttlMs

        val sql = "INSERT INTO auth_sessions (session_id, author_id, token_hash, device_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId.value)
                stmt.setString(2, authorId.value)
                stmt.setString(3, tokenHash)
                stmt.setString(4, deviceId?.value)
                stmt.setLong(5, now)
                stmt.setLong(6, expiresAt)
                stmt.executeUpdate()
            }
        }
        SecurityAuditLogger.logEvent(db, "AUTH_SESSION_CREATED", authorId, sessionId, outcome = "SUCCESS")
        return AuthSession(sessionId, authorId, token, issuedAt = now, expiresAt = expiresAt)
    }

    fun validateSession(token: String): AuthPrincipal? {
        if (token.isBlank()) return null
        val tokenHash = PasswordHasher.hashToken(token.trim())
        val now = System.currentTimeMillis()
        val sql = "SELECT session_id, author_id, device_id, created_at, expires_at FROM auth_sessions WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, tokenHash)
                stmt.setLong(2, now)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val sId = rs.getString("session_id")
                    val aId = rs.getString("author_id")
                    val devId = rs.getString("device_id")
                    val issuedAt = rs.getLong("created_at")
                    val expiresAt = rs.getLong("expires_at")
                    return AuthPrincipal(
                        authorId = Id(aId),
                        sessionId = Id(sId),
                        deviceId = devId?.let { Id(it) },
                        issuedAt = issuedAt,
                        expiresAt = expiresAt
                    )
                }
            }
        }
        return null
    }

    fun revokeSession(token: String): Boolean {
        if (token.isBlank()) return false
        val tokenHash = PasswordHasher.hashToken(token.trim())
        val sql = "UPDATE auth_sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, System.currentTimeMillis())
                stmt.setString(2, tokenHash)
                val rows = stmt.executeUpdate()
                return rows > 0
            }
        }
    }

    fun revokeAllSessionsForAuthor(authorId: Id): Int {
        val sql = "UPDATE auth_sessions SET revoked_at = ? WHERE author_id = ? AND revoked_at IS NULL"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, System.currentTimeMillis())
                stmt.setString(2, authorId.value)
                return stmt.executeUpdate()
            }
        }
    }

    // ================= ChatRepository =================

    // In-memory ephemeral buffer for ENCRYPTED chat messages.
    // SOVEREIGN PRIVACY RULE: Encrypted messages are NEVER written to the persistent database!
    private val ephemeralEncryptedMailbox = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    override suspend fun createChatSession(
        initiatorId: Id,
        recipientId: Id,
        type: ChatType,
        initiatorPublicKey: String?
    ): ChatSession {
        // Enforce: person to person chat can only be three types and ONLY ONE instance of each type!
        val checkSql = """
            SELECT id, initiator_id, recipient_id, type, status, initiator_public_key, recipient_public_key, shared_alias, proposed_shared_alias, alias_proposer_id, created_at, updated_at
            FROM chat_sessions
            WHERE ((initiator_id = ? AND recipient_id = ?) OR (initiator_id = ? AND recipient_id = ?))
              AND type = ?
            LIMIT 1
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(checkSql).use { stmt ->
                stmt.setString(1, initiatorId.value)
                stmt.setString(2, recipientId.value)
                stmt.setString(3, recipientId.value)
                stmt.setString(4, initiatorId.value)
                stmt.setString(5, type.name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val existing = ChatSession(
                        id = Id(rs.getString("id")),
                        initiatorId = Id(rs.getString("initiator_id")),
                        recipientId = Id(rs.getString("recipient_id")),
                        type = ChatType.valueOf(rs.getString("type")),
                        status = ChatStatus.valueOf(rs.getString("status")),
                        initiatorPublicKey = rs.getString("initiator_public_key"),
                        recipientPublicKey = rs.getString("recipient_public_key"),
                        sharedAlias = rs.getString("shared_alias"),
                        proposedSharedAlias = rs.getString("proposed_shared_alias"),
                        aliasProposerId = rs.getString("alias_proposer_id")?.let { Id(it) },
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    )
                    // If initiator provided new key and existing session needs update
                    if (initiatorPublicKey != null && existing.initiatorId == initiatorId && existing.initiatorPublicKey != initiatorPublicKey) {
                        db.getConnection().use { conn ->
                            conn.prepareStatement("UPDATE chat_sessions SET initiator_public_key = ?, updated_at = ? WHERE id = ?").use { stmt ->
                                stmt.setString(1, initiatorPublicKey)
                                stmt.setLong(2, System.currentTimeMillis())
                                stmt.setString(3, existing.id.value)
                                stmt.executeUpdate()
                            }
                        }
                        return existing.copy(initiatorPublicKey = initiatorPublicKey)
                    }
                    return existing
                }
            }
        }

        val chatId = Id("chat-" + UUID.randomUUID().toString().take(12))
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = chatId,
            initiatorId = initiatorId,
            recipientId = recipientId,
            type = type,
            status = ChatStatus.PENDING,
            initiatorPublicKey = initiatorPublicKey,
            recipientPublicKey = null,
            sharedAlias = null,
            proposedSharedAlias = null,
            aliasProposerId = null,
            createdAt = now,
            updatedAt = now
        )

        val sql = "INSERT INTO chat_sessions (id, initiator_id, recipient_id, type, status, initiator_public_key, recipient_public_key, shared_alias, proposed_shared_alias, alias_proposer_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, session.id.value)
                stmt.setString(2, session.initiatorId.value)
                stmt.setString(3, session.recipientId.value)
                stmt.setString(4, session.type.name)
                stmt.setString(5, session.status.name)
                stmt.setString(6, session.initiatorPublicKey)
                stmt.setString(7, session.recipientPublicKey)
                stmt.setString(8, session.sharedAlias)
                stmt.setString(9, session.proposedSharedAlias)
                stmt.setString(10, session.aliasProposerId?.value)
                stmt.setLong(11, session.createdAt)
                stmt.setLong(12, session.updatedAt)
                stmt.executeUpdate()
            }
        }
        return session
    }

    override suspend fun getChatSession(chatId: Id): ChatSession? {
        val sql = "SELECT id, initiator_id, recipient_id, type, status, initiator_public_key, recipient_public_key, shared_alias, proposed_shared_alias, alias_proposer_id, created_at, updated_at FROM chat_sessions WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, chatId.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return parseChatSession(rs)
                }
            }
        }
        return null
    }

    override suspend fun getChatSessionsForAuthor(authorId: Id): List<ChatSession> {
        val sql = "SELECT id, initiator_id, recipient_id, type, status, initiator_public_key, recipient_public_key, shared_alias, proposed_shared_alias, alias_proposer_id, created_at, updated_at FROM chat_sessions WHERE initiator_id = ? OR recipient_id = ? ORDER BY updated_at DESC"
        val list = mutableListOf<ChatSession>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(parseChatSession(rs))
                }
            }
        }
        return list
    }

    override suspend fun updateChatStatus(
        chatId: Id,
        status: ChatStatus,
        recipientPublicKey: String?
    ): Boolean {
        val sql = if (recipientPublicKey != null) {
            "UPDATE chat_sessions SET status = ?, recipient_public_key = ?, updated_at = ? WHERE id = ?"
        } else {
            "UPDATE chat_sessions SET status = ?, updated_at = ? WHERE id = ?"
        }
        val now = System.currentTimeMillis()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                if (recipientPublicKey != null) {
                    stmt.setString(2, recipientPublicKey)
                    stmt.setLong(3, now)
                    stmt.setString(4, chatId.value)
                } else {
                    stmt.setLong(2, now)
                    stmt.setString(3, chatId.value)
                }
                return stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun sendChatMessage(message: ChatMessage): Boolean {
        val session = getChatSession(message.chatId) ?: return false
        if (session.status != ChatStatus.ACCEPTED) return false

        val decision = connects.quotes.security.ChatSecurityPolicy.validateSend(session, message)
        if (decision is connects.quotes.security.SecurityDecision.Rejected) {
            logger.warn("ChatSecurityPolicy rejected message: ${decision.reason}")
            return false
        }

        val now = System.currentTimeMillis()
        val updateSessionSql = "UPDATE chat_sessions SET updated_at = ? WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(updateSessionSql).use { stmt ->
                stmt.setLong(1, now)
                stmt.setString(2, message.chatId.value)
                stmt.executeUpdate()
            }
        }

        if (session.type == ChatType.ENCRYPTED) {
            // SOVEREIGN PRIVACY RULE: Strictly stored ONLY in memory. NEVER written to disk!
            val list = ephemeralEncryptedMailbox.computeIfAbsent(message.chatId.value) { mutableListOf() }
            synchronized(list) {
                list.add(message)
            }
            return true
        } else if (session.type == ChatType.ENCRYPTED_CLOUD) {
            // ENCRYPTED_CLOUD: Ciphertext stored in cloud database. Server has ZERO access to plaintexts!
            val sql = "INSERT INTO encrypted_cloud_chat_messages (id, chat_id, sender_id, recipient_id, ciphertext, cited_passage_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            db.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, message.id.value)
                    stmt.setString(2, message.chatId.value)
                    stmt.setString(3, message.senderId.value)
                    stmt.setString(4, message.recipientId.value)
                    stmt.setString(5, message.content)
                    stmt.setString(6, message.citedPassageId?.value)
                    stmt.setLong(7, message.timestamp)
                    return stmt.executeUpdate() > 0
                }
            }
        } else {
            // UNENCRYPTED: Stored in persistent database table
            val sql = "INSERT INTO unencrypted_chat_messages (id, chat_id, sender_id, recipient_id, content, cited_passage_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            db.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, message.id.value)
                    stmt.setString(2, message.chatId.value)
                    stmt.setString(3, message.senderId.value)
                    stmt.setString(4, message.recipientId.value)
                    stmt.setString(5, message.content)
                    stmt.setString(6, message.citedPassageId?.value)
                    stmt.setLong(7, message.timestamp)
                    return stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun getChatMessages(chatId: Id, authorId: Id): List<ChatMessage> {
        val session = getChatSession(chatId) ?: return emptyList()
        if (session.initiatorId != authorId && session.recipientId != authorId) {
            return emptyList()
        }

        if (session.type == ChatType.ENCRYPTED) {
            val list = ephemeralEncryptedMailbox[chatId.value]
            return if (list != null) synchronized(list) { list.toList() } else emptyList()
        } else if (session.type == ChatType.ENCRYPTED_CLOUD) {
            val sql = "SELECT id, chat_id, sender_id, recipient_id, ciphertext, cited_passage_id, created_at FROM encrypted_cloud_chat_messages WHERE chat_id = ? ORDER BY created_at ASC"
            val list = mutableListOf<ChatMessage>()
            db.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val citedId = rs.getString("cited_passage_id")
                        list.add(
                            ChatMessage(
                                id = Id(rs.getString("id")),
                                chatId = Id(rs.getString("chat_id")),
                                senderId = Id(rs.getString("sender_id")),
                                recipientId = Id(rs.getString("recipient_id")),
                                content = rs.getString("ciphertext"),
                                isEncrypted = true,
                                citedPassageId = if (citedId.isNullOrBlank()) null else Id(citedId),
                                timestamp = rs.getLong("created_at")
                            )
                        )
                    }
                }
            }
            return list
        } else {
            val sql = "SELECT id, chat_id, sender_id, recipient_id, content, cited_passage_id, created_at FROM unencrypted_chat_messages WHERE chat_id = ? ORDER BY created_at ASC"
            val list = mutableListOf<ChatMessage>()
            db.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val citedId = rs.getString("cited_passage_id")
                        list.add(
                            ChatMessage(
                                id = Id(rs.getString("id")),
                                chatId = Id(rs.getString("chat_id")),
                                senderId = Id(rs.getString("sender_id")),
                                recipientId = Id(rs.getString("recipient_id")),
                                content = rs.getString("content"),
                                isEncrypted = false,
                                citedPassageId = if (citedId.isNullOrBlank()) null else Id(citedId),
                                timestamp = rs.getLong("created_at")
                            )
                        )
                    }
                }
            }
            return list
        }
    }

    override suspend fun renewChatKey(chatId: Id, authorId: Id, newPublicKey: String): ChatSession? {
        val session = getChatSession(chatId) ?: return null
        val isInitiator = session.initiatorId == authorId
        val isRecipient = session.recipientId == authorId
        if (!isInitiator && !isRecipient) return null

        val column = if (isInitiator) "initiator_public_key" else "recipient_public_key"
        val now = System.currentTimeMillis()
        val sql = "UPDATE chat_sessions SET $column = ?, updated_at = ? WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, newPublicKey)
                stmt.setLong(2, now)
                stmt.setString(3, chatId.value)
                stmt.executeUpdate()
            }
        }
        return getChatSession(chatId)
    }

    override suspend fun backupKeyStore(keyStore: KeyStorePayload): Boolean {
        val now = if (keyStore.updatedAt > 0) keyStore.updatedAt else System.currentTimeMillis()
        val sql = if (isMysql()) {
            "INSERT INTO author_keystores (author_id, encrypted_payload, salt, iv, version, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE encrypted_payload = VALUES(encrypted_payload), salt = VALUES(salt), iv = VALUES(iv), version = VALUES(version), updated_at = VALUES(updated_at)"
        } else {
            "INSERT OR REPLACE INTO author_keystores (author_id, encrypted_payload, salt, iv, version, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
        }
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, keyStore.authorId.value)
                stmt.setString(2, keyStore.encryptedPayload)
                stmt.setString(3, keyStore.salt)
                stmt.setString(4, keyStore.iv)
                stmt.setInt(5, keyStore.version)
                stmt.setLong(6, now)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun getKeyStore(authorId: Id): KeyStorePayload? {
        val sql = "SELECT author_id, encrypted_payload, salt, iv, version, updated_at FROM author_keystores WHERE author_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return KeyStorePayload(
                        authorId = Id(rs.getString("author_id")),
                        encryptedPayload = rs.getString("encrypted_payload"),
                        salt = rs.getString("salt"),
                        iv = rs.getString("iv"),
                        version = rs.getInt("version"),
                        updatedAt = rs.getLong("updated_at")
                    )
                }
            }
        }
        return null
    }

    override suspend fun deleteKeyStore(authorId: Id): Boolean {
        val sql = "DELETE FROM author_keystores WHERE author_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun proposeSharedAlias(chatId: Id, authorId: Id, alias: String): ChatSession? {
        val session = getChatSession(chatId) ?: return null
        if (session.initiatorId != authorId && session.recipientId != authorId) return null
        val now = System.currentTimeMillis()
        val sql = "UPDATE chat_sessions SET proposed_shared_alias = ?, alias_proposer_id = ?, updated_at = ? WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, alias.trim())
                stmt.setString(2, authorId.value)
                stmt.setLong(3, now)
                stmt.setString(4, chatId.value)
                stmt.executeUpdate()
            }
        }
        return getChatSession(chatId)
    }

    override suspend fun acceptSharedAlias(chatId: Id, authorId: Id): ChatSession? {
        val session = getChatSession(chatId) ?: return null
        if (session.initiatorId != authorId && session.recipientId != authorId) return null
        if (session.aliasProposerId == authorId || session.proposedSharedAlias.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val sql = "UPDATE chat_sessions SET shared_alias = ?, proposed_shared_alias = NULL, alias_proposer_id = NULL, updated_at = ? WHERE id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, session.proposedSharedAlias)
                stmt.setLong(2, now)
                stmt.setString(3, chatId.value)
                stmt.executeUpdate()
            }
        }
        return getChatSession(chatId)
    }

    override suspend fun deleteChatSession(chatId: Id, authorId: Id): Boolean {
        val session = getChatSession(chatId) ?: return false
        if (session.initiatorId != authorId && session.recipientId != authorId) {
            return false
        }
        db.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM unencrypted_chat_messages WHERE chat_id = ?").use { stmt ->
                stmt.setString(1, chatId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM encrypted_cloud_chat_messages WHERE chat_id = ?").use { stmt ->
                stmt.setString(1, chatId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM chat_sessions WHERE id = ?").use { stmt ->
                stmt.setString(1, chatId.value)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ================= Author Subscriptions =================

    fun addAuthorSubscription(authorId: Id, targetPassageId: Id): Boolean {
        val sql = if (isMysql()) {
            "INSERT INTO author_subscriptions (author_id, target_passage_id, created_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE created_at = VALUES(created_at)"
        } else {
            "INSERT OR IGNORE INTO author_subscriptions (author_id, target_passage_id, created_at) VALUES (?, ?, ?)"
        }
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, targetPassageId.value)
                stmt.setLong(3, System.currentTimeMillis())
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun removeAuthorSubscription(authorId: Id, targetPassageId: Id): Boolean {
        val sql = "DELETE FROM author_subscriptions WHERE author_id = ? AND target_passage_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, targetPassageId.value)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun getAuthorSubscriptions(authorId: Id): List<Id> {
        val sql = "SELECT target_passage_id FROM author_subscriptions WHERE author_id = ? ORDER BY created_at DESC"
        val list = mutableListOf<Id>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(Id(rs.getString("target_passage_id")))
                }
            }
        }
        return list
    }

    fun isAuthorSubscribed(authorId: Id, targetPassageId: Id): Boolean {
        val sql = "SELECT 1 FROM author_subscriptions WHERE author_id = ? AND target_passage_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, targetPassageId.value)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    private fun parseChatSession(rs: ResultSet): ChatSession {
        val proposerId = rs.getString("alias_proposer_id")
        return ChatSession(
            id = Id(rs.getString("id")),
            initiatorId = Id(rs.getString("initiator_id")),
            recipientId = Id(rs.getString("recipient_id")),
            type = ChatType.valueOf(rs.getString("type")),
            status = ChatStatus.valueOf(rs.getString("status")),
            initiatorPublicKey = rs.getString("initiator_public_key"),
            recipientPublicKey = rs.getString("recipient_public_key"),
            sharedAlias = rs.getString("shared_alias"),
            proposedSharedAlias = rs.getString("proposed_shared_alias"),
            aliasProposerId = if (proposerId.isNullOrBlank()) null else Id(proposerId),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    // ================= AuthorRepository =================

    override suspend fun getAuthorSettings(authorId: Id): AuthorSettings? {
        val sql = "SELECT author_id, last_active_at, inactivity_period_days, discard_action, username, bio, alias_search_enabled, updated_at FROM author_settings WHERE author_id = ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val actionStr = rs.getString("discard_action")
                    val action = try { AuthorDiscardAction.valueOf(actionStr) } catch (_: Exception) { AuthorDiscardAction.ORPHAN }
                    return AuthorSettings(
                        authorId = Id(rs.getString("author_id")),
                        lastActiveAt = rs.getLong("last_active_at"),
                        inactivityPeriodDays = rs.getInt("inactivity_period_days"),
                        discardAction = action,
                        username = rs.getString("username"),
                        bio = rs.getString("bio"),
                        aliasSearchEnabled = rs.getInt("alias_search_enabled") == 1,
                        updatedAt = rs.getLong("updated_at")
                    )
                }
            }
        }
        return AuthorSettings(
            authorId = authorId,
            lastActiveAt = System.currentTimeMillis(),
            inactivityPeriodDays = 30,
            discardAction = AuthorDiscardAction.ORPHAN,
            username = null,
            bio = null,
            aliasSearchEnabled = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun saveAuthorSettings(settings: AuthorSettings): Boolean {
        val now = System.currentTimeMillis()
        val aliasFlag = if (settings.aliasSearchEnabled) 1 else 0
        val sql = if (isMysql()) {
            """
            INSERT INTO author_settings (author_id, last_active_at, inactivity_period_days, discard_action, username, bio, alias_search_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_active_at = VALUES(last_active_at),
                inactivity_period_days = VALUES(inactivity_period_days),
                discard_action = VALUES(discard_action),
                username = VALUES(username),
                bio = VALUES(bio),
                alias_search_enabled = VALUES(alias_search_enabled),
                updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO author_settings (author_id, last_active_at, inactivity_period_days, discard_action, username, bio, alias_search_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(author_id) DO UPDATE SET
                last_active_at = excluded.last_active_at,
                inactivity_period_days = excluded.inactivity_period_days,
                discard_action = excluded.discard_action,
                username = excluded.username,
                bio = excluded.bio,
                alias_search_enabled = excluded.alias_search_enabled,
                updated_at = excluded.updated_at
            """.trimIndent()
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, settings.authorId.value)
                stmt.setLong(2, settings.lastActiveAt)
                stmt.setInt(3, settings.inactivityPeriodDays)
                stmt.setString(4, settings.discardAction.name)
                stmt.setString(5, settings.username)
                stmt.setString(6, settings.bio)
                stmt.setInt(7, aliasFlag)
                stmt.setLong(8, now)
                stmt.executeUpdate()
            }
        }

        // Synchronize inactivity tag on Author passage if present
        val passage = get(settings.authorId)
        if (passage is AuthorPassage) {
            val updatedTags = passage.tags.filterNot { it.startsWith("inactivity:") }.toMutableList()
            updatedTags.add(settings.inactivityTag)
            val updatedPassage = passage.copy(tags = updatedTags)
            save(updatedPassage, getParentId(settings.authorId))
            recordTags(listOf(settings.inactivityTag))
        }
        return true
    }

    override suspend fun resolveAuthorByUsernameOrId(query: String): Id? {
        val clean = query.trim().removePrefix("@")
        if (clean.isBlank()) return null

        // 1. Check exact UUID (Sovereign UUID is always valid for direct lookups)
        val byId = get(Id(clean))
        if (byId is AuthorPassage) {
            return byId.id
        }

        // 2. Check author_settings.username (only if alias search enabled)
        val sqlUser = "SELECT author_id, alias_search_enabled FROM author_settings WHERE LOWER(username) = LOWER(?)"
        db.getConnection().use { conn ->
            conn.prepareStatement(sqlUser).use { stmt ->
                stmt.setString(1, clean)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val enabled = rs.getInt("alias_search_enabled") == 1
                    if (enabled) {
                        return Id(rs.getString("author_id"))
                    } else {
                        return null
                    }
                }
            }
        }

        // 3. Check author moniker in passages (only if alias search enabled)
        val sqlMoniker = """
            SELECT p.id, COALESCE(s.alias_search_enabled, 1) AS alias_search_enabled
            FROM passages p
            LEFT JOIN author_settings s ON p.id = s.author_id
            WHERE p.type = 'AUTHOR' AND LOWER(p.content) = LOWER(?)
        """.trimIndent()
        db.getConnection().use { conn ->
            conn.prepareStatement(sqlMoniker).use { stmt ->
                stmt.setString(1, clean)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val enabled = rs.getInt("alias_search_enabled") == 1
                    if (enabled) {
                        return Id(rs.getString("id"))
                    } else {
                        return null
                    }
                }
            }
        }
        return null
    }

    override suspend fun checkUsernameAvailable(username: String, currentAuthorId: Id?): Boolean {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val sql = "SELECT author_id FROM author_settings WHERE LOWER(username) = LOWER(?)"
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, clean)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val existing = rs.getString("author_id")
                    return currentAuthorId != null && existing == currentAuthorId.value
                }
            }
        }
        return true
    }

    override suspend fun logAuthorActivity(authorId: Id, activityType: String): Boolean {
        val now = System.currentTimeMillis()
        val upsertSql = if (isMysql()) {
            """
            INSERT INTO author_settings (author_id, last_active_at, inactivity_period_days, discard_action, updated_at)
            VALUES (?, ?, 30, 'ORPHAN', ?)
            ON DUPLICATE KEY UPDATE last_active_at = VALUES(last_active_at), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO author_settings (author_id, last_active_at, inactivity_period_days, discard_action, updated_at)
            VALUES (?, ?, 30, 'ORPHAN', ?)
            ON CONFLICT(author_id) DO UPDATE SET last_active_at = excluded.last_active_at, updated_at = excluded.updated_at
            """.trimIndent()
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(upsertSql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setLong(2, now)
                stmt.setLong(3, now)
                stmt.executeUpdate()
            }

            val logSql = "INSERT INTO author_activity_logs (id, author_id, activity_type, timestamp) VALUES (?, ?, ?, ?)"
            conn.prepareStatement(logSql).use { stmt ->
                stmt.setString(1, "act-" + UUID.randomUUID().toString().take(12))
                stmt.setString(2, authorId.value)
                stmt.setString(3, activityType)
                stmt.setLong(4, now)
                stmt.executeUpdate()
            }
        }
        return true
    }

    override suspend fun getAuthorActivityLogs(authorId: Id, limit: Int): List<AuthorActivityLog> {
        val sql = "SELECT id, author_id, activity_type, timestamp FROM author_activity_logs WHERE author_id = ? ORDER BY timestamp DESC LIMIT ?"
        val list = mutableListOf<AuthorActivityLog>()
        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setInt(2, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        AuthorActivityLog(
                            id = rs.getString("id"),
                            authorId = Id(rs.getString("author_id")),
                            activityType = rs.getString("activity_type"),
                            timestamp = rs.getLong("timestamp")
                        )
                    )
                }
            }
        }
        return list
    }

    override suspend fun performInactivityCheck(currentTimeMillis: Long): InactivityCheckResult {
        val sql = "SELECT author_id, last_active_at, inactivity_period_days, discard_action FROM author_settings"
        val candidates = mutableListOf<AuthorSettings>()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val actionStr = rs.getString("discard_action")
                    val action = try { AuthorDiscardAction.valueOf(actionStr) } catch (_: Exception) { AuthorDiscardAction.ORPHAN }
                    candidates.add(
                        AuthorSettings(
                            authorId = Id(rs.getString("author_id")),
                            lastActiveAt = rs.getLong("last_active_at"),
                            inactivityPeriodDays = rs.getInt("inactivity_period_days"),
                            discardAction = action
                        )
                    )
                }
            }
        }

        var purgedAuthors = 0
        var deletedChats = 0
        var orphanedPassages = 0
        var deletedPassages = 0
        val processedIds = mutableListOf<String>()

        for (settings in candidates) {
            if (settings.isExpired(currentTimeMillis)) {
                processedIds.add(settings.authorId.value)
                purgedAuthors++

                // Count chats before purge
                val chatCountSql = "SELECT COUNT(*) FROM unencrypted_chat_messages WHERE sender_id = ?"
                db.getConnection().use { conn ->
                    conn.prepareStatement(chatCountSql).use { stmt ->
                        stmt.setString(1, settings.authorId.value)
                        val rs = stmt.executeQuery()
                        if (rs.next()) deletedChats += rs.getInt(1)
                    }
                    val cloudCountSql = "SELECT COUNT(*) FROM encrypted_cloud_chat_messages WHERE sender_id = ?"
                    conn.prepareStatement(cloudCountSql).use { stmt ->
                        stmt.setString(1, settings.authorId.value)
                        val rs = stmt.executeQuery()
                        if (rs.next()) deletedChats += rs.getInt(1)
                    }
                }

                // Count orphaned vs deleted passages
                val ownedPassageIds = mutableListOf<Id>()
                val sqlOwned = "SELECT id FROM passages WHERE parent_id = ? AND id != ?"
                db.getConnection().use { conn ->
                    conn.prepareStatement(sqlOwned).use { stmt ->
                        stmt.setString(1, settings.authorId.value)
                        stmt.setString(2, settings.authorId.value)
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            ownedPassageIds.add(Id(rs.getString("id")))
                        }
                    }
                }
                for (pId in ownedPassageIds) {
                    val isAdv = isAdvertised(pId)
                    val recordable = isRecordable(pId)
                    if (!isAdv) {
                        if (settings.discardAction == AuthorDiscardAction.ORPHAN || recordable) {
                            orphanedPassages++
                        } else {
                            deletedPassages++
                        }
                    } else {
                        if (settings.discardAction == AuthorDiscardAction.ORPHAN || recordable) {
                            orphanedPassages++
                        }
                    }
                }

                executeAuthorDiscard(settings.authorId, settings.discardAction)
            }
        }

        return InactivityCheckResult(
            purgedAuthorsCount = purgedAuthors,
            deletedChatsCount = deletedChats,
            orphanedPassagesCount = orphanedPassages,
            deletedPassagesCount = deletedPassages,
            processedAuthorIds = processedIds,
            timestamp = currentTimeMillis
        )
    }

    override suspend fun executeAuthorDiscard(authorId: Id, explicitAction: AuthorDiscardAction?): Boolean {
        val currentSettings = getAuthorSettings(authorId)
        val action = explicitAction ?: currentSettings?.discardAction ?: AuthorDiscardAction.ORPHAN

        // 1. Delete chats from author's side
        db.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM unencrypted_chat_messages WHERE sender_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM encrypted_cloud_chat_messages WHERE sender_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
        }
        // In-memory mailboxes cleanup
        ephemeralEncryptedMailbox.values.forEach { list ->
            synchronized(list) {
                list.removeAll { it.senderId == authorId }
            }
        }

        // 2. Query all passages created by author
        val ownedPassageIds = mutableListOf<Id>()
        val sqlOwned = "SELECT id FROM passages WHERE parent_id = ? AND id != ?"
        db.getConnection().use { conn ->
            conn.prepareStatement(sqlOwned).use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.setString(2, authorId.value)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    ownedPassageIds.add(Id(rs.getString("id")))
                }
            }
        }

        for (pId in ownedPassageIds) {
            val isAdv = isAdvertised(pId)
            val recordable = isRecordable(pId)

            if (!isAdv) {
                // Unadvertised passages
                if (action == AuthorDiscardAction.ORPHAN || recordable) {
                    // Stripped of ownership and advertised as public domain orphan knowledge on Billboard
                    db.getConnection().use { conn ->
                        conn.prepareStatement("UPDATE passages SET parent_id = NULL WHERE id = ?").use { stmt ->
                            stmt.setString(1, pId.value)
                            stmt.executeUpdate()
                        }
                    }
                    advertise(pId, isOrphan = true)
                } else {
                    // Permanent Delete
                    delete(pId)
                }
            } else {
                // Already advertised passages
                if (action == AuthorDiscardAction.ORPHAN || recordable) {
                    db.getConnection().use { conn ->
                        conn.prepareStatement("UPDATE passages SET parent_id = NULL WHERE id = ?").use { stmt ->
                            stmt.setString(1, pId.value)
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement("UPDATE billboard SET is_orphan = 1 WHERE passage_id = ?").use { stmt ->
                            stmt.setString(1, pId.value)
                            stmt.executeUpdate()
                        }
                    }
                } else {
                    delete(pId)
                }
            }
        }

        // 3. Cleanup author credentials, keystore, settings, and logs
        db.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM auth_credentials WHERE author_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM author_keystores WHERE author_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM author_settings WHERE author_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM author_activity_logs WHERE author_id = ?").use { stmt ->
                stmt.setString(1, authorId.value)
                stmt.executeUpdate()
            }
        }

        if (action == AuthorDiscardAction.DELETE) {
            delete(authorId)
        }
        return true
    }

    private fun isMysql(): Boolean {
        return try {
            db.getConnection().use { it.metaData.databaseProductName.contains("MySQL", ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }
}
