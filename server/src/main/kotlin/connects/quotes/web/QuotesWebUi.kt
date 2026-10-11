package connects.quotes.web

/**
 * Loads the single-page web UI served by the Ktor server from resources.
 */
object QuotesWebUi {
    val html: String by lazy {
        val stream = QuotesWebUi::class.java.classLoader.getResourceAsStream("web/index.html")
        stream?.bufferedReader()?.use { it.readText() } ?: """
            <!DOCTYPE html><html><body><h1>Quotes Connect Server Online</h1></body></html>
        """.trimIndent()
    }
}
