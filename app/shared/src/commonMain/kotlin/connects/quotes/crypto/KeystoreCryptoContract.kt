package connects.quotes.crypto

/**
 * Versioned, cross-platform cryptographic contract for encrypted local keystores.
 *
 * This file defines the versioned parameters used by the local encrypted-keystore
 * encryption/decryption implementations.
 */
object KeystoreCryptoContract {
    const val VERSION = 1

    const val KDF = "PBKDF2-HMAC-SHA256"
    const val KDF_ITERATIONS = 600_000
    const val LEGACY_BLOB_VERSION = 2
    const val CURRENT_BLOB_VERSION = 3
    const val DERIVED_KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val LEGACY_KDF_ITERATIONS = 100_000

    const val CIPHER = "AES-256-GCM"
    const val NONCE_BYTES = 12
    const val AUTH_TAG_BYTES = 16

    const val KEY_FORMAT = "UTF-8 password -> PBKDF2-HMAC-SHA256 -> AES-256-GCM"
}
