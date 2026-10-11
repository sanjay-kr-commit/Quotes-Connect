package connects.quotes.crypto

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PlatformEncryptedKeystoreStore {
    private const val PREFS_NAME = "quotes_connect_encrypted_keystores"
    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = false }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun context(): Context = appContext ?: error("Encrypted keystore storage is not initialized")

    private fun key(authorId: String): String = "keystore_${authorId.replace(Regex("[^A-Za-z0-9._-]"), "_")}"

    actual suspend fun save(authorId: String, blob: EncryptedKeyStoreBlob): Boolean {
        return context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(authorId), json.encodeToString(blob))
            .commit()
    }

    actual suspend fun load(authorId: String): EncryptedKeyStoreBlob? {
        val raw = context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(authorId), null)
        return raw?.let { json.decodeFromString<EncryptedKeyStoreBlob>(it) }
    }

    actual suspend fun delete(authorId: String): Boolean {
        return context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(authorId))
            .commit()
    }
}
