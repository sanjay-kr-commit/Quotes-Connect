package connects.quotes.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object KeystorePbkdf2 {
    actual suspend fun derive(password: String, salt: ByteArray): ByteArray {
        require(salt.size == KeystoreCryptoContract.SALT_BYTES) {
            "Keystore PBKDF2 salt must be ${KeystoreCryptoContract.SALT_BYTES} bytes"
        }

        val passwordBytes = password.encodeToByteArray()
        val mac = Mac.getInstance("HmacSHA256")
        val passwordKey = SecretKeySpec(passwordBytes, "HmacSHA256")
        mac.init(passwordKey)

        val block = ByteArray(salt.size + 4)
        salt.copyInto(block, 0)
        block[block.lastIndex] = 1

        mac.update(block)
        var u = mac.doFinal()
        val output = ByteArray(KeystoreCryptoContract.DERIVED_KEY_BYTES)
        u.copyInto(output)

        repeat(KeystoreCryptoContract.KDF_ITERATIONS - 1) {
            u = mac.doFinal(u)
            for (i in output.indices) {
                output[i] = (output[i].toInt() xor u[i].toInt()).toByte()
            }
        }
        return output
    }
}
