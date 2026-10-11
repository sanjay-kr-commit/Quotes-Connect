package connects.quotes.crypto

/**
 * Explicit lifecycle state of the local chat keystore.
 *
 * This type intentionally contains no persistence or cryptographic behavior.
 * It is the state-machine contract used by the keystore implementation.
 */
enum class KeystoreState {
    ABSENT,
    AVAILABLE,
    LOCKED,
    CORRUPT,
    UNSUPPORTED,
    UNAVAILABLE,
}

enum class KeystoreTransition {
    CREATE,
    LOCK,
    UNLOCK,
    DELETE,
}

/**
 * Applies one lifecycle transition. Invalid transitions are rejected rather
 * than silently coercing the keystore into another state.
 */
fun KeystoreState.transition(transition: KeystoreTransition): KeystoreState = when (this) {
    KeystoreState.ABSENT -> when (transition) {
        KeystoreTransition.CREATE -> KeystoreState.AVAILABLE
        else -> error("Invalid keystore transition: $this + $transition")
    }

    KeystoreState.AVAILABLE -> when (transition) {
        KeystoreTransition.LOCK -> KeystoreState.LOCKED
        KeystoreTransition.DELETE -> KeystoreState.ABSENT
        else -> error("Invalid keystore transition: $this + $transition")
    }

    KeystoreState.LOCKED -> when (transition) {
        KeystoreTransition.UNLOCK -> KeystoreState.AVAILABLE
        KeystoreTransition.DELETE -> KeystoreState.ABSENT
        else -> error("Invalid keystore transition: $this + $transition")
    }

    KeystoreState.CORRUPT,
    KeystoreState.UNSUPPORTED,
    KeystoreState.UNAVAILABLE -> when (transition) {
        KeystoreTransition.DELETE -> KeystoreState.ABSENT
        else -> error("Invalid keystore transition: $this + $transition")
    }
}
