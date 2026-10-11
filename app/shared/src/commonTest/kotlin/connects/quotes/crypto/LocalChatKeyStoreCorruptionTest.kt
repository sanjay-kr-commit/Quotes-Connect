package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalChatKeyStoreCorruptionTest {
    @Test
    fun corruptStateNeverSilentlyRegenerates() = runTest {
        val authorId = "stage13-corrupt-${hashCode()}"
        LocalChatKeyStore.deleteKeyPair(authorId)
        LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")

        LocalChatKeyStore.markCorrupt(authorId)

        assertEquals(KeystoreState.CORRUPT, LocalChatKeyStore.state(authorId))
        assertNull(LocalChatKeyStore.loadKeyPair(authorId))
        assertFailsWith<IllegalStateException> {
            LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        }

        LocalChatKeyStore.deleteKeyPair(authorId)
        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
    }
}
