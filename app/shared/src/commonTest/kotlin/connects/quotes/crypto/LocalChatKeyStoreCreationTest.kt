package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalChatKeyStoreCreationTest {
    @Test
    fun absent_identity_cannot_be_created_without_password() = runTest {
        val authorId = "stage20-password-required-${kotlin.random.Random.nextLong()}"
        assertFailsWith<IllegalArgumentException> {
            LocalChatKeyStore.getOrGenerateKeyPair(authorId)
        }
        assertEquals(KeystoreState.ABSENT, LocalChatKeyStore.state(authorId))
        LocalChatKeyStore.deleteKeyPair(authorId)
    }

    @Test
    fun absent_identity_is_created_once_and_reused() = runTest {
        val authorId = "stage09-creation-${kotlin.random.Random.nextLong()}"
        assertFalse(LocalChatKeyStore.hasKeyPair(authorId))

        val first = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        assertTrue(LocalChatKeyStore.hasKeyPair(authorId))
        assertNotNull(first.privateKey)
        assertNotNull(first.publicKey)

        val second = LocalChatKeyStore.getOrGenerateKeyPair(authorId, "stage20-password")
        assertEquals(first, second)

        LocalChatKeyStore.deleteKeyPair(authorId)
    }
}
