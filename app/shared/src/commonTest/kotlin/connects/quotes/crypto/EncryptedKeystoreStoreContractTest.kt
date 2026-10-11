package connects.quotes.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EncryptedKeystoreStoreContractTest {
    @Test
    fun saveAndLoadRoundTrip() = runTest {
        val id = "stage15-roundtrip"
        val blob = EncryptedKeyStoreBlob("ciphertext", "salt", "iv")
        assertEquals(KeystoreCryptoContract.CURRENT_BLOB_VERSION, blob.version)
        assertEquals(KeystoreCryptoContract.KDF, blob.kdf)
        assertEquals(KeystoreCryptoContract.KDF_ITERATIONS, blob.kdfIterations)
        assertEquals(KeystoreCryptoContract.CIPHER, blob.cipher)
        assertTrue(PlatformEncryptedKeystoreStore.save(id, blob))
        assertEquals(blob, PlatformEncryptedKeystoreStore.load(id))
        assertTrue(PlatformEncryptedKeystoreStore.delete(id))
        assertNull(PlatformEncryptedKeystoreStore.load(id))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedKeyStoreBlob("ciphertext", "salt", "nonce", version = 99)
        }
    }

    @Test
    fun rejectsCurrentBlobWithLegacyIterations() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedKeyStoreBlob(
                encryptedPayload = "ciphertext",
                salt = "salt",
                iv = "nonce",
                version = KeystoreCryptoContract.CURRENT_BLOB_VERSION,
                kdfIterations = KeystoreCryptoContract.LEGACY_KDF_ITERATIONS
            )
        }
    }
}
