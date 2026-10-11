package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalChatKeyStoreLoadingTest {
    @Test
    fun loading_missing_identity_does_not_generate() = runTest {
        val authorId = "stage10-missing-${kotlin.random.Random.nextLong()}"
        assertNull(LocalChatKeyStore.loadKeyPair(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))
    }

    @Test
    fun loading_existing_identity_returns_same_key_pair() = runTest {
        val authorId = "stage10-existing-${kotlin.random.Random.nextLong()}"
        val created = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        val loaded = LocalChatKeyStore.loadKeyPair(authorId)
        assertEquals(created, loaded)
        LocalChatKeyStore.lock(authorId)
        val restored = LocalChatKeyStore.loadKeyPair(authorId, "stage20-password")
        assertEquals(created, restored)
        LocalChatKeyStore.deleteKeyPair(authorId)
    }
}
