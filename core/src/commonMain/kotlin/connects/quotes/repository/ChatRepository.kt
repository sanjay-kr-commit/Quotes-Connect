package connects.quotes.repository

import connects.quotes.model.ChatMessage
import connects.quotes.model.ChatSession
import connects.quotes.model.ChatStatus
import connects.quotes.model.ChatType
import connects.quotes.model.Id

/**
 * Repository interface for managing personal chat sessions and private citation messages.
 *
 * Sovereign Privacy Guarantees:
 * - Unencrypted chat messages are persisted in storage.
 * - Encrypted chat messages are NEVER written to the persistent database. They are held
 *   only in-memory ephemerally until delivered to the recipient client.
 */
interface ChatRepository {

    suspend fun createChatSession(
        initiatorId: Id,
        recipientId: Id,
        type: ChatType,
        initiatorPublicKey: String? = null
    ): ChatSession

    suspend fun getChatSession(chatId: Id): ChatSession?

    suspend fun getChatSessionsForAuthor(authorId: Id): List<ChatSession>

    suspend fun updateChatStatus(
        chatId: Id,
        status: ChatStatus,
        recipientPublicKey: String? = null
    ): Boolean

    /**
     * Sends a private citation message.
     * If type is ENCRYPTED, it MUST NOT be written to the database.
     */
    suspend fun sendChatMessage(message: ChatMessage): Boolean

    /**
     * Retrieves messages for a chat session.
     * If ENCRYPTED, retrieves and drains ephemeral in-memory messages.
     * If UNENCRYPTED, retrieves persistent messages from storage.
     */
    suspend fun getChatMessages(chatId: Id, authorId: Id): List<ChatMessage>

    /**
     * Renews/rotates the public key for an author in an encrypted chat session.
     * Used when an author reinstalls the client and needs a new key exchange.
     */
    suspend fun renewChatKey(chatId: Id, authorId: Id, newPublicKey: String): ChatSession?

    /**
     * Backs up a client-encrypted keystore payload to the cloud.
     */
    suspend fun backupKeyStore(keyStore: connects.quotes.model.KeyStorePayload): Boolean

    /**
     * Retrieves the client-encrypted keystore payload from the cloud.
     */
    suspend fun getKeyStore(authorId: Id): connects.quotes.model.KeyStorePayload?

    /**
     * Deletes the keystore payload from the cloud (for keystore reset).
     */
    suspend fun deleteKeyStore(authorId: Id): Boolean

    /**
     * Proposes a shared alias for a chat session.
     */
    suspend fun proposeSharedAlias(chatId: Id, authorId: Id, alias: String): ChatSession?

    /**
     * Accepts a proposed shared alias for a chat session.
     */
    suspend fun acceptSharedAlias(chatId: Id, authorId: Id): ChatSession?

    /**
     * Deletes a chat session and associated messages.
     */
    suspend fun deleteChatSession(chatId: Id, authorId: Id): Boolean
}
