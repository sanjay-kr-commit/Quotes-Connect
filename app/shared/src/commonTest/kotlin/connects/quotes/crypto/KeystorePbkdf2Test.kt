package connects.quotes.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeystorePbkdf2Test {
    @Test
    fun derives_contract_vector() = runTest {
        val derived = KeystorePbkdf2.derive("password", "0123456789abcdef".encodeToByteArray())
        assertEquals(KeystoreCryptoContract.DERIVED_KEY_BYTES, derived.size)
        assertContentEquals(
            hexToBytes("996d7c90f74a4a169cf7adef42b06848f7d1bac3e568d1cc94d4f79be1ee0263"),
            derived
        )
    }

    @Test
    fun rejects_wrong_salt_length() = runTest {
        assertFailsWith<IllegalArgumentException> {
            KeystorePbkdf2.derive("password", ByteArray(KeystoreCryptoContract.SALT_BYTES - 1))
        }
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
