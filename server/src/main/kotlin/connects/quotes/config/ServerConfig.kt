package connects.quotes.config

import connects.quotes.database.DatabaseConfig
import connects.quotes.database.DatabaseMode
import io.ktor.network.tls.certificates.*
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.Properties

/**
 * Runtime transport protocol.
 */
enum class ServerProtocol {
    HTTP,
    HTTPS;

    val scheme: String get() = name.lowercase()
}

/**
 * TLS material used when HTTPS is enabled.
 *
 * Secrets are intentionally represented as configuration values only. The
 * resolved configuration must never be logged in full.
 */
data class SslConfig(
    val keyStorePath: String? = null,
    val keyStorePassword: String = "quotesconnect",
    val keyAlias: String = "quotesconnect",
    val keyPassword: String = "quotesconnect"
) {
    data class ResolvedKeyStore(
        val keyStore: KeyStore,
        val keyAlias: String,
        val keyStorePassword: String,
        val keyPassword: String
    )

    fun resolveKeyStore(): ResolvedKeyStore {
        if (!keyStorePath.isNullOrBlank()) {
            val file = File(keyStorePath)
            require(file.isFile) { "Configured TLS keystore does not exist: $keyStorePath" }

            val storeType =
                if (keyStorePath.endsWith(".p12", ignoreCase = true) ||
                    keyStorePath.endsWith(".pfx", ignoreCase = true)
                ) "PKCS12" else "JKS"

            val ks = KeyStore.getInstance(storeType)
            FileInputStream(file).use { fis ->
                ks.load(fis, keyStorePassword.toCharArray())
            }
            require(ks.containsAlias(keyAlias)) {
                "Configured TLS keystore does not contain alias '$keyAlias'"
            }
            return ResolvedKeyStore(ks, keyAlias, keyStorePassword, keyPassword)
        }

        // Development-only convenience. Production deployments should mount
        // an explicit keystore and password through the deployment environment.
        val defaultDir = File(System.getProperty("user.dir"), ".quotes_connect")
        defaultDir.mkdirs()
        val defaultKeyStoreFile = File(defaultDir, "server_dev_keystore.jks")

        val ks = if (defaultKeyStoreFile.exists() && defaultKeyStoreFile.length() > 0) {
            try {
                KeyStore.getInstance("JKS").also { existing ->
                    FileInputStream(defaultKeyStoreFile).use { fis ->
                        existing.load(fis, keyStorePassword.toCharArray())
                    }
                }
            } catch (_: Exception) {
                generateCertificate(
                    file = defaultKeyStoreFile,
                    keyAlias = keyAlias,
                    keyPassword = keyPassword,
                    jksPassword = keyStorePassword
                )
            }
        } else {
            generateCertificate(
                file = defaultKeyStoreFile,
                keyAlias = keyAlias,
                keyPassword = keyPassword,
                jksPassword = keyStorePassword
            )
        }

        return ResolvedKeyStore(ks, keyAlias, keyStorePassword, keyPassword)
    }

    companion object {
        fun defaultDevConfig(): SslConfig = SslConfig()
    }
}

/**
 * Server deployment configuration.
 *
 * Precedence:
 *   built-in defaults -> properties file -> QUOTES_* environment variables.
 *
 * The legacy environment names are still accepted so existing deployments do
 * not break while the QUOTES_* namespace becomes the canonical interface.
 */
