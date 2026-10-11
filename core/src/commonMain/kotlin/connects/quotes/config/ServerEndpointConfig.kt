package connects.quotes.config

import kotlinx.serialization.Serializable

/**
 * Universal Server Endpoint configuration for Quotes Connect.
 * Enables the client (Desktop, Web, Android) to connect to any server instance,
 * whether local (localhost:8080), LAN IP (e.g. 192.168.1.100:8080), or remote domain.
 */
@Serializable
data class ServerEndpointConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val useSsl: Boolean = false
) {
    val baseUrl: String
        get() {
            val scheme = if (useSsl) "https" else "http"
            val isStandardPort = (useSsl && port == 443) || (!useSsl && port == 80)
            return if (isStandardPort) "$scheme://$host" else "$scheme://$host:$port"
        }

    val graphqlUrl: String
        get() = "$baseUrl/graphql"

    val statusUrl: String
        get() = "$baseUrl/status"

    fun isLocalhost(): Boolean {
        return host.equals("localhost", ignoreCase = true) ||
                host == "127.0.0.1" ||
                host == "::1" ||
                host == "0.0.0.0"
    }

    val isSecureTransport: Boolean
        get() = useSsl || port == 443 || port == 8443 || baseUrl.startsWith("https://", ignoreCase = true)

    companion object {
        const val DEFAULT_HOST = QuotesConnectDefaults.DEFAULT_SERVER_HOST
        const val DEFAULT_PORT = QuotesConnectDefaults.DEFAULT_SERVER_PORT

        val DEFAULT = ServerEndpointConfig(
            host = DEFAULT_HOST,
            port = DEFAULT_PORT,
            useSsl = QuotesConnectDefaults.DEFAULT_SERVER_USE_SSL
        )

        /**
         * Parses a user-supplied input (IP, hostname, URL, with or without port and protocol).
         * Examples:
         * - "192.168.1.50" -> host="192.168.1.50", port=8080
         * - "192.168.1.50:8080" -> host="192.168.1.50", port=8080
         * - "http://10.0.2.2:8080" -> host="10.0.2.2", port=8080 (Android emulator host)
         * - "https://server.domain.com:8443/graphql" -> host="server.domain.com", port=8443, ssl=true
         */
        fun fromInput(input: String): ServerEndpointConfig {
            var raw = input.trim()
            if (raw.isBlank()) return DEFAULT

            val hasHttpsPrefix = raw.startsWith("https://", ignoreCase = true)
            val hasHttpPrefix = raw.startsWith("http://", ignoreCase = true)
            var useSsl = hasHttpsPrefix
            raw = raw.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")

            // Strip path if provided (e.g. /graphql, /status)
            val pathIndex = raw.indexOf('/')
            if (pathIndex != -1) {
                raw = raw.substring(0, pathIndex)
            }

            raw = raw.trim()
            if (raw.isBlank()) return DEFAULT

            val colonIndex = raw.lastIndexOf(':')
            val defaultPort = if (useSsl) 443 else DEFAULT_PORT

            return if (colonIndex != -1 && colonIndex < raw.length - 1) {
                val hostPart = raw.substring(0, colonIndex).trim()
                val portPart = raw.substring(colonIndex + 1).trim()
                val port = portPart.toIntOrNull() ?: defaultPort
                // Detect secure transport via port 443 / 8443 or https scheme
                val detectedSsl = useSsl || (!hasHttpPrefix && (port == 443 || port == 8443))
                ServerEndpointConfig(
                    host = hostPart.ifBlank { DEFAULT_HOST },
                    port = port,
                    useSsl = detectedSsl
                )
            } else {
                ServerEndpointConfig(
                    host = raw,
                    port = defaultPort,
                    useSsl = useSsl
                )
            }
        }
    }
}
