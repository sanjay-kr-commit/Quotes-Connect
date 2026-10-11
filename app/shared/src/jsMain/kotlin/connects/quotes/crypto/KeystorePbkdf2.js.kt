package connects.quotes.crypto

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

actual object KeystorePbkdf2 {
    private val subtle get() = window.asDynamic().crypto.subtle

    private fun toUint8Array(bytes: ByteArray): Uint8Array {
        val result = Uint8Array(bytes.size)
        for (i in bytes.indices) result.asDynamic()[i] = bytes[i]
        return result
    }

    private fun toByteArray(buffer: dynamic): ByteArray {
        val array = js("new Uint8Array(buffer)").unsafeCast<Uint8Array>()
        val result = ByteArray(array.length)
        for (i in 0 until array.length) result[i] = array.asDynamic()[i]
        return result
    }

    actual suspend fun derive(password: String, salt: ByteArray): ByteArray {
        require(salt.size == KeystoreCryptoContract.SALT_BYTES) {
            "Keystore PBKDF2 salt must be ${KeystoreCryptoContract.SALT_BYTES} bytes"
        }

        val passwordBytes = window.asDynamic().TextEncoder().encode(password).unsafeCast<Uint8Array>()
        val passwordKey = (subtle.importKey(
            "raw",
            passwordBytes,
            js("{ name: 'PBKDF2' }"),
            false,
            arrayOf("deriveBits")
        ) as Promise<dynamic>).await()

        val params = js("{ name: 'PBKDF2', hash: 'SHA-256' }")
        params.salt = toUint8Array(salt)
        params.iterations = KeystoreCryptoContract.KDF_ITERATIONS

        val derived = (subtle.deriveBits(
            params,
            passwordKey,
            KeystoreCryptoContract.DERIVED_KEY_BYTES * 8
        ) as Promise<dynamic>).await()

        return toByteArray(derived)
    }
}
