package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalChatKeyStoreDurableLifecycleTest {
    @Test
    fun durable_lifecycle_round_trips_identity_without_plaintext_storage() = runTest {
        val authorId = "stage21-${kotlin.random.Random.nextLong()}"
        val password = "stage21-password"

        val created = LocalChatKeyStore.getOrGenerateKeyPair(authorId, password)
        val durable = PlatformEncryptedKeystoreStore.load(authorId)

        assertNotNull(durable)
        assertEquals(KeystoreCryptoContract.CURRENT_BLOB_VERSION, durable.version)
        assertTrue(durable.encryptedPayload.isNotEmpty())
        assertTrue(!durable.encryptedPayload.contains(created.privateKey))
        assertTrue(!durable.encryptedPayload.contains(created.publicKey))

        LocalChatKeyStore.lock(authorId)
        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))

        assertFailsWith<Throwable> {
            LocalChatKeyStore.unlock(authorId, "wrong-password")
        }
        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))

        LocalChatKeyStore.unlock(authorId, password)
        assertEquals(KeystoreState.AVAILABLE, LocalChatKeyStore.state(authorId))
        assertEquals(created, LocalChatKeyStore.getKeyPair(authorId))

        LocalChatKeyStore.lock(authorId)
        LocalChatKeyStore.deleteKeyPair(authorId)

        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        assertNull(PlatformEncryptedKeystoreStore.load(authorId))
    }
}
