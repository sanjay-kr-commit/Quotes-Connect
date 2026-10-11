package connects.quotes.config

import android.content.Context

actual object PlatformServerEndpointStore {
    private const val PREFS_NAME = "quotes_connect_config"
    private const val KEY_SERVER_ENDPOINT = "server_endpoint"
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    actual fun load(): ServerEndpointConfig? {
        val ctx = appContext ?: return null
        val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_ENDPOINT, null) ?: return null
        return try { ServerEndpointConfig.fromInput(raw) } catch (_: Exception) { null }
    }

    actual fun save(config: ServerEndpointConfig) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_SERVER_ENDPOINT, config.baseUrl)?.apply()
    }

    actual fun clear() {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_SERVER_ENDPOINT)?.apply()
    }
}
