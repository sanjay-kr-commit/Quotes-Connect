package connects.quotes.crypto

/**
 * Platform-backed PBKDF2-HMAC-SHA256 derivation for the local keystore.
 *
 * The input contract is deliberately narrow: a password string, a 16-byte salt,
 * and the shared keystore crypto parameters. Implementations must derive exactly
 * KeystoreCryptoContract.DERIVED_KEY_BYTES bytes using the exact iteration count
 * from KeystoreCryptoContract.
 */
expect object KeystorePbkdf2 {
    suspend fun derive(password: String, salt: ByteArray): ByteArray
}
