package connects.quotes.crypto

import connects.quotes.security.CryptoEnvelope
import connects.quotes.security.DecryptionFailed
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalEncodingApi::class)
actual object PlatformCrypto {
    private val json = Json { ignoreUnknownKeys = true }
    private val subtle get() = window.asDynamic().crypto.subtle
    private val cryptoObj get() = window.asDynamic().crypto

    private fun byteArrayToUint8Array(bytes: ByteArray): Uint8Array {
        val arr = Uint8Array(bytes.size)
        for (i in bytes.indices) {
            arr.asDynamic()[i] = bytes[i]
        }
        return arr
    }

    private fun uint8ArrayToByteArray(arr: Uint8Array): ByteArray {
        val length = arr.length
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = arr.asDynamic()[i]
        }
        return result
    }

    private fun encodeUtf8(str: String): Uint8Array {
        val encoder = window.asDynamic().TextEncoder()
        return encoder.encode(str).unsafeCast<Uint8Array>()
    }

    private fun decodeUtf8(buffer: dynamic): String {
        val decoder = window.asDynamic().TextDecoder()
        val arr = js("new Uint8Array(buffer)")
        return decoder.decode(arr).unsafeCast<String>()
    }
    
    private fun bufferToByteArray(buffer: dynamic): ByteArray {
        val arr = js("new Uint8Array(buffer)").unsafeCast<Uint8Array>()
        return uint8ArrayToByteArray(arr)
    }
    
    private fun byteArrayToBuffer(bytes: ByteArray): dynamic {
        return byteArrayToUint8Array(bytes).buffer
    }

    actual suspend fun generateKeyPair(): CryptoKeyPair {
        val rsaParams = js("{ name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }")
        val keyPair = (subtle.generateKey(rsaParams, true, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
        
        val pubBuffer = (subtle.exportKey("spki", keyPair.publicKey) as Promise<dynamic>).await()
        val privBuffer = (subtle.exportKey("pkcs8", keyPair.privateKey) as Promise<dynamic>).await()
        
        val pubBytes = bufferToByteArray(pubBuffer)
        val privBytes = bufferToByteArray(privBuffer)
        
        return CryptoKeyPair(
            publicKey = Base64.encode(pubBytes),
            privateKey = Base64.encode(privBytes)
        )
    }

    actual suspend fun encryptEnvelope(
        plainText: String,
        recipientPublicKeyBase64: String,
        aad: String?,
        keyId: String?
    ): CryptoEnvelope {
        val pubKeyBytes = Base64.decode(recipientPublicKeyBase64.trim())
        val pubKeyParams = js("{ name: 'RSA-OAEP', hash: 'SHA-256' }")
        val pubKey = (subtle.importKey("spki", byteArrayToBuffer(pubKeyBytes), pubKeyParams, true, arrayOf("encrypt")) as Promise<dynamic>).await()

        val aesKeyBuffer = Uint8Array(32)
        cryptoObj.getRandomValues(aesKeyBuffer)
        val aesKeyParams = js("{ name: 'AES-GCM' }")
        val aesKey = (subtle.importKey("raw", aesKeyBuffer, aesKeyParams, false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
        
        val ivBuffer = Uint8Array(12)
        cryptoObj.getRandomValues(ivBuffer)
        val plainTextBuffer = encodeUtf8(plainText)
        
        val encryptParams = js("{ name: 'AES-GCM' }")
        encryptParams.iv = ivBuffer
        if (aad != null) {
            encryptParams.additionalData = encodeUtf8(aad)
        }
        
        val cipherBuffer = (subtle.encrypt(encryptParams, aesKey, plainTextBuffer) as Promise<dynamic>).await()
        val encryptedAesKeyBuffer = (subtle.encrypt(js("{ name: 'RSA-OAEP' }"), pubKey, aesKeyBuffer) as Promise<dynamic>).await()
        
        val encryptedAesKeyBytes = bufferToByteArray(encryptedAesKeyBuffer)
        val cipherBytes = bufferToByteArray(cipherBuffer)
        val ivBytes = uint8ArrayToByteArray(ivBuffer)

        return CryptoEnvelope(
            version = 1,
            suite = CryptoEnvelope.SUITE_RSA2048_AES256GCM,
            keyId = keyId ?: "default",
            nonce = Base64.encode(ivBytes),
            ciphertext = Base64.encode(encryptedAesKeyBytes) + ":" + Base64.encode(cipherBytes),
            aad = aad
        )
    }

    actual suspend fun decryptEnvelope(
        envelope: CryptoEnvelope,
        privateKeyBase64: String
    ): String {
        try {
            val parts = envelope.ciphertext.split(":")
            if (parts.size != 2) throw DecryptionFailed("Malformed envelope ciphertext")
            val encryptedAesKeyBytes = Base64.decode(parts[0])
            val cipherBytes = Base64.decode(parts[1])
            val ivBytes = Base64.decode(envelope.nonce)
            
            val cleanPrivKey = privateKeyBase64
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()
            val privKeyBytes = Base64.decode(cleanPrivKey)
            
            val privKeyParams = js("{ name: 'RSA-OAEP', hash: 'SHA-256' }")
            val privKey = (subtle.importKey("pkcs8", byteArrayToBuffer(privKeyBytes), privKeyParams, true, arrayOf("decrypt")) as Promise<dynamic>).await()
            
            val aesKeyRawBuffer = (subtle.decrypt(js("{ name: 'RSA-OAEP' }"), privKey, byteArrayToBuffer(encryptedAesKeyBytes)) as Promise<dynamic>).await()
            val aesKeyParams = js("{ name: 'AES-GCM' }")
            val aesKey = (subtle.importKey("raw", aesKeyRawBuffer, aesKeyParams, false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
            
            val decryptParams = js("{ name: 'AES-GCM' }")
            decryptParams.iv = byteArrayToBuffer(ivBytes)
            val envelopeAad = envelope.aad
            if (envelopeAad != null) {
                decryptParams.additionalData = encodeUtf8(envelopeAad)
            }
            
            val decryptedBuffer = (subtle.decrypt(decryptParams, aesKey, byteArrayToBuffer(cipherBytes)) as Promise<dynamic>).await()
            return decodeUtf8(decryptedBuffer)
        } catch (e: Exception) {
            if (e is DecryptionFailed) throw e
            throw DecryptionFailed("Envelope decryption failed", e)
        }
    }

    
    actual suspend fun generateAgreementKeyPair(): CryptoKeyPair {
        val params = js("{ name: 'ECDH', namedCurve: 'P-256' }")
        val keyPair = (subtle.generateKey(params, true, arrayOf("deriveKey", "deriveBits")) as kotlin.js.Promise<dynamic>).await()
        
        val pubBuffer = (subtle.exportKey("spki", keyPair.publicKey) as kotlin.js.Promise<dynamic>).await()
        val privBuffer = (subtle.exportKey("pkcs8", keyPair.privateKey) as kotlin.js.Promise<dynamic>).await()
        
        return CryptoKeyPair(
            publicKey = Base64.encode(bufferToByteArray(pubBuffer)),
            privateKey = Base64.encode(bufferToByteArray(privBuffer))
        )
    }

    actual suspend fun deriveAgreementSecret(privateKeyBase64: String, peerPublicKeyBase64: String): ByteArray {
        val ecdhParams = js("{ name: 'ECDH', namedCurve: 'P-256' }")
        
        val privBytes = Base64.decode(privateKeyBase64)
        val privKey = (subtle.importKey("pkcs8", byteArrayToBuffer(privBytes), ecdhParams, false, arrayOf("deriveBits")) as kotlin.js.Promise<dynamic>).await()
        
        val pubBytes = Base64.decode(peerPublicKeyBase64)
        val pubKey = (subtle.importKey("spki", byteArrayToBuffer(pubBytes), ecdhParams, false, arrayOf<String>()) as kotlin.js.Promise<dynamic>).await()
        
        val deriveParams = js("{ name: 'ECDH' }")
        deriveParams.public = pubKey
        
        val bitsBuffer = (subtle.deriveBits(deriveParams, privKey, 256) as kotlin.js.Promise<dynamic>).await()
        return bufferToByteArray(bitsBuffer)
    }

    actual suspend fun sign(message: ByteArray, privateKeyBase64: String): String {
        val cleanedPrivKey = privateKeyBase64
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()
        val privBytes = Base64.decode(cleanedPrivKey)
        val signParams = js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }")
        
        val privKey = (subtle.importKey("pkcs8", byteArrayToBuffer(privBytes), signParams, false, arrayOf("sign")) as kotlin.js.Promise<dynamic>).await()
        
        val sigBuffer = (subtle.sign(signParams, privKey, byteArrayToBuffer(message)) as kotlin.js.Promise<dynamic>).await()
        return Base64.encode(bufferToByteArray(sigBuffer))
    }

    actual suspend fun verify(message: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val cleanedPubKey = publicKeyBase64
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()
            val pubBytes = Base64.decode(cleanedPubKey)
            val verifyParams = js("{ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }")
            
            val pubKey = (subtle.importKey("spki", byteArrayToBuffer(pubBytes), verifyParams, false, arrayOf("verify")) as kotlin.js.Promise<dynamic>).await()
            
            val sigBytes = Base64.decode(signatureBase64)
            val result = (subtle.verify(verifyParams, pubKey, byteArrayToBuffer(sigBytes), byteArrayToBuffer(message)) as kotlin.js.Promise<dynamic>).await()
            result.toString().toBoolean()
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val params = js("{ name: 'HMAC', hash: 'SHA-256' }")
        val cryptoKey = (subtle.importKey(
            "raw", byteArrayToBuffer(key), params, false, arrayOf("sign")
        ) as Promise<dynamic>).await()
        val signature = (subtle.sign(params, cryptoKey, byteArrayToBuffer(message)) as Promise<dynamic>).await()
        return bufferToByteArray(signature)
    }

    actual suspend fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..255 * 32) { "Invalid HKDF output length" }
        val params = js("{ name: 'HKDF' }")
        val key = (subtle.importKey(
            "raw", byteArrayToBuffer(inputKeyMaterial), params, false, arrayOf("deriveBits")
        ) as Promise<dynamic>).await()
        val deriveParams = js("{ name: 'HKDF', hash: 'SHA-256' }")
        deriveParams.salt = byteArrayToBuffer(salt)
        deriveParams.info = byteArrayToBuffer(info)
        val bits = (subtle.deriveBits(deriveParams, key, length * 8) as Promise<dynamic>).await()
        return bufferToByteArray(bits)
    }

    actual suspend fun encrypt(plainText: String, recipientPublicKeyBase64: String): String {
        val env = encryptEnvelope(plainText, recipientPublicKeyBase64, null, null)
        return "ASYM:${env.ciphertext.split(":")[0]}:${env.nonce}:${env.ciphertext.split(":")[1]}"
    }

    actual suspend fun decrypt(cipherText: String, privateKeyBase64: String): String {
        return try {
            val trimmed = cipherText.trim()
            if (trimmed.startsWith("{") && trimmed.contains("\"nonce\"")) {
                val env = json.decodeFromString<CryptoEnvelope>(trimmed)
                return decryptEnvelope(env, privateKeyBase64)
            }

            if (trimmed.startsWith("ASYM:{")) {
                val jsonStr = trimmed.removePrefix("ASYM:")
                val env = json.decodeFromString<CryptoEnvelope>(jsonStr)
                return decryptEnvelope(env, privateKeyBase64)
            }

            if (trimmed.startsWith("ASYM:")) {
                val parts = trimmed.removePrefix("ASYM:").split(":")
                if (parts.size == 3) {
                    val encryptedAesKey = parts[0]
                    val iv = parts[1]
                    val cipherData = parts[2]
                    val env = CryptoEnvelope(
                        version = 1,
                        suite = CryptoEnvelope.SUITE_RSA2048_AES256GCM,
                        keyId = "default",
                        nonce = iv,
                        ciphertext = "$encryptedAesKey:$cipherData",
                        aad = null
                    )
                    return decryptEnvelope(env, privateKeyBase64)
                }
            }
            throw DecryptionFailed("Unsupported ciphertext format")
        } catch (e: Exception) {
            if (e is DecryptionFailed) throw e
            throw DecryptionFailed("Legacy decryption failed", e)
        }
    }

    actual suspend fun encryptSymmetric(plainText: String, password: String): EncryptedKeyStoreBlob {
        val saltBuffer = Uint8Array(KeystoreCryptoContract.SALT_BYTES)
        cryptoObj.getRandomValues(saltBuffer)
        val ivBuffer = Uint8Array(KeystoreCryptoContract.NONCE_BYTES)
        cryptoObj.getRandomValues(ivBuffer)
        
        val saltBytes = uint8ArrayToByteArray(saltBuffer)
        val secretKeyBytes = KeystorePbkdf2.derive(password, saltBytes)
        require(secretKeyBytes.size == KeystoreCryptoContract.DERIVED_KEY_BYTES) { "Invalid AES-256 key length" }
        val aesKey = (subtle.importKey("raw", byteArrayToUint8Array(secretKeyBytes), js("{ name: 'AES-GCM' }"), false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
        
        val encryptParams = js("{ name: 'AES-GCM' }")
        encryptParams.iv = ivBuffer
        encryptParams.tagLength = KeystoreCryptoContract.AUTH_TAG_BYTES * 8
        val cipherBuffer = (subtle.encrypt(encryptParams, aesKey, encodeUtf8(plainText)) as Promise<dynamic>).await()
        
        val cipherBytes = bufferToByteArray(cipherBuffer)
        val saltBytes = uint8ArrayToByteArray(saltBuffer)
        val ivBytes = uint8ArrayToByteArray(ivBuffer)
        
        return EncryptedKeyStoreBlob(
            encryptedPayload = Base64.encode(cipherBytes),
            salt = Base64.encode(saltBytes),
            iv = Base64.encode(ivBytes),
            version = KeystoreCryptoContract.CURRENT_BLOB_VERSION
        )
    }

    actual suspend fun decryptSymmetric(blob: EncryptedKeyStoreBlob, password: String): String {
        try {
            val saltBytes = Base64.decode(blob.salt)
            val ivBytes = Base64.decode(blob.iv)
            val cipherBytes = Base64.decode(blob.encryptedPayload)

            require(saltBytes.size == KeystoreCryptoContract.SALT_BYTES) { "Invalid keystore salt length" }
            require(ivBytes.size == KeystoreCryptoContract.NONCE_BYTES) { "Invalid keystore nonce length" }
            require(cipherBytes.size >= KeystoreCryptoContract.AUTH_TAG_BYTES) { "Keystore ciphertext is shorter than the authentication tag" }
            val aesKey = if (blob.version == KeystoreCryptoContract.LEGACY_BLOB_VERSION) {
                val passwordData = encodeUtf8(password)
                val pbkdf2Params = js("{ name: 'PBKDF2' }")
                val passwordKey = (subtle.importKey("raw", passwordData, pbkdf2Params, false, arrayOf("deriveKey")) as Promise<dynamic>).await()
                val deriveParams = js("{ name: 'PBKDF2', hash: 'SHA-256' }")
                deriveParams.salt = byteArrayToBuffer(saltBytes)
                deriveParams.iterations = KeystoreCryptoContract.LEGACY_KDF_ITERATIONS
                (subtle.deriveKey(deriveParams, passwordKey, js("{ name: 'AES-GCM', length: 256 }"), true, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
            } else {
                val secretKeyBytes = KeystorePbkdf2.derive(password, saltBytes)
                (subtle.importKey("raw", byteArrayToUint8Array(secretKeyBytes), js("{ name: 'AES-GCM' }"), false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
            }
            
            val decryptParams = js("{ name: 'AES-GCM' }")
            decryptParams.iv = byteArrayToBuffer(ivBytes)
            decryptParams.tagLength = KeystoreCryptoContract.AUTH_TAG_BYTES * 8
            
            val decryptedBuffer = (subtle.decrypt(decryptParams, aesKey, byteArrayToBuffer(cipherBytes)) as Promise<dynamic>).await()
            return decodeUtf8(decryptedBuffer)
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt keystore blob", e)
        }
    }

    actual suspend fun encryptChannel(
        plainText: String,
        serverPublicKeyBase64: String,
        keyId: String
    ): Pair<ByteArray, connects.quotes.model.EncryptedChannelPayload> {
        val aesKeyBuffer = Uint8Array(32)
        cryptoObj.getRandomValues(aesKeyBuffer)
        val aesKeyParams = js("{ name: 'AES-GCM' }")
        val aesKey = (subtle.importKey("raw", aesKeyBuffer, aesKeyParams, false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
        
        val ivBuffer = Uint8Array(12)
        cryptoObj.getRandomValues(ivBuffer)
        
        val reqBytes = connects.quotes.security.PlatformSecureRandom.nextBytes(16)
        val requestId = reqBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val aadString = "$keyId:$requestId"

        val encryptParams = js("{ name: 'AES-GCM' }")
        encryptParams.iv = ivBuffer
        encryptParams.additionalData = encodeUtf8(aadString)
        val cipherBuffer = (subtle.encrypt(encryptParams, aesKey, encodeUtf8(plainText)) as Promise<dynamic>).await()
        
        val cleanedKey = serverPublicKeyBase64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()
            
        val pubKeyBytes = Base64.decode(cleanedKey)
        val pubKeyParams = js("{ name: 'RSA-OAEP', hash: 'SHA-256' }")
        val pubKey = (subtle.importKey("spki", byteArrayToBuffer(pubKeyBytes), pubKeyParams, true, arrayOf("encrypt")) as Promise<dynamic>).await()
        
        val rsaEncryptParams = js("{ name: 'RSA-OAEP' }")
        val encryptedAesKeyBuffer = (subtle.encrypt(rsaEncryptParams, pubKey, aesKeyBuffer) as Promise<dynamic>).await()
        
        val encryptedAesKeyBytes = bufferToByteArray(encryptedAesKeyBuffer)
        val cipherBytes = bufferToByteArray(cipherBuffer)
        val ivBytes = bufferToByteArray(ivBuffer.buffer)
        val aesKeyBytes = bufferToByteArray(aesKeyBuffer.buffer)
        
        val payload = connects.quotes.model.EncryptedChannelPayload(
            keyId = keyId,
            requestId = requestId,
            encryptedAesKey = Base64.encode(encryptedAesKeyBytes),
            iv = Base64.encode(ivBytes),
            cipherText = Base64.encode(cipherBytes)
        )
        return Pair(aesKeyBytes, payload)
    }

    actual suspend fun decryptChannel(
        cipherTextBase64: String,
        ivBase64: String,
        aesKey: ByteArray,
        aad: String?
    ): String {
        return try {
            val aesKeyBuffer = byteArrayToBuffer(aesKey)
            val aesKeyParams = js("{ name: 'AES-GCM' }")
            val cryptoKey = (subtle.importKey("raw", aesKeyBuffer, aesKeyParams, false, arrayOf("decrypt")) as Promise<dynamic>).await()

            val ivBytes = Base64.decode(ivBase64.trim())
            val ivBuffer = byteArrayToBuffer(ivBytes)
            val decryptParams = js("{ name: 'AES-GCM' }")
            decryptParams.iv = ivBuffer
            if (aad != null) {
                decryptParams.additionalData = encodeUtf8(aad)
            }

            val cipherBytes = Base64.decode(cipherTextBase64.trim())
            val cipherBuffer = byteArrayToBuffer(cipherBytes)
            val plainBuffer = (subtle.decrypt(decryptParams, cryptoKey, cipherBuffer) as Promise<dynamic>).await()
            decodeUtf8(bufferToByteArray(plainBuffer))
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt server channel response", e)
        }
    }
}
