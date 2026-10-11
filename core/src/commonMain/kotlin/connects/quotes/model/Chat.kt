package connects.quotes.model

import kotlinx.serialization.Serializable

/**
 * Types of personal chat channels.
 * - ENCRYPTED: End-to-end asymmetric encryption. Keys stored locally on user devices.
 *   Encrypted messages are NEVER stored in the server database (ephemeral in-memory routing only).
 * - UNENCRYPTED: Standard plain messages. Stored in server database for persistent history.
 * - ENCRYPTED_CLOUD: Cloud-backed encrypted chat. Ciphertext stored in server database.
 *   Private keys encrypted symmetrically with user password and backed up on server.
 */
@Serializable
enum class ChatType {
    ENCRYPTED,
    UNENCRYPTED,
    ENCRYPTED_CLOUD
}

/**
 * Encrypted keystore payload backed up to the server.
 * The server stores only ciphertext (AES-256-GCM); it has zero knowledge of the user password.
 */
@Serializable
data class KeyStorePayload(
    val authorId: Id,
    val encryptedPayload: String, // Ciphertext of private key
    val salt: String,            // Salt used for PBKDF2
    val iv: String,              // IV used for AES-GCM
    val version: Int = 1,
    val updatedAt: Long = 0L
)

/**
 * State of a personal chat handshake.
 */
@Serializable
enum class ChatStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CLOSED
}

/**
 * Represents a personal chat session between two sovereign authors.
 */
@Serializable
data class ChatSession(
    val id: Id,
    val initiatorId: Id,
    val recipientId: Id,
    val type: ChatType,
    val status: ChatStatus,
    val initiatorPublicKey: String? = null,
    val recipientPublicKey: String? = null,
    val sharedAlias: String? = null,
    val proposedSharedAlias: String? = null,
    val aliasProposerId: Id? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Represents a pending key in the relay buffer waiting for counterparty delivery.
 */
@Serializable
data class PendingChatKey(
    val id: String,
    val recipientId: Id,
    val chatId: Id,
    val senderId: Id,
    val publicKey: String,
    val timestamp: Long
)

/**
 * Represents a private message (private citation) exchanged within a chat session.
 * For ENCRYPTED chats:
 * - Content is asymmetrically encrypted ciphertext (e.g. Base64-encoded).
 * - Never written to the server database (routed ephemerally in-memory).
 * For UNENCRYPTED chats:
 * - Content is plain text.
 * - Stored in the server database.
 * For ENCRYPTED_CLOUD chats:
 * - Content is asymmetrically encrypted ciphertext.
 * - Persisted in the server database (ciphertext only; server has no decryption key).
 */
@Serializable
data class ChatMessage(
    val id: Id,
    val chatId: Id,
    val senderId: Id,
    val recipientId: Id,
    val content: String,
    val isEncrypted: Boolean,
    val citedPassageId: Id? = null,
    val timestamp: Long
)

/**
 * Intent codes for granular, intent-based TCP reactive streaming.
 * Allows client tabs and windows to register interest in specific updates
 * and update UI elements granularly without whole-page refresh.
 */
object ChatIntentCode {
    const val STATUS_CHANGED = 1     // Session approved, rejected, or requested
    const val MESSAGE = 2            // New message in conversation
    const val TYPING = 3             // Typing indicator
    const val CHAT_DELETED = 4       // Conversation deleted
    const val NOTIFICATION = 5       // In-app notification event
    const val GRAPH_CHANGED = 6      // Billboard/feed graph delta
    const val ALIAS_CHANGED = 7      // Alias proposed or accepted
    const val KEY_CHANGED = 8        // Public key renewal
    const val PING = 9               // Heartbeat keepalive
    const val RESYNC_REQUEST = 10    // Client resync request

    fun fromType(type: String): Int = when (type) {
        "STATUS_CHANGED" -> STATUS_CHANGED
        "MESSAGE" -> MESSAGE
        "TYPING" -> TYPING
        "CHAT_DELETED" -> CHAT_DELETED
        "NOTIFICATION" -> NOTIFICATION
        "GRAPH_CHANGED" -> GRAPH_CHANGED
        "ALIAS_PROPOSAL", "ALIAS_ACCEPTED" -> ALIAS_CHANGED
        "KEY_CHANGED" -> KEY_CHANGED
        "PING" -> PING
        "RESYNC_REQUEST" -> RESYNC_REQUEST
        else -> 0
    }
}

/**
 * Real-time event packet transmitted across the live chat TCP streaming connection and global SSE events.
 */
@Serializable
data class LiveChatPacket(
    val type: String, // "CONNECTED", "MESSAGE", "KEY_CHANGED", "STATUS_CHANGED", "NOTIFICATION", "PING", "TYPING", "RESYNC_REQUEST", "ALIAS_PROPOSAL", "ALIAS_ACCEPTED", "GRAPH_CHANGED"
    val intentCode: Int = ChatIntentCode.fromType(type),
    val connectionId: String? = null,
    val chatId: String? = null,
    val message: ChatMessage? = null,
    val session: ChatSession? = null,
    val newPublicKey: String? = null,
    val senderId: String? = null,
    val text: String? = null,
    val alias: String? = null,
    val entityType: String? = null,
    val isTyping: Boolean? = null,
    val timestamp: Long = 0L
)

@Serializable
enum class KeyExchangePacketType {
    HELLO,
    PUBLIC_KEY,
    KEY_CONFIRM,
    ABORT,
    WAITING_FOR_PEER,
    PEER_CONNECTED,
    COMPLETE
}

@Serializable
data class KeyExchangePacket(
    val protocolVersion: Int = 1,
    val sessionId: String,
    val sequence: Long = 0L,
    val type: KeyExchangePacketType,
    val payload: String = "", 
    val senderId: String = "",
    val recipientId: String = "",
    val createdAt: Long = 0L
)
