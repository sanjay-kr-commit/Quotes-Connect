package connects.quotes.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val keystoreJson = Json { ignoreUnknownKeys = false }

internal suspend fun encryptLocalKeyPair(keyPair: CryptoKeyPair, password: String): EncryptedKeyStoreBlob {
    require(password.isNotBlank()) { "Keystore password must not be blank" }
    val plaintext = keystoreJson.encodeToString(keyPair)
    return PlatformCrypto.encryptSymmetric(plaintext, password)
}

internal suspend fun decryptLocalKeyPair(blob: EncryptedKeyStoreBlob, password: String): CryptoKeyPair {
    require(password.isNotBlank()) { "Keystore password must not be blank" }
    val plaintext = PlatformCrypto.decryptSymmetric(blob, password)
    return try {
        keystoreJson.decodeFromString<CryptoKeyPair>(plaintext)
    } catch (e: Exception) {
        throw InvalidKeystoreEnvelopeException("Decrypted keystore payload is not a valid identity key pair: ${e.message}")
    }
}

internal fun classifyKeystoreLoadFailure(error: Throwable): KeystoreState = when (error) {
    is UnsupportedKeystoreEnvelopeException -> KeystoreState.UNSUPPORTED
    is InvalidKeystoreEnvelopeException -> KeystoreState.CORRUPT
    is SerializationException -> KeystoreState.CORRUPT
    else -> KeystoreState.UNAVAILABLE
}

internal class KeystorePersistenceController {
    val mutex = Mutex()
}
