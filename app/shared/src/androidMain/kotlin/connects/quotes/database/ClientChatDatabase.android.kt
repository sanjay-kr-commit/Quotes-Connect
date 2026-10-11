package connects.quotes.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import connects.quotes.model.ChatMessage
import connects.quotes.model.Id
import java.io.File

actual object ClientChatDatabase {
    private var appContext: Context? = null
    private val dbLock = Any()
    private var database: SQLiteDatabase? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getDb(): SQLiteDatabase {
        database?.let { if (it.isOpen) return it }
        synchronized(dbLock) {
            database?.let { if (it.isOpen) return it }
            val ctx = appContext
            val db = if (ctx != null) {
                ctx.openOrCreateDatabase("quotes_client_encrypted_chats.db", Context.MODE_PRIVATE, null)
            } else {
                val tmp = System.getProperty("java.io.tmpdir") ?: "."
                val file = File(tmp, "quotes_client_encrypted_chats.db")
                SQLiteDatabase.openOrCreateDatabase(file, null)
            }

            db.execSQL(
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
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_android_chat_id ON local_chat_messages(chat_id);")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_personal_names (
                    chat_id TEXT PRIMARY KEY,
                    personal_name TEXT NOT NULL
                );
                """.trimIndent()
            )
            database = db
            return db
        }
    }

    actual fun saveMessage(message: ChatMessage, decryptedPlaintext: String?) {
        synchronized(dbLock) {
            val db = getDb()
            val cv = ContentValues().apply {
                put("id", message.id.value)
                put("chat_id", message.chatId.value)
                put("sender_id", message.senderId.value)
                put("recipient_id", message.recipientId.value)
                put("content", message.content)
                if (decryptedPlaintext != null) {
                    put("decrypted_content", decryptedPlaintext)
                }
                put("is_encrypted", if (message.isEncrypted) 1 else 0)
                put("cited_passage_id", message.citedPassageId?.value)
                put("timestamp", message.timestamp)
            }
            db.insertWithOnConflict("local_chat_messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    actual fun saveMessages(messages: List<ChatMessage>, decryptedMap: Map<String, String>?) {
        if (messages.isEmpty()) return
        synchronized(dbLock) {
            val db = getDb()
            db.beginTransaction()
            try {
                for (msg in messages) {
                    val cv = ContentValues().apply {
                        put("id", msg.id.value)
                        put("chat_id", msg.chatId.value)
                        put("sender_id", msg.senderId.value)
                        put("recipient_id", msg.recipientId.value)
                        put("content", msg.content)
                        val dec = decryptedMap?.get(msg.id.value)
                        if (dec != null) {
                            put("decrypted_content", dec)
                        }
                        put("is_encrypted", if (msg.isEncrypted) 1 else 0)
                        put("cited_passage_id", msg.citedPassageId?.value)
                        put("timestamp", msg.timestamp)
                    }
                    db.insertWithOnConflict("local_chat_messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    actual fun getMessages(chatId: Id): List<ChatMessage> {
        synchronized(dbLock) {
            val db = getDb()
            val list = mutableListOf<ChatMessage>()
            val cursor = db.rawQuery(
                "SELECT id, chat_id, sender_id, recipient_id, content, is_encrypted, cited_passage_id, timestamp FROM local_chat_messages WHERE chat_id = ? ORDER BY timestamp ASC",
                arrayOf(chatId.value)
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    val cited = c.getString(c.getColumnIndexOrThrow("cited_passage_id"))
                    list.add(
                        ChatMessage(
                            id = Id(c.getString(c.getColumnIndexOrThrow("id"))),
                            chatId = Id(c.getString(c.getColumnIndexOrThrow("chat_id"))),
                            senderId = Id(c.getString(c.getColumnIndexOrThrow("sender_id"))),
                            recipientId = Id(c.getString(c.getColumnIndexOrThrow("recipient_id"))),
                            content = c.getString(c.getColumnIndexOrThrow("content")),
                            isEncrypted = c.getInt(c.getColumnIndexOrThrow("is_encrypted")) == 1,
                            citedPassageId = if (cited.isNullOrBlank()) null else Id(cited),
                            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
            return list
        }
    }

    actual fun getDecryptedPlaintext(messageId: Id): String? {
        synchronized(dbLock) {
            val db = getDb()
            val cursor = db.rawQuery(
                "SELECT decrypted_content FROM local_chat_messages WHERE id = ?",
                arrayOf(messageId.value)
            )
            cursor.use { c ->
                if (c.moveToNext()) {
                    val idx = c.getColumnIndex("decrypted_content")
                    if (idx != -1 && !c.isNull(idx)) {
                        return c.getString(idx)
                    }
                }
            }
            return null
        }
    }

    actual fun getAllDecryptedPlaintexts(chatId: Id): Map<String, String> {
        synchronized(dbLock) {
            val db = getDb()
            val map = mutableMapOf<String, String>()
            val cursor = db.rawQuery(
                "SELECT id, decrypted_content FROM local_chat_messages WHERE chat_id = ? AND decrypted_content IS NOT NULL",
                arrayOf(chatId.value)
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val dec = c.getString(1)
                    if (!dec.isNullOrBlank()) {
                        map[id] = dec
                    }
                }
            }
            return map
        }
    }

    actual fun getOutgoingStack(chatId: Id, senderId: Id): List<ChatMessage> {
        synchronized(dbLock) {
            val db = getDb()
            val list = mutableListOf<ChatMessage>()
            val cursor = db.rawQuery(
                "SELECT id, chat_id, sender_id, recipient_id, content, is_encrypted, cited_passage_id, timestamp FROM local_chat_messages WHERE chat_id = ? AND sender_id = ? AND is_encrypted = 1 ORDER BY timestamp ASC",
                arrayOf(chatId.value, senderId.value)
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    val cited = c.getString(c.getColumnIndexOrThrow("cited_passage_id"))
                    list.add(
                        ChatMessage(
                            id = Id(c.getString(c.getColumnIndexOrThrow("id"))),
                            chatId = Id(c.getString(c.getColumnIndexOrThrow("chat_id"))),
                            senderId = Id(c.getString(c.getColumnIndexOrThrow("sender_id"))),
                            recipientId = Id(c.getString(c.getColumnIndexOrThrow("recipient_id"))),
                            content = c.getString(c.getColumnIndexOrThrow("content")),
                            isEncrypted = true,
                            citedPassageId = if (cited.isNullOrBlank()) null else Id(cited),
                            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
            return list
        }
    }

    actual fun savePersonalName(chatId: Id, name: String) {
        synchronized(dbLock) {
            val db = getDb()
            val cv = ContentValues().apply {
                put("chat_id", chatId.value)
                put("personal_name", name)
            }
            db.insertWithOnConflict("chat_personal_names", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    actual fun getPersonalName(chatId: Id): String? {
        synchronized(dbLock) {
            val db = getDb()
            val cursor = db.rawQuery("SELECT personal_name FROM chat_personal_names WHERE chat_id = ?", arrayOf(chatId.value))
            cursor.use { c ->
                if (c.moveToFirst()) {
                    return c.getString(c.getColumnIndexOrThrow("personal_name"))
                }
            }
            return null
        }
    }

    actual fun getAllPersonalNames(): Map<String, String> {
        synchronized(dbLock) {
            val db = getDb()
            val map = mutableMapOf<String, String>()
            val cursor = db.rawQuery("SELECT chat_id, personal_name FROM chat_personal_names", null)
            cursor.use { c ->
                while (c.moveToNext()) {
                    map[c.getString(c.getColumnIndexOrThrow("chat_id"))] = c.getString(c.getColumnIndexOrThrow("personal_name"))
                }
            }
            return map
        }
    }

    actual fun deleteChat(chatId: Id) {
        synchronized(dbLock) {
            val db = getDb()
            db.delete("local_chat_messages", "chat_id = ?", arrayOf(chatId.value))
            db.delete("chat_personal_names", "chat_id = ?", arrayOf(chatId.value))
        }
    }

    actual fun clearChat(chatId: Id) {
        deleteChat(chatId)
    }
}
