package connects.quotes.crypto

import connects.quotes.security.CryptoEnvelope
import connects.quotes.security.DecryptionFailed
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalEncodingApi::class, kotlin.js.ExperimentalWasmJsInterop::class)



actual object PlatformCrypto {
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun generateKeyPair(): CryptoKeyPair {
        val keyPair = cryptoGenerateRSAKeyPair().await()
        
        val pubKey = getPublicKey(keyPair)
        val privKey = getPrivateKey(keyPair)
        
        val pubBuffer = cryptoExportKey("spki", pubKey).await()
        val privBuffer = cryptoExportKey("pkcs8", privKey).await()
        
        val pubBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(pubBuffer))
        val privBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(privBuffer))
        
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
        val pubKeyBuffer = byteArrayToUint8Array(pubKeyBytes)
        val pubKey = cryptoImportRSAPublicKey(pubKeyBuffer).await()

        val aesKeyBuffer = cryptoGetRandomValues(32) // 256-bit AES key
        val aesKey = cryptoImportAESKey(aesKeyBuffer).await()
        
        val ivBuffer = cryptoGetRandomValues(12) // 96-bit IV
        val plainTextBuffer = encodeUtf8(plainText)
        
        val cipherBuffer = if (aad != null) {
            val aadBuffer = encodeUtf8(aad)
            cryptoEncryptAESGCMWithAAD(aesKey, ivBuffer, plainTextBuffer, aadBuffer).await()
        } else {
            cryptoEncryptAESGCM(aesKey, ivBuffer, plainTextBuffer).await()
        }
        
        val encryptedAesKeyBuffer = cryptoEncryptRSA(pubKey, aesKeyBuffer).await()
        
        val encryptedAesKeyBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(encryptedAesKeyBuffer))
        val cipherBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(cipherBuffer))
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
            
            val privKeyBuffer = byteArrayToUint8Array(privKeyBytes)
            val privKey = cryptoImportRSAPrivateKey(privKeyBuffer).await()
            
            val encryptedAesKeyBuffer = byteArrayToUint8Array(encryptedAesKeyBytes)
            val aesKeyRawBuffer = cryptoDecryptRSA(privKey, encryptedAesKeyBuffer).await()
            val aesKeyRawUint8 = arrayBufferToUint8Array(aesKeyRawBuffer)
            val aesKey = cryptoImportAESKey(aesKeyRawUint8).await()
            
            val cipherBuffer = byteArrayToUint8Array(cipherBytes)
            val ivBuffer = byteArrayToUint8Array(ivBytes)
            
            val decryptedBuffer = if (envelope.aad != null) {
                val envelopeAad = envelope.aad
                val aadBuffer = encodeUtf8(envelopeAad!!)
                cryptoDecryptAESGCMWithAAD(aesKey, ivBuffer, cipherBuffer, aadBuffer).await()
            } else {
                cryptoDecryptAESGCM(aesKey, ivBuffer, cipherBuffer).await()
            }
            
            return decodeUtf8(decryptedBuffer)
        } catch (e: Exception) {
            if (e is DecryptionFailed) throw e
            throw DecryptionFailed("Envelope decryption failed", e)
        }
    }

    
    
    actual suspend fun generateAgreementKeyPair(): CryptoKeyPair {
        val keyPair = cryptoGenerateECDHKeyPair().await()
        val pubBuffer = cryptoExportKey("spki", getPublicKey(keyPair)).await()
        val privBuffer = cryptoExportKey("pkcs8", getPrivateKey(keyPair)).await()
        
        return CryptoKeyPair(
            publicKey = Base64.encode(uint8ArrayToByteArray(arrayBufferToUint8Array(pubBuffer))),
            privateKey = Base64.encode(uint8ArrayToByteArray(arrayBufferToUint8Array(privBuffer)))
        )
    }

    actual suspend fun deriveAgreementSecret(privateKeyBase64: String, peerPublicKeyBase64: String): ByteArray {
        val privBytes = Base64.decode(privateKeyBase64)
        val privKey = cryptoImportECDHPrivateKey(byteArrayToUint8Array(privBytes)).await()
        
        val pubBytes = Base64.decode(peerPublicKeyBase64)
        val pubKey = cryptoImportECDHPublicKey(byteArrayToUint8Array(pubBytes)).await()
        
        val bitsBuffer = cryptoDeriveECDHBits(pubKey, privKey).await()
        return uint8ArrayToByteArray(arrayBufferToUint8Array(bitsBuffer))
    }

    actual suspend fun sign(message: ByteArray, privateKeyBase64: String): String {
        val cleanedPrivKey = privateKeyBase64
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\r", "").replace("\n", "")
            .replace(" ", "")
            .trim()
        val privBytes = Base64.decode(cleanedPrivKey)
        val privKey = cryptoImportRSASignPrivateKey(byteArrayToUint8Array(privBytes)).await()
        
        val sigBuffer = cryptoSignRSA(privKey, byteArrayToUint8Array(message)).await()
        return Base64.encode(uint8ArrayToByteArray(arrayBufferToUint8Array(sigBuffer)))
    }

    actual suspend fun verify(message: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val cleanedPubKey = publicKeyBase64
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "").replace("\n", "")
                .replace(" ", "")
                .trim()
            val pubBytes = Base64.decode(cleanedPubKey)
            val pubKey = cryptoImportRSAVerifyPublicKey(byteArrayToUint8Array(pubBytes)).await()
            
            val sigBytes = Base64.decode(signatureBase64)
            val result = cryptoVerifyRSA(pubKey, byteArrayToUint8Array(sigBytes), byteArrayToUint8Array(message)).await()
            cryptoIsTrue(result)
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val result = cryptoHmacSHA256(
            byteArrayToUint8Array(key),
            byteArrayToUint8Array(message)
        ).await()
        return uint8ArrayToByteArray(arrayBufferToUint8Array(result))
    }

    actual suspend fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..255 * 32) { "Invalid HKDF output length" }
        val result = cryptoHKDFSHA256(
            byteArrayToUint8Array(inputKeyMaterial),
            byteArrayToUint8Array(salt),
            byteArrayToUint8Array(info),
            length * 8
        ).await()
        return uint8ArrayToByteArray(arrayBufferToUint8Array(result))
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
        val saltBytes = uint8ArrayToByteArray(cryptoGetRandomValues(KeystoreCryptoContract.SALT_BYTES))
        val ivBytes = uint8ArrayToByteArray(cryptoGetRandomValues(KeystoreCryptoContract.NONCE_BYTES))
        
        val derivedKeyBytes = KeystorePbkdf2.derive(password, saltBytes)
        require(derivedKeyBytes.size == KeystoreCryptoContract.DERIVED_KEY_BYTES) { "Invalid AES-256 key length" }
        val aesKey = cryptoImportAESKey(byteArrayToUint8Array(derivedKeyBytes)).await()
        
        val ivBuffer = byteArrayToUint8Array(ivBytes)
        val plainTextBuffer = encodeUtf8(plainText)
        
        val cipherBuffer = cryptoEncryptKeystoreAESGCM(aesKey, ivBuffer, plainTextBuffer).await()
        val cipherBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(cipherBuffer))
        
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
                val passwordKey = cryptoImportPBKDF2Password(passwordData).await()
                val saltBuffer = byteArrayToUint8Array(saltBytes)
                cryptoDerivePBKDF2Key(passwordKey, saltBuffer, KeystoreCryptoContract.LEGACY_KDF_ITERATIONS).await()
            } else {
                cryptoImportAESKey(byteArrayToUint8Array(KeystorePbkdf2.derive(password, saltBytes))).await()
            }
            
            val ivBuffer = byteArrayToUint8Array(ivBytes)
            val cipherBuffer = byteArrayToUint8Array(cipherBytes)
            
            val decryptedBuffer = cryptoDecryptKeystoreAESGCM(aesKey, ivBuffer, cipherBuffer).await()
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
        val aesKeyBuffer = cryptoGetRandomValues(32)
        val aesKey = cryptoImportAESKey(aesKeyBuffer).await()
        
        val ivBytes = uint8ArrayToByteArray(cryptoGetRandomValues(12))
        val ivBuffer = byteArrayToUint8Array(ivBytes)
        val plainTextBuffer = encodeUtf8(plainText)
        
        val reqBytes = connects.quotes.security.PlatformSecureRandom.nextBytes(16)
        val requestId = reqBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val aadString = "$keyId:$requestId"

        val cipherBufferAny = cryptoEncryptAESGCMWithAAD(aesKey, ivBuffer, plainTextBuffer, encodeUtf8(aadString)).await()
        val cipherBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(cipherBufferAny))
        
        val cleanedKey = serverPublicKeyBase64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()
            
        val pubKeyBytes = Base64.decode(cleanedKey)
        val pubKeyBuffer = byteArrayToUint8Array(pubKeyBytes)
        val serverKey = cryptoImportRSAPublicKey(pubKeyBuffer).await()
        
        val encryptedAesBufferAny = cryptoEncryptRSA(serverKey, aesKeyBuffer).await()
        val encryptedAesKeyBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(encryptedAesBufferAny))
        
        val aesKeyBytes = uint8ArrayToByteArray(aesKeyBuffer)
        
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
            val aesKeyBuffer = byteArrayToUint8Array(aesKey)
            val cryptoKey = cryptoImportAESKey(aesKeyBuffer).await()

            val ivBytes = Base64.decode(ivBase64.trim())
            val ivBuffer = byteArrayToUint8Array(ivBytes)

            val cipherBytes = Base64.decode(cipherTextBase64.trim())
            val cipherBuffer = byteArrayToUint8Array(cipherBytes)
            
            val plainBufferAny = if (aad != null) {
                cryptoDecryptAESGCMWithAAD(cryptoKey, ivBuffer, cipherBuffer, encodeUtf8(aad)).await()
            } else {
                cryptoDecryptAESGCM(cryptoKey, ivBuffer, cipherBuffer).await()
            }
            val plainBytes = uint8ArrayToByteArray(arrayBufferToUint8Array(plainBufferAny))
            decodeUtf8(plainBufferAny)
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt server channel response", e)
        }
    }
}
