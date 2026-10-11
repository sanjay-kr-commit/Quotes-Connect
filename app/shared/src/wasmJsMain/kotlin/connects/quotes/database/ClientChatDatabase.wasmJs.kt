package connects.quotes.database

import connects.quotes.model.ChatMessage
import connects.quotes.model.Id

actual object ClientChatDatabase {
    private val memoryStore = mutableListOf<LocalChatRecord>()
    private val decryptedStore = mutableMapOf<String, String>()

    private data class LocalChatRecord(
        val message: ChatMessage,
        val decryptedPlaintext: String?
    )

    actual fun saveMessage(message: ChatMessage, decryptedPlaintext: String?) {
        memoryStore.removeAll { it.message.id == message.id }
        memoryStore.add(LocalChatRecord(message, decryptedPlaintext))
        if (decryptedPlaintext != null) {
            decryptedStore[message.id.value] = decryptedPlaintext
        }
    }

    actual fun saveMessages(messages: List<ChatMessage>, decryptedMap: Map<String, String>?) {
        for (m in messages) {
            saveMessage(m, decryptedMap?.get(m.id.value))
        }
    }

    actual fun getMessages(chatId: Id): List<ChatMessage> {
        return memoryStore.filter { it.message.chatId == chatId }.map { it.message }.sortedBy { it.timestamp }
    }

    actual fun getDecryptedPlaintext(messageId: Id): String? {
        return decryptedStore[messageId.value]
    }

    actual fun getAllDecryptedPlaintexts(chatId: Id): Map<String, String> {
        val messages = getMessages(chatId)
        val result = mutableMapOf<String, String>()
        for (m in messages) {
            val dec = decryptedStore[m.id.value]
            if (dec != null) result[m.id.value] = dec
        }
        return result
    }

    actual fun getOutgoingStack(chatId: Id, senderId: Id): List<ChatMessage> {
        return memoryStore.filter { it.message.chatId == chatId && it.message.senderId == senderId && it.message.isEncrypted }
            .map { it.message }
            .sortedBy { it.timestamp }
    }

    private val personalNames = mutableMapOf<String, String>()

    actual fun savePersonalName(chatId: Id, name: String) {
        personalNames[chatId.value] = name
    }

    actual fun getPersonalName(chatId: Id): String? {
        return personalNames[chatId.value]
    }

    actual fun getAllPersonalNames(): Map<String, String> {
        return personalNames.toMap()
    }

    actual fun deleteChat(chatId: Id) {
        memoryStore.removeAll { it.message.chatId == chatId }
        personalNames.remove(chatId.value)
    }

    actual fun clearChat(chatId: Id) {
        deleteChat(chatId)
    }
}
