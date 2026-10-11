package connects.quotes.config

/**
 * Single shared client/server default configuration.
 *
 * This is deliberately a Kotlin source-level configuration object so every
 * Kotlin target consumes the same defaults instead of embedding localhost or
 * port literals in platform code.
 */
object QuotesConnectDefaults {
    const val DEFAULT_SERVER_HOST: String = "localhost"
    const val DEFAULT_SERVER_PORT: Int = 8080
    const val DEFAULT_SERVER_USE_SSL: Boolean = false
    const val DEFAULT_SERVER_PROTOCOL: String = "http"
}
