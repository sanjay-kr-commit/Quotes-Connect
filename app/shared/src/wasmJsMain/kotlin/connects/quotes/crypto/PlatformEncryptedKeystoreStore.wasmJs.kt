package connects.quotes.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PlatformEncryptedKeystoreStore {
    private const val PREFIX = "qc_encrypted_keystore_"
    private val json = Json { ignoreUnknownKeys = false }

    private fun key(authorId: String): String = PREFIX + authorId

    actual suspend fun save(authorId: String, blob: EncryptedKeyStoreBlob): Boolean {
        val storageKey = key(authorId)
        val encoded = json.encodeToString(blob)
        js("window.localStorage.setItem(storageKey, encoded)")
        return true
    }

    actual suspend fun load(authorId: String): EncryptedKeyStoreBlob? {
        val storageKey = key(authorId)
        val raw: String? = js("window.localStorage.getItem(storageKey)") as? String
        return raw?.let { json.decodeFromString<EncryptedKeyStoreBlob>(it) }
    }

    actual suspend fun delete(authorId: String): Boolean {
        val storageKey = key(authorId)
        js("window.localStorage.removeItem(storageKey)")
        return true
    }
}
