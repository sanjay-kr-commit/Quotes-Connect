package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalChatKeyStoreDeletionTest {
    @Test
    fun delete_available_keystore_removes_identity_and_returns_absent() = runTest {
        val authorId = "stage12-delete-available"
        LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")

        LocalChatKeyStore.deleteKeyPair(authorId)

        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.loadKeyPair(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))
    }

    @Test
    fun delete_locked_keystore_returns_to_absent() = runTest {
        val authorId = "stage12-delete-locked"
        LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        LocalChatKeyStore.lock(authorId)

        LocalChatKeyStore.deleteKeyPair(authorId)

        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.loadKeyPair(authorId))
    }

    @Test
    fun deletion_does_not_preserve_old_identity_for_recreation() = runTest {
        val authorId = "stage12-delete-recreate"
        val oldKey = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")

        LocalChatKeyStore.deleteKeyPair(authorId)

        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        val newKey = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        assertEquals(KeystoreState.AVAILABLE, LocalChatKeyStore.state(authorId))
        kotlin.test.assertNotEquals(oldKey, newKey)
        LocalChatKeyStore.deleteKeyPair(authorId)
    }
}
