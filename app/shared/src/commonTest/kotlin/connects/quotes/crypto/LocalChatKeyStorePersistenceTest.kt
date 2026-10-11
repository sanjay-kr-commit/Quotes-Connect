package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LocalChatKeyStorePersistenceTest {
    @Test
    fun lock_removes_memory_but_preserves_encrypted_durable_identity() = runTest {
        val authorId = "stage20-persistence-${kotlin.random.Random.nextLong()}"
        val password = "stage20-password"
        val created = LocalChatKeyStore.getOrGenerateKeyPair(authorId, password)

        val blob = PlatformEncryptedKeystoreStore.load(authorId)
        assertNotNull(blob)
        assertEquals(KeystoreCryptoContract.CURRENT_BLOB_VERSION, blob.version)
        kotlin.test.assertFalse(blob.encryptedPayload.contains(created.privateKey))

        LocalChatKeyStore.lock(authorId)
        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))

        LocalChatKeyStore.unlock(authorId, password)
        assertEquals(KeystoreState.AVAILABLE, LocalChatKeyStore.state(authorId))
        assertEquals(created, LocalChatKeyStore.getKeyPair(authorId))

        LocalChatKeyStore.deleteKeyPair(authorId)
    }
}
