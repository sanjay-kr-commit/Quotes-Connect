package connects.quotes.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual object PlatformEncryptedKeystoreStore {
    private val json = Json { ignoreUnknownKeys = false }

    private fun directory(): File = File(System.getProperty("user.home") ?: ".", ".quotes_connect/keystore").also {
        if (!it.exists() && !it.mkdirs() && !it.exists()) {
            throw IllegalStateException("Unable to create encrypted keystore directory")
        }
    }

    private fun safeId(authorId: String): String = authorId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun file(authorId: String): File = File(directory(), "${safeId(authorId)}.blob")

    actual suspend fun save(authorId: String, blob: EncryptedKeyStoreBlob): Boolean {
        val target = file(authorId)
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        return try {
            temp.writeText(json.encodeToString(blob))
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    actual suspend fun load(authorId: String): EncryptedKeyStoreBlob? {
        val target = file(authorId)
        if (!target.exists()) return null
        return json.decodeFromString<EncryptedKeyStoreBlob>(target.readText())
    }

    actual suspend fun delete(authorId: String): Boolean {
        val target = file(authorId)
        return !target.exists() || target.delete()
    }
}
