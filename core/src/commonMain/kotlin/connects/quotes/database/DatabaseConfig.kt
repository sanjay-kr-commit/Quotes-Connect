package connects.quotes.database

/**
 * Operating mode for the database connection.
 * - DEMO: In-process SQLite database (standalone, zero-config, portable).
 * - PRODUCTION: External SQL database (e.g. MySQL) for high-scale deployment.
 */
enum class DatabaseMode {
    DEMO,
    PRODUCTION
}

/**
 * Configuration settings for connecting to the database.
 */
data class DatabaseConfig(
    val mode: DatabaseMode = DatabaseMode.DEMO,
    val sqliteFilePath: String = "quotes_connect.db",
    val jdbcUrl: String? = null,
    val dbUser: String? = null,
    val dbPassword: String? = null
) {
    companion object {
        fun demo(): DatabaseConfig = DatabaseConfig(mode = DatabaseMode.DEMO)

        fun production(jdbcUrl: String, user: String, pass: String): DatabaseConfig = DatabaseConfig(
            mode = DatabaseMode.PRODUCTION,
            jdbcUrl = jdbcUrl,
            dbUser = user,
            dbPassword = pass
        )
    }
}
