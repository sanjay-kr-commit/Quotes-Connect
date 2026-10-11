package connects.quotes.crypto

import android.util.Base64
import connects.quotes.security.AuthenticationFailed
import connects.quotes.security.CryptoEnvelope
import connects.quotes.security.CryptoException
import connects.quotes.security.DecryptionFailed
import connects.quotes.security.MalformedEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object PlatformCrypto {
    private val keyFactory = KeyFactory.getInstance("RSA")
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual suspend fun generateKeyPair(): CryptoKeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val kp = kpg.generateKeyPair()
        val pubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val privB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        return CryptoKeyPair(publicKey = pubB64, privateKey = privB64)
    }

    actual suspend fun encryptEnvelope(
        plainText: String,
        recipientPublicKeyBase64: String,
        aad: String?,
        keyId: String?
    ): CryptoEnvelope {
        return try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, secureRandom)
            val aesKey = keyGen.generateKey()
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
            if (!aad.isNullOrBlank()) {
                aesCipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            }
            val cipherTextBytes = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val cleanedKey = recipientPublicKeyBase64
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()
            val pubKeyBytes = try {
                Base64.decode(cleanedKey, Base64.NO_WRAP)
            } catch (_: Exception) {
                Base64.decode(cleanedKey, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes))
            val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
            rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepParams)
            val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

            val b64Key = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)
            val b64Payload = Base64.encodeToString(cipherTextBytes, Base64.NO_WRAP)

            CryptoEnvelope(
                version = 1,
                suite = CryptoEnvelope.SUITE_RSA2048_AES256GCM,
                keyId = keyId,
                nonce = Base64.encodeToString(iv, Base64.NO_WRAP),
                aad = aad,
                ciphertext = "$b64Key:$b64Payload"
            )
        } catch (e: Exception) {
            throw DecryptionFailed("Encryption failed", e)
        }
    }

    actual suspend fun decryptEnvelope(
        envelope: CryptoEnvelope,
        privateKeyBase64: String
    ): String {
        return try {
            val parts = envelope.ciphertext.split(":")
            if (parts.size != 2) {
                throw MalformedEnvelope("Envelope ciphertext payload requires format <encryptedKey>:<ciphertext>")
            }
            val encryptedAesKey = try {
                Base64.decode(parts[0].trim(), Base64.NO_WRAP)
            } catch (_: Exception) {
                Base64.decode(parts[0].trim(), Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val cipherTextBytes = try {
                Base64.decode(parts[1].trim(), Base64.NO_WRAP)
            } catch (_: Exception) {
                Base64.decode(parts[1].trim(), Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val iv = try {
                Base64.decode(envelope.nonce.trim(), Base64.NO_WRAP)
            } catch (_: Exception) {
                Base64.decode(envelope.nonce.trim(), Base64.URL_SAFE or Base64.NO_WRAP)
            }

            val cleanedPrivKey = privateKeyBase64
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()
            val privKeyBytes = try {
                Base64.decode(cleanedPrivKey, Base64.NO_WRAP)
            } catch (_: Exception) {
                Base64.decode(cleanedPrivKey, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))

            val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
            rsaCipher.init(Cipher.DECRYPT_MODE, privKey, oaepParams)
            val rawAesKey = rsaCipher.doFinal(encryptedAesKey)

            val aesKey = SecretKeySpec(rawAesKey, "AES")
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
            val aadVal = envelope.aad
            if (!aadVal.isNullOrBlank()) {
                aesCipher.updateAAD(aadVal.toByteArray(Charsets.UTF_8))
            }
            val decryptedBytes = aesCipher.doFinal(cipherTextBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw AuthenticationFailed("Authentication tag mismatch or corrupted ciphertext/AAD", e)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt envelope", e)
        }
    }

    
    actual suspend fun generateAgreementKeyPair(): CryptoKeyPair {
        val keyPairGen = java.security.KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"), secureRandom)
        val keyPair = keyPairGen.generateKeyPair()
        return CryptoKeyPair(
            publicKey = android.util.Base64.encodeToString(keyPair.public.encoded, android.util.Base64.NO_WRAP),
            privateKey = android.util.Base64.encodeToString(keyPair.private.encoded, android.util.Base64.NO_WRAP)
        )
    }

    actual suspend fun deriveAgreementSecret(privateKeyBase64: String, peerPublicKeyBase64: String): ByteArray {
        val kf = java.security.KeyFactory.getInstance("EC")
        val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(android.util.Base64.decode(privateKeyBase64, android.util.Base64.DEFAULT))
        val privKey = kf.generatePrivate(privKeySpec)
        
        val pubKeySpec = java.security.spec.X509EncodedKeySpec(android.util.Base64.decode(peerPublicKeyBase64, android.util.Base64.DEFAULT))
        val pubKey = kf.generatePublic(pubKeySpec)
        
        val keyAgree = javax.crypto.KeyAgreement.getInstance("ECDH")
        keyAgree.init(privKey)
        keyAgree.doPhase(pubKey, true)
        return keyAgree.generateSecret()
    }

    actual suspend fun sign(message: ByteArray, privateKeyBase64: String): String {
        val cleanedPrivKey = privateKeyBase64
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()
        val privKeyBytes = android.util.Base64.decode(cleanedPrivKey, android.util.Base64.DEFAULT)
        val privKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privKeyBytes))
        
        val signature = java.security.Signature.getInstance("SHA256withRSA")
        signature.initSign(privKey, secureRandom)
        signature.update(message)
        return android.util.Base64.encodeToString(signature.sign(), android.util.Base64.NO_WRAP)
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
            val pubKeyBytes = android.util.Base64.decode(cleanedPubKey, android.util.Base64.DEFAULT)
            val pubKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(pubKeyBytes))
            
            val signature = java.security.Signature.getInstance("SHA256withRSA")
            signature.initVerify(pubKey)
            signature.update(message)
            signature.verify(android.util.Base64.decode(signatureBase64, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    actual suspend fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..255 * 32) { "Invalid HKDF output length" }
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, inputKeyMaterial)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val blockInput = previous + info + byteArrayOf(counter.toByte())
            previous = hmacSha256(prk, blockInput)
            val copyLength = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, copyLength)
            offset += copyLength
            counter++
        }
        return output
    }

    actual suspend fun encrypt(plainText: String, recipientPublicKeyBase64: String): String {
        val env = encryptEnvelope(plainText, recipientPublicKeyBase64)
        val parts = env.ciphertext.split(":")
        val b64Key = parts[0]
        val b64Payload = parts[1]
        val b64Iv = env.nonce
        return "ASYM:$b64Key:$b64Iv:$b64Payload"
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
                    val encryptedAesKey = try {
                        Base64.decode(parts[0], Base64.NO_WRAP)
                    } catch (_: Exception) {
                        Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP)
                    }
                    val iv = try {
                        Base64.decode(parts[1], Base64.NO_WRAP)
                    } catch (_: Exception) {
                        Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
                    }
                    val cipherTextBytes = try {
                        Base64.decode(parts[2], Base64.NO_WRAP)
                    } catch (_: Exception) {
                        Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP)
                    }

                    val cleanedPrivKey = privateKeyBase64
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\r", "")
                        .replace("\n", "")
                        .replace(" ", "")
                        .trim()
                    val privKeyBytes = try {
                        Base64.decode(cleanedPrivKey, Base64.NO_WRAP)
                    } catch (_: Exception) {
                        Base64.decode(cleanedPrivKey, Base64.URL_SAFE or Base64.NO_WRAP)
                    }
                    val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))
                    val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                    val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
                    rsaCipher.init(Cipher.DECRYPT_MODE, privKey, oaepParams)
                    val rawAesKey = rsaCipher.doFinal(encryptedAesKey)

                    val aesKey = SecretKeySpec(rawAesKey, "AES")
                    val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
                    aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
                    val decryptedBytes = aesCipher.doFinal(cipherTextBytes)
                    return String(decryptedBytes, Charsets.UTF_8)
                }
            }

            throw connects.quotes.security.DecryptionFailed("Unsupported or malformed ciphertext format")
        } catch (e: Exception) {
            if (e is connects.quotes.security.DecryptionFailed) throw e
            throw connects.quotes.security.DecryptionFailed("Unable to decrypt message with local private key", e)
        }
    }

    private fun legacyPbkdf2(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, KeystoreCryptoContract.LEGACY_KDF_ITERATIONS, KeystoreCryptoContract.DERIVED_KEY_BYTES * 8)
        return factory.generateSecret(spec).encoded
    }

    actual suspend fun encryptSymmetric(plainText: String, password: String): EncryptedKeyStoreBlob {
        val salt = ByteArray(KeystoreCryptoContract.SALT_BYTES)
        secureRandom.nextBytes(salt)
        val iv = ByteArray(KeystoreCryptoContract.NONCE_BYTES)
        secureRandom.nextBytes(iv)

        val secretKeyBytes = KeystorePbkdf2.derive(password, salt)
        require(secretKeyBytes.size == KeystoreCryptoContract.DERIVED_KEY_BYTES) { "Invalid AES-256 key length" }
        require(salt.size == KeystoreCryptoContract.SALT_BYTES) { "Invalid keystore salt length" }
        require(iv.size == KeystoreCryptoContract.NONCE_BYTES) { "Invalid keystore nonce length" }
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(KeystoreCryptoContract.AUTH_TAG_BYTES * 8, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedKeyStoreBlob(
            encryptedPayload = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            version = KeystoreCryptoContract.CURRENT_BLOB_VERSION
        )
    }

    actual suspend fun decryptSymmetric(blob: EncryptedKeyStoreBlob, password: String): String {
        return try {
            val salt = Base64.decode(blob.salt, Base64.NO_WRAP)
            val iv = Base64.decode(blob.iv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(blob.encryptedPayload, Base64.NO_WRAP)

            require(salt.size == KeystoreCryptoContract.SALT_BYTES) { "Invalid keystore salt length" }
            require(iv.size == KeystoreCryptoContract.NONCE_BYTES) { "Invalid keystore nonce length" }
            require(ciphertext.size >= KeystoreCryptoContract.AUTH_TAG_BYTES) { "Keystore ciphertext is shorter than the authentication tag" }
            require(blob.version >= KeystoreCryptoContract.LEGACY_BLOB_VERSION) { "Unsupported keystore blob version: ${blob.version}" }
            val secretKeyBytes = when (blob.version) {
                KeystoreCryptoContract.LEGACY_BLOB_VERSION -> legacyPbkdf2(password, salt)
                else -> KeystorePbkdf2.derive(password, salt)
            }
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(KeystoreCryptoContract.AUTH_TAG_BYTES * 8, iv))
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt keystore blob: invalid password or corrupted data", e)
        }
    }

    actual suspend fun encryptChannel(
        plainText: String,
        serverPublicKeyBase64: String,
        keyId: String
    ): Pair<ByteArray, connects.quotes.model.EncryptedChannelPayload> {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, secureRandom)
        val aesKey = keyGen.generateKey()
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        
        val reqBytes = connects.quotes.security.PlatformSecureRandom.nextBytes(16)
        val requestId = reqBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val aadString = "$keyId:$requestId"

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        aesCipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))
        val cipherTextBytes = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val cleanedKey = serverPublicKeyBase64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val mgf1ParameterSpec = java.security.spec.MGF1ParameterSpec("SHA-256")
        val oaepParameterSpec = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", mgf1ParameterSpec, javax.crypto.spec.PSource.PSpecified.DEFAULT)
        val keySpec = java.security.spec.X509EncodedKeySpec(android.util.Base64.decode(cleanedKey, android.util.Base64.NO_WRAP))
        val serverPubKey = keyFactory.generatePublic(keySpec)
        
        rsaCipher.init(Cipher.ENCRYPT_MODE, serverPubKey, oaepParameterSpec)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val payload = connects.quotes.model.EncryptedChannelPayload(
            keyId = keyId,
            requestId = requestId,
            encryptedAesKey = android.util.Base64.encodeToString(encryptedAesKey, android.util.Base64.NO_WRAP),
            iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
            cipherText = android.util.Base64.encodeToString(cipherTextBytes, android.util.Base64.NO_WRAP)
        )
        return Pair(aesKey.encoded, payload)
    }

    actual suspend fun decryptChannel(
        cipherTextBase64: String,
        ivBase64: String,
        aesKey: ByteArray,
        aad: String?
    ): String {
        return try {
            val iv = android.util.Base64.decode(ivBase64.trim(), android.util.Base64.NO_WRAP)
            val cipherText = android.util.Base64.decode(cipherTextBase64.trim(), android.util.Base64.NO_WRAP)
            val secretKey = SecretKeySpec(aesKey, "AES")
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aesCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            if (aad != null) aesCipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            val decryptedBytes = aesCipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionFailed("Failed to decrypt server channel response", e)
        }
    }
}
