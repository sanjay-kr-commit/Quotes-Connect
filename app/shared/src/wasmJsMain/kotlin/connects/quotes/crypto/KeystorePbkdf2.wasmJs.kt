package connects.quotes.crypto

import kotlinx.coroutines.await
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
actual object KeystorePbkdf2 {
    actual suspend fun derive(password: String, salt: ByteArray): ByteArray {
        require(salt.size == KeystoreCryptoContract.SALT_BYTES) {
            "Keystore PBKDF2 salt must be ${KeystoreCryptoContract.SALT_BYTES} bytes"
        }

        val passwordKey = cryptoImportPBKDF2Password(encodeUtf8(password)).await()
        val derived = cryptoDerivePBKDF2Bits(
            passwordKey,
            byteArrayToUint8Array(salt),
            KeystoreCryptoContract.KDF_ITERATIONS,
            KeystoreCryptoContract.DERIVED_KEY_BYTES * 8
        ).await()
        return uint8ArrayToByteArray(arrayBufferToUint8Array(derived))
    }
}
