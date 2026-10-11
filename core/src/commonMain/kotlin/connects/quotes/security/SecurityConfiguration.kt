package connects.quotes.security

/**
 * Canonical security parameter registry.
 *
 * This object is the single source of truth for security parameters that are
 * already implemented by the product. It intentionally does not change the
 * algorithms or protocol behavior. Later stages may change individual
 * protocols only through an explicit migration.
 */
object SecurityConfiguration {
    const val CONFIGURATION_VERSION = 1

    // Local encrypted keystore contract.
    const val KEYSTORE_KDF = "PBKDF2-HMAC-SHA256"
    const val KEYSTORE_KDF_ITERATIONS = 600_000
    const val KEYSTORE_LEGACY_KDF_ITERATIONS = 100_000
    const val KEYSTORE_DERIVED_KEY_BYTES = 32
    const val KEYSTORE_SALT_BYTES = 16
    const val KEYSTORE_CIPHER = "AES-256-GCM"
    const val KEYSTORE_NONCE_BYTES = 12
    const val KEYSTORE_AUTH_TAG_BYTES = 16
    const val KEYSTORE_LEGACY_BLOB_VERSION = 2
    const val KEYSTORE_CURRENT_BLOB_VERSION = 3

    // Existing server credential-verifier parameter. Kept separate from the
    // keystore legacy compatibility value because they are different protocols.
    const val AUTH_PASSWORD_KDF_ITERATIONS = 100_000

    // Shared cryptographic primitives used by the current peer-exchange code.
    const val ECDH_CURVE = "P-256"
    const val ECDH_JAVA_CURVE = "secp256r1"
    const val HKDF_HASH = "SHA-256"
    const val HMAC_ALGORITHM = "HmacSHA256"
    const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    const val WEB_SIGNATURE_ALGORITHM = "RSASSA-PKCS1-v1_5"
    const val RSA_KEY_BITS = 2048
    const val RSA_OAEP_HASH = "SHA-256"
    const val RSA_OAEP_MGF1_HASH = "SHA-256"
    const val AES_KEY_BYTES = 32
    const val AES_GCM_NONCE_BYTES = 12
    const val AES_GCM_AUTH_TAG_BYTES = 16

    // Current authenticated peer key-exchange protocol identifiers.
    const val KEY_EXCHANGE_PROTOCOL_VERSION = 1
    const val KEY_EXCHANGE_SUITE_ID = "QC-ECDH-P256-HKDF-SHA256-RSA-SHA256"
    const val KEY_EXCHANGE_TRANSCRIPT_LABEL = "QC-KEYEXCHANGE-V1"
    const val KEY_EXCHANGE_CONFIRM_LABEL = "QC-KEYEXCHANGE-V1-CONFIRM"
    const val KEY_EXCHANGE_SESSION_KEY_BYTES = 32

    // Existing hybrid channel/envelope identifiers. These are retained here so
    // callers do not invent another spelling of an existing suite.
    const val RSA_HYBRID_SUITE_ID = "QC-RSA2048-AES256GCM-OAEP"
    const val CHANNEL_HYBRID_SUITE_ID = "QC-CHANNEL-RSA2048-AES256GCM"
}
