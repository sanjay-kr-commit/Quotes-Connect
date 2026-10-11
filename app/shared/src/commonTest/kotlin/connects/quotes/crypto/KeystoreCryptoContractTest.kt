package connects.quotes.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class KeystoreCryptoContractTest {
    @Test
    fun contract_is_explicit_and_stable() {
        assertEquals(1, KeystoreCryptoContract.VERSION)
        assertEquals("PBKDF2-HMAC-SHA256", KeystoreCryptoContract.KDF)
        assertEquals(600_000, KeystoreCryptoContract.KDF_ITERATIONS)
        assertEquals(32, KeystoreCryptoContract.DERIVED_KEY_BYTES)
        assertEquals(16, KeystoreCryptoContract.SALT_BYTES)
        assertEquals("AES-256-GCM", KeystoreCryptoContract.CIPHER)
        assertEquals(12, KeystoreCryptoContract.NONCE_BYTES)
        assertEquals(16, KeystoreCryptoContract.AUTH_TAG_BYTES)
    }
}
