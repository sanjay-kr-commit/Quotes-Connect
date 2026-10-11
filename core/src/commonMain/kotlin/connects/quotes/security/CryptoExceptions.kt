package connects.quotes.security

/**
 * Root exception for all cryptographic operations.
 * Fails closed across all security boundaries.
 */
sealed class CryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when an operation is attempted on an unsupported or placeholder platform.
 * Strictly prevents silent plaintext fallback.
 */
class UnsupportedSecureOperation(message: String) : CryptoException(message)

/**
 * Thrown when the envelope structure or format is invalid.
 */
class MalformedEnvelope(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when message authentication fails (e.g. invalid AEAD tag, altered ciphertext or AAD).
 */
class AuthenticationFailed(message: String, cause: Throwable? = null) : CryptoException(message, cause)

/**
 * Thrown when a key is unknown, missing, expired, or revoked.
 */
class UnknownKey(message: String) : CryptoException(message)

/**
 * Thrown when a replayed message or sequence anomaly is detected.
 */
class ReplayDetected(message: String) : CryptoException(message)

/**
 * Generic decryption failure (never leaking sensitive plaintext or details).
 */
class DecryptionFailed(message: String, cause: Throwable? = null) : CryptoException(message, cause)
