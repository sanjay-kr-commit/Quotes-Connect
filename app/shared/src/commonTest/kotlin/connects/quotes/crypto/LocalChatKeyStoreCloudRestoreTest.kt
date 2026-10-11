package connects.quotes.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalChatKeyStoreCloudRestoreTest {
    @Test
    fun restorePreservesOpaqueEnvelopeAndUnlocksSameIdentity() = runTest {
        val sourceId = "stage23-source"
        val targetId = "stage23-target"
        val password = "stage23-password"

        LocalChatKeyStore.deleteKeyPair(sourceId)
        LocalChatKeyStore.deleteKeyPair(targetId)

        val source = LocalChatKeyStore.getOrGenerateKeyPair(sourceId, password)
        val sourceBlob = PlatformEncryptedKeystoreStore.load(sourceId)
            ?: error("source blob missing")

        LocalChatKeyStore.restoreEncryptedBlob(targetId, sourceBlob)
        val restoredBlob = PlatformEncryptedKeystoreStore.load(targetId)
            ?: error("restored blob missing")

        assertEquals(sourceBlob, restoredBlob)
        assertTrue(!LocalChatKeyStore.hasKeyPair(targetId))

        LocalChatKeyStore.unlock(targetId, password)
        assertEquals(source, LocalChatKeyStore.getKeyPair(targetId))
    }

    @Test
    fun restoreDoesNotReencryptEnvelope() = runTest {
        val sourceId = "stage23-source-reencrypt"
        val targetId = "stage23-target-reencrypt"
        val password = "stage23-password"

        LocalChatKeyStore.deleteKeyPair(sourceId)
        LocalChatKeyStore.deleteKeyPair(targetId)

        LocalChatKeyStore.getOrGenerateKeyPair(sourceId, password)
        val sourceBlob = PlatformEncryptedKeystoreStore.load(sourceId)
            ?: error("source blob missing")

        LocalChatKeyStore.restoreEncryptedBlob(targetId, sourceBlob)
        val beforeUnlock = PlatformEncryptedKeystoreStore.load(targetId)
            ?: error("target blob missing")
        LocalChatKeyStore.unlock(targetId, password)
        val afterUnlock = PlatformEncryptedKeystoreStore.load(targetId)
            ?: error("target blob missing after unlock")

        assertEquals(sourceBlob, beforeUnlock)
        assertEquals(beforeUnlock, afterUnlock)
        assertNotEquals("", afterUnlock.encryptedPayload)
    }
}
