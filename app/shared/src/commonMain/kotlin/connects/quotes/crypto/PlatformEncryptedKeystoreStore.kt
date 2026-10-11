package connects.quotes.crypto

/**
 * Durable storage boundary for already-encrypted keystore blobs.
 *
 * This layer deliberately knows nothing about passwords or cryptographic
 * algorithms. It must never be used to persist plaintext key material.
 */
expect object PlatformEncryptedKeystoreStore {
    suspend fun save(authorId: String, blob: EncryptedKeyStoreBlob): Boolean
    suspend fun load(authorId: String): EncryptedKeyStoreBlob?
    suspend fun delete(authorId: String): Boolean
}
