package connects.quotes.database

import connects.quotes.model.ChatMessage
import connects.quotes.model.Id
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

actual object ClientChatDatabase {
    private val dbLock = Any()
    private val dbUrl: String by lazy {
        initDb()
    }

    private fun initDb(): String {
        Class.forName("org.sqlite.JDBC")
        val localFile = File("quotes_client_encrypted_chats.db")
        val targetFile = try {
            if (!localFile.exists()) {
                localFile.createNewFile()
            }
            localFile
        } catch (_: Exception) {
            val userHome = System.getProperty("user.home") ?: "."
            val fallbackDir = File(userHome, ".quotes_connect")
            fallbackDir.mkdirs()
            File(fallbackDir, "quotes_client_encrypted_chats.db")
        }

        val url = "jdbc:sqlite:${targetFile.canonicalPath}"
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS local_chat_messages (
                        id TEXT PRIMARY KEY,
                        chat_id TEXT NOT NULL,
                        sender_id TEXT NOT NULL,
                        recipient_id TEXT NOT NULL,
                        content TEXT NOT NULL,
                        decrypted_content TEXT,
                        is_encrypted INT NOT NULL,
                        cited_passage_id TEXT,
                        timestamp BIGINT NOT NULL
                    );
                    """.trimIndent()
                )
                stmt.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_local_chat_id ON local_chat_messages(chat_id);
                    """.trimIndent()
                )
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS chat_personal_names (
                        chat_id TEXT PRIMARY KEY,
                        personal_name TEXT NOT NULL
                    );
                    """.trimIndent()
                )
            }
        }
        return url
    }

    private fun getConnection(): Connection = DriverManager.getConnection(dbUrl)

    actual fun saveMessage(message: ChatMessage, decryptedPlaintext: String?) {
        synchronized(dbLock) {
            val sql = """
                INSERT INTO local_chat_messages (id, chat_id, sender_id, recipient_id, content, decrypted_content, is_encrypted, cited_passage_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    decrypted_content = coalesce(excluded.decrypted_content, local_chat_messages.decrypted_content),
                    content = excluded.content
            """.trimIndent()

            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, message.id.value)
                    stmt.setString(2, message.chatId.value)
                    stmt.setString(3, message.senderId.value)
                    stmt.setString(4, message.recipientId.value)
                    stmt.setString(5, message.content)
                    stmt.setString(6, decryptedPlaintext)
                    stmt.setInt(7, if (message.isEncrypted) 1 else 0)
                    stmt.setString(8, message.citedPassageId?.value)
                    stmt.setLong(9, message.timestamp)
                    stmt.executeUpdate()
                }
            }
        }
    }

    actual fun saveMessages(messages: List<ChatMessage>, decryptedMap: Map<String, String>?) {
        if (messages.isEmpty()) return
        synchronized(dbLock) {
            val sql = """
                INSERT INTO local_chat_messages (id, chat_id, sender_id, recipient_id, content, decrypted_content, is_encrypted, cited_passage_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    decrypted_content = coalesce(excluded.decrypted_content, local_chat_messages.decrypted_content),
                    content = excluded.content
            """.trimIndent()

            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(sql).use { stmt ->
                        for (msg in messages) {
                            val decrypted = decryptedMap?.get(msg.id.value)
                            stmt.setString(1, msg.id.value)
                            stmt.setString(2, msg.chatId.value)
                            stmt.setString(3, msg.senderId.value)
                            stmt.setString(4, msg.recipientId.value)
                            stmt.setString(5, msg.content)
                            stmt.setString(6, decrypted)
                            stmt.setInt(7, if (msg.isEncrypted) 1 else 0)
                            stmt.setString(8, msg.citedPassageId?.value)
                            stmt.setLong(9, msg.timestamp)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }

    actual fun getMessages(chatId: Id): List<ChatMessage> {
        synchronized(dbLock) {
            val sql = "SELECT id, chat_id, sender_id, recipient_id, content, is_encrypted, cited_passage_id, timestamp FROM local_chat_messages WHERE chat_id = ? ORDER BY timestamp ASC"
            val list = mutableListOf<ChatMessage>()
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val cited = rs.getString("cited_passage_id")
                        list.add(
                            ChatMessage(
                                id = Id(rs.getString("id")),
                                chatId = Id(rs.getString("chat_id")),
                                senderId = Id(rs.getString("sender_id")),
                                recipientId = Id(rs.getString("recipient_id")),
                                content = rs.getString("content"),
                                isEncrypted = rs.getInt("is_encrypted") == 1,
                                citedPassageId = if (cited.isNullOrBlank()) null else Id(cited),
                                timestamp = rs.getLong("timestamp")
                            )
                        )
                    }
                }
            }
            return list
        }
    }

    actual fun getDecryptedPlaintext(messageId: Id): String? {
        synchronized(dbLock) {
            val sql = "SELECT decrypted_content FROM local_chat_messages WHERE id = ?"
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, messageId.value)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        return rs.getString("decrypted_content")
                    }
                }
            }
            return null
        }
    }

    actual fun getAllDecryptedPlaintexts(chatId: Id): Map<String, String> {
        synchronized(dbLock) {
            val sql = "SELECT id, decrypted_content FROM local_chat_messages WHERE chat_id = ? AND decrypted_content IS NOT NULL"
            val map = mutableMapOf<String, String>()
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val dec = rs.getString("decrypted_content")
                        if (!dec.isNullOrBlank()) {
                            map[rs.getString("id")] = dec
                        }
                    }
                }
            }
            return map
        }
    }

    actual fun getOutgoingStack(chatId: Id, senderId: Id): List<ChatMessage> {
        synchronized(dbLock) {
            val sql = "SELECT id, chat_id, sender_id, recipient_id, content, is_encrypted, cited_passage_id, timestamp FROM local_chat_messages WHERE chat_id = ? AND sender_id = ? AND is_encrypted = 1 ORDER BY timestamp ASC"
            val list = mutableListOf<ChatMessage>()
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    stmt.setString(2, senderId.value)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val cited = rs.getString("cited_passage_id")
                        list.add(
                            ChatMessage(
                                id = Id(rs.getString("id")),
                                chatId = Id(rs.getString("chat_id")),
                                senderId = Id(rs.getString("sender_id")),
                                recipientId = Id(rs.getString("recipient_id")),
                                content = rs.getString("content"),
                                isEncrypted = true,
                                citedPassageId = if (cited.isNullOrBlank()) null else Id(cited),
                                timestamp = rs.getLong("timestamp")
                            )
                        )
                    }
                }
            }
            return list
        }
    }

    actual fun savePersonalName(chatId: Id, name: String) {
        synchronized(dbLock) {
            val sql = """
                INSERT INTO chat_personal_names (chat_id, personal_name)
                VALUES (?, ?)
                ON CONFLICT(chat_id) DO UPDATE SET personal_name = excluded.personal_name;
            """.trimIndent()
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    stmt.setString(2, name)
                    stmt.executeUpdate()
                }
            }
        }
    }

    actual fun getPersonalName(chatId: Id): String? {
        synchronized(dbLock) {
            val sql = "SELECT personal_name FROM chat_personal_names WHERE chat_id = ?"
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, chatId.value)
                    val rs = stmt.executeQuery()
                    if (rs.next()) return rs.getString("personal_name")
                }
            }
            return null
        }
    }

    actual fun getAllPersonalNames(): Map<String, String> {
        synchronized(dbLock) {
            val sql = "SELECT chat_id, personal_name FROM chat_personal_names"
            val map = mutableMapOf<String, String>()
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        map[rs.getString("chat_id")] = rs.getString("personal_name")
                    }
                }
            }
            return map
        }
    }

    actual fun deleteChat(chatId: Id) {
        synchronized(dbLock) {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM local_chat_messages WHERE chat_id = ?").use { stmt ->
                    stmt.setString(1, chatId.value)
                    stmt.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM chat_personal_names WHERE chat_id = ?").use { stmt ->
                    stmt.setString(1, chatId.value)
                    stmt.executeUpdate()
                }
            }
        }
    }

    actual fun clearChat(chatId: Id) {
        deleteChat(chatId)
    }
}