data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val protocol: ServerProtocol = ServerProtocol.HTTP,
    val sslConfig: SslConfig? = null,
    val databaseConfig: DatabaseConfig
) {
    companion object {
        private const val DEFAULT_CONFIG_RESOURCE = "/server.properties"

        fun fromEnv(): ServerConfig {
            val properties = loadProperties()

            fun value(canonical: String, vararg legacy: String): String? =
                System.getenv(canonical)?.takeIf { it.isNotBlank() }
                    ?: legacy.asSequence()
                        .mapNotNull { System.getenv(it) }
                        .firstOrNull { it.isNotBlank() }
                    ?: properties.getProperty(canonical.removePrefix("QUOTES_").lowercase().replace('_', '.'))
                        ?.takeIf { it.isNotBlank() }

            fun bool(canonical: String, default: Boolean, vararg legacy: String): Boolean =
                value(canonical, *legacy)?.trim()?.lowercase()?.let {
                    it == "true" || it == "1" || it == "yes" || it == "on"
                } ?: default

            val protocolRaw = value("QUOTES_SERVER_PROTOCOL", "SERVER_PROTOCOL", "PROTOCOL")
                ?.trim()?.uppercase()

            val httpsEnabled = bool(
                "QUOTES_TLS_ENABLED",
                default = protocolRaw == null && false,
                "SSL_ENABLED",
                "SERVER_SSL_ENABLED",
                "HTTPS_ENABLED"
            )

            val protocol = when {
                protocolRaw == "HTTPS" -> ServerProtocol.HTTPS
                protocolRaw == "HTTP" -> ServerProtocol.HTTP
                httpsEnabled -> ServerProtocol.HTTPS
                else -> ServerProtocol.HTTP
            }

            val defaultPort = if (protocol == ServerProtocol.HTTPS) 8443 else 8080
            val port = value("QUOTES_SERVER_PORT", "SERVER_PORT", "PORT")
                ?.toIntOrNull() ?: defaultPort

            val host = value("QUOTES_SERVER_HOST", "SERVER_HOST", "HOST") ?: "0.0.0.0"

            val sslConfig = if (protocol == ServerProtocol.HTTPS) {
                SslConfig(
                    keyStorePath = value("QUOTES_TLS_KEYSTORE", "SSL_KEYSTORE_PATH"),
                    keyStorePassword = value("QUOTES_TLS_KEYSTORE_PASSWORD", "SSL_KEYSTORE_PASSWORD")
                        ?: "quotesconnect",
                    keyAlias = value("QUOTES_TLS_KEY_ALIAS", "SSL_KEY_ALIAS") ?: "quotesconnect",
                    keyPassword = value("QUOTES_TLS_KEY_PASSWORD", "SSL_KEY_PASSWORD")
                        ?: value("QUOTES_TLS_KEYSTORE_PASSWORD", "SSL_KEYSTORE_PASSWORD")
                        ?: "quotesconnect"
                )
            } else null

            val modeRaw = value("QUOTES_DATABASE_MODE", "SERVER_MODE")
                ?.uppercase() ?: "DEMO"

            val databaseConfig = when (modeRaw) {
                "PRODUCTION", "MYSQL" -> {
                    val url = value("QUOTES_DATABASE_URL", "DB_URL")
                        ?: "jdbc:mysql://localhost:3306/quotes_connect?useSSL=false&allowPublicKeyRetrieval=true"
                    val user = value("QUOTES_DATABASE_USERNAME", "DB_USER") ?: "root"
                    val password = value("QUOTES_DATABASE_PASSWORD", "DB_PASSWORD") ?: ""
                    DatabaseConfig.production(url, user, password)
                }

                "DEMO", "SQLITE" -> {
                    val rawPath = value("QUOTES_SQLITE_PATH", "SQLITE_PATH") ?: "quotes_connect.db"
                    val file = File(rawPath).let {
                        if (it.isAbsolute) it else File(System.getProperty("user.dir"), rawPath)
                    }
                    DatabaseConfig(
                        mode = DatabaseMode.DEMO,
                        sqliteFilePath = file.canonicalPath
                    )
                }

                else -> error("Unsupported database mode '$modeRaw'. Use SQLITE/DEMO or MYSQL/PRODUCTION.")
            }

            return ServerConfig(
                port = port,
                host = host,
                protocol = protocol,
                sslConfig = sslConfig,
                databaseConfig = databaseConfig
            )
        }

        private fun loadProperties(): Properties {
            val result = Properties()

            // External file wins over the packaged defaults, but environment
            // variables still win over both.
            val externalPath = System.getenv("QUOTES_CONFIG_FILE")
                ?.takeIf { it.isNotBlank() }
                ?: System.getProperty("quotes.config.file")

            if (externalPath != null) {
                val file = File(externalPath)
                require(file.isFile) { "Configured server config file does not exist: $externalPath" }
                FileInputStream(file).use(result::load)
                return result
            }

            ServerConfig::class.java.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)?.use(result::load)
            return result
        }
    }
}
