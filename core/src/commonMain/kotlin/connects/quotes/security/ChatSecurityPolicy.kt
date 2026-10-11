package connects.quotes.security

import connects.quotes.model.ChatMessage
import connects.quotes.model.ChatSession
import connects.quotes.model.ChatType

sealed interface SecurityDecision {
    data object Allowed : SecurityDecision
    data class Rejected(val reason: String) : SecurityDecision
}

/**
 * Domain-level policy asserting hard invariants for the three Quotes Connect chat modes:
 *
 * 1. UNENCRYPTED_CLOUD:
 *    Persistent cloud conversation. Server stores plaintext. Transport protected by TLS.
 *
 * 2. ENCRYPTED_CLOUD:
 *    Persistent cloud conversation. Server stores ciphertext ONLY. Server cannot decrypt.
 *    Plaintext content is strictly forbidden and rejected.
 *
 * 3. ENCRYPTED_ON_DEVICE:
 *    Device-only encrypted conversation. Server message persistence is strictly FORBIDDEN.
 *    No offline cloud delivery.
 */
object ChatSecurityPolicy {

    fun validateSend(session: ChatSession, message: ChatMessage): SecurityDecision {
        return when (session.type) {
            ChatType.UNENCRYPTED -> {
                // Persistent cloud chat; plaintext is intentional and permitted
                SecurityDecision.Allowed
            }
            ChatType.ENCRYPTED_CLOUD -> {
                // Persistent cloud chat, but server stores ciphertext ONLY
                if (!message.isEncrypted) {
                    SecurityDecision.Rejected("ENCRYPTED_CLOUD requires application-level encryption. Unencrypted plaintext messages are strictly forbidden.")
                } else if (!message.content.startsWith("ASYM:") && !message.content.startsWith("{") && message.content.length < 16) {
                    SecurityDecision.Rejected("ENCRYPTED_CLOUD message content must be a valid cryptographic envelope.")
                } else {
                    SecurityDecision.Allowed
                }
            }
            ChatType.ENCRYPTED -> {
                // Device-only encrypted chat; server database persistence is forbidden
                if (!message.isEncrypted) {
                    SecurityDecision.Rejected("ENCRYPTED device-only sessions require end-to-end ciphertext.")
                } else {
                    SecurityDecision.Allowed
                }
            }
        }
    }
}
