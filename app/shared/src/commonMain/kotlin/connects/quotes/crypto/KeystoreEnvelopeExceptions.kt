package connects.quotes.crypto

/** Raised when a keystore envelope version is not supported by this client. */
class UnsupportedKeystoreEnvelopeException(message: String) : IllegalArgumentException(message)

/** Raised when an envelope is structurally invalid. */
class InvalidKeystoreEnvelopeException(message: String) : IllegalArgumentException(message)
