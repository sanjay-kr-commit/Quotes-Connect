package connects.quotes.crypto

/**
 * Local identity-key lifecycle facade backed by the encrypted platform store.
 *
 * The password is required whenever encrypted durable state must be created or
 * restored. No plaintext private key is written to durable storage.
 */
expect object LocalChatKeyStore {
    suspend fun hasKeyPair(authorId: String): Boolean
    /** Loads the active identity, or decrypts the durable identity when a password is supplied. */
    suspend fun loadKeyPair(authorId: String, password: String? = null): CryptoKeyPair?
    /** Returns only the currently unlocked in-memory identity. */
    suspend fun getKeyPair(authorId: String): CryptoKeyPair?
    suspend fun getOrGenerateKeyPair(authorId: String, password: String? = null): CryptoKeyPair
    suspend fun saveKeyPair(authorId: String, keyPair: CryptoKeyPair, password: String? = null)
    suspend fun lock(authorId: String)
    suspend fun unlock(authorId: String, password: String)
    /** Imports an already-encrypted envelope without decrypting or re-encrypting it. */
    suspend fun restoreEncryptedBlob(authorId: String, blob: EncryptedKeyStoreBlob)
    suspend fun deleteKeyPair(authorId: String)
    suspend fun state(authorId: String): KeystoreState
    /** Marks the local keystore state corrupt without deleting its durable blob. */
    suspend fun markCorrupt(authorId: String)
}
