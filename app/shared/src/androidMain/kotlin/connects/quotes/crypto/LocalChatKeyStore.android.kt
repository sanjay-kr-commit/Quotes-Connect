package connects.quotes.crypto

import kotlinx.coroutines.sync.withLock

private val localChatKeyPairs = mutableMapOf<String, CryptoKeyPair>()
private val corruptAuthors = mutableSetOf<String>()
private val controller = KeystorePersistenceController()

actual object LocalChatKeyStore {
    actual suspend fun hasKeyPair(authorId: String): Boolean = controller.mutex.withLock {
        localChatKeyPairs.containsKey(authorId) && !corruptAuthors.contains(authorId)
    }

    actual suspend fun loadKeyPair(authorId: String, password: String?): CryptoKeyPair? = controller.mutex.withLock {
        if (corruptAuthors.contains(authorId)) return@withLock null
        localChatKeyPairs[authorId]?.let { return@withLock it }
        val blob = PlatformEncryptedKeystoreStore.load(authorId) ?: return@withLock null
        if (password.isNullOrBlank()) return@withLock null
        val keyPair = decryptLocalKeyPair(blob, password)
        localChatKeyPairs[authorId] = keyPair
        keyPair
    }

    actual suspend fun getKeyPair(authorId: String): CryptoKeyPair? = controller.mutex.withLock {
        if (corruptAuthors.contains(authorId)) null else localChatKeyPairs[authorId]
    }

    actual suspend fun getOrGenerateKeyPair(authorId: String, password: String?): CryptoKeyPair = controller.mutex.withLock {
        check(!corruptAuthors.contains(authorId)) { "Keystore is corrupt: " }
        localChatKeyPairs[authorId]?.let { return@withLock it }
        require(!password.isNullOrBlank()) { "A password is required to create or unlock the durable keystore" }
        val existingBlob = PlatformEncryptedKeystoreStore.load(authorId)
        if (existingBlob != null) {
            val restored = decryptLocalKeyPair(existingBlob, password)
            localChatKeyPairs[authorId] = restored
            return@withLock restored
        }
        val generated = PlatformCrypto.generateKeyPair()
        val blob = encryptLocalKeyPair(generated, password)
        check(PlatformEncryptedKeystoreStore.save(authorId, blob)) {
            "Failed to durably persist encrypted keystore"
        }
        localChatKeyPairs[authorId] = generated
        generated
    }

    actual suspend fun saveKeyPair(authorId: String, keyPair: CryptoKeyPair, password: String?) = controller.mutex.withLock {
        check(!corruptAuthors.contains(authorId)) { "Keystore is corrupt: " }
        require(!password.isNullOrBlank()) { "A password is required to persist the keystore" }
        val blob = encryptLocalKeyPair(keyPair, password)
        check(PlatformEncryptedKeystoreStore.save(authorId, blob)) {
            "Failed to durably persist encrypted keystore"
        }
        localChatKeyPairs[authorId] = keyPair
    }

    actual suspend fun lock(authorId: String) = controller.mutex.withLock {
        if (corruptAuthors.contains(authorId)) return@withLock
        localChatKeyPairs.remove(authorId)
    }

    actual suspend fun restoreEncryptedBlob(authorId: String, blob: EncryptedKeyStoreBlob) = controller.mutex.withLock {
        check(!corruptAuthors.contains(authorId)) { "Keystore is corrupt: $authorId" }
        check(PlatformEncryptedKeystoreStore.save(authorId, blob)) {
            "Failed to durably restore encrypted keystore"
        }
        localChatKeyPairs.remove(authorId)
        Unit
    }

    actual suspend fun unlock(authorId: String, password: String) = controller.mutex.withLock {
        check(!corruptAuthors.contains(authorId)) { "Keystore is corrupt: " }
        if (localChatKeyPairs.containsKey(authorId)) return@withLock
        val blob = PlatformEncryptedKeystoreStore.load(authorId)
            ?: error("No encrypted keystore exists: ")
        val keyPair = decryptLocalKeyPair(blob, password)
        localChatKeyPairs[authorId] = keyPair
    }

    actual suspend fun deleteKeyPair(authorId: String) = controller.mutex.withLock {
        check(PlatformEncryptedKeystoreStore.delete(authorId)) { "Failed to delete durable keystore" }
        localChatKeyPairs.remove(authorId)
        corruptAuthors.remove(authorId)
        Unit
    }

    actual suspend fun markCorrupt(authorId: String) = controller.mutex.withLock {
        localChatKeyPairs.remove(authorId)
        corruptAuthors.add(authorId)
        Unit
    }

    actual suspend fun state(authorId: String): KeystoreState = controller.mutex.withLock {
        if (corruptAuthors.contains(authorId)) return@withLock KeystoreState.CORRUPT
        if (localChatKeyPairs.containsKey(authorId)) return@withLock KeystoreState.AVAILABLE
        try {
            if (PlatformEncryptedKeystoreStore.load(authorId) == null) KeystoreState.ABSENT else KeystoreState.LOCKED
        } catch (e: Throwable) {
            classifyKeystoreLoadFailure(e)
        }
    }
}
