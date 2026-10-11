package connects.quotes.config

/** Platform persistence for the selected server endpoint. */
expect object PlatformServerEndpointStore {
    fun load(): ServerEndpointConfig?
    fun save(config: ServerEndpointConfig)
    fun clear()
}
