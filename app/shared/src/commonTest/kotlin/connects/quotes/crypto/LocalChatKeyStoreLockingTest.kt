package connects.quotes.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class LocalChatKeyStoreLockingTest {
    @Test
    fun lockRemovesUsableKeyAndTransitionsToLocked() = runTest {
        val authorId = "stage11-lock"
        LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")

        assertEquals(KeystoreState.AVAILABLE, LocalChatKeyStore.state(authorId))
        assertFalse(LocalChatKeyStore.loadKeyPair(authorId) == null)

        LocalChatKeyStore.lock(authorId)

        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))
        assertFalse(LocalChatKeyStore.hasKeyPair(authorId))
        assertNull(LocalChatKeyStore.loadKeyPair(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))
        LocalChatKeyStore.deleteKeyPair(authorId)
    }

    @Test
    fun lockedStore_requires_explicit_unlock_to_restore_identity() = runTest {
        val authorId = "stage11-locked-${kotlin.random.Random.nextLong()}"
        val created = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        LocalChatKeyStore.lock(authorId)

        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))
        assertFailsWith<Throwable> {
            LocalChatKeyStore.unlock(authorId, "wrong-password")
        }
        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))

        LocalChatKeyStore.unlock(authorId, "stage20-password")
        assertEquals(created, LocalChatKeyStore.getKeyPair(authorId))
        LocalChatKeyStore.deleteKeyPair(authorId)
    }

    @Test
    fun lockingAbsentStoreDoesNotInventAState() = runTest {
        val authorId = "stage11-absent"
        LocalChatKeyStore.lock(authorId)
        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        LocalChatKeyStore.deleteKeyPair(authorId)
    }
    @Test
    fun unlockRestoresPreviouslyLockedIdentity() = runTest {
        val authorId = "unlock-author"
        val created = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")

        LocalChatKeyStore.lock(authorId)
        assertEquals(KeystoreState.LOCKED, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.getKeyPair(authorId))

        LocalChatKeyStore.unlock(authorId, "stage20-password")

        assertEquals(KeystoreState.AVAILABLE, LocalChatKeyStore.state(authorId))
        assertNotNull(LocalChatKeyStore.getKeyPair(authorId))
        assertEquals(created.publicKey.contentEquals(LocalChatKeyStore.getKeyPair(authorId)!!.publicKey), true)
        LocalChatKeyStore.deleteKeyPair(authorId)
    }
}
