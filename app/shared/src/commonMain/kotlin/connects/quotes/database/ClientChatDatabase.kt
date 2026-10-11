package connects.quotes.database

import connects.quotes.model.ChatMessage
import connects.quotes.model.Id

/**
 * Sovereign Client-Side Persistent Database for Chat Messages.
 *
 * Privacy Invariant:
 * Encrypted chat messages are NEVER written to the server database.
 * To provide persistent history across client restarts, each client device
 * maintains its own local database.
 *
 * Also maintains an outgoing message stack for encrypted chats so that
 * if a counterparty loses their key (e.g. app reinstall), the sender can
 * re-encrypt and resend previous messages with the new key.
 */
expect object ClientChatDatabase {
    fun saveMessage(message: ChatMessage, decryptedPlaintext: String? = null)
    fun saveMessages(messages: List<ChatMessage>, decryptedMap: Map<String, String>? = null)
    fun getMessages(chatId: Id): List<ChatMessage>
    fun getDecryptedPlaintext(messageId: Id): String?
    fun getAllDecryptedPlaintexts(chatId: Id): Map<String, String>
    fun getOutgoingStack(chatId: Id, senderId: Id): List<ChatMessage>
    fun savePersonalName(chatId: Id, name: String)
    fun getPersonalName(chatId: Id): String?
    fun getAllPersonalNames(): Map<String, String>
    fun deleteChat(chatId: Id)
    fun clearChat(chatId: Id)
}
