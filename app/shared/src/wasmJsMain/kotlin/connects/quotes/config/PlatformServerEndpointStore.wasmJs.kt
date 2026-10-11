package connects.quotes.config

actual object PlatformServerEndpointStore {
    private const val KEY = "qc_server_endpoint"
    actual fun load(): ServerEndpointConfig? = try {
        val raw: String? = js("window.localStorage.getItem(KEY)") as? String
        raw?.let(ServerEndpointConfig::fromInput)
    } catch (_: Throwable) { null }
    actual fun save(config: ServerEndpointConfig) { try { js("window.localStorage.setItem(KEY, config.baseUrl)") } catch (_: Throwable) {} }
    actual fun clear() { try { js("window.localStorage.removeItem(KEY)") } catch (_: Throwable) {} }
}
