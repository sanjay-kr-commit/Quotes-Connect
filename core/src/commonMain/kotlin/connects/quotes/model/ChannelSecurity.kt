package connects.quotes.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChannelSecurityMode(val label: String, val isEncrypted: Boolean) {
    TLS_SSL("SSL / TLS SECURED", true),
    ASYM_ENCRYPTED("ENCRYPTED CHANNEL", true),
    INSECURE("INSECURE HTTP", false),
    OFFLINE("OFFLINE", false)
}

@Serializable
data class ChannelHandshakeResponse(
    val keyId: String,
    val serverPublicKey: String,
    val algorithm: String = "RSA-2048/AES-256-GCM",
    val timestamp: Long
)

@Serializable
data class EncryptedChannelPayload(
    val keyId: String,
    val requestId: String,       // Unique monotonic or random ID to prevent replay attacks
    val encryptedAesKey: String, // AES-256 key encrypted with Server's RSA Public Key (Base64)
    val iv: String,              // 12-byte initialization vector for AES-GCM (Base64)
    val cipherText: String       // AES-GCM encrypted payload (Base64)
)

@Serializable
data class EncryptedChannelResponse(
    val requestId: String,       // Request ID echoed back
    val iv: String,              // 12-byte initialization vector for AES-GCM (Base64)
    val cipherText: String       // AES-GCM encrypted response using the shared request AES key (Base64)
)
