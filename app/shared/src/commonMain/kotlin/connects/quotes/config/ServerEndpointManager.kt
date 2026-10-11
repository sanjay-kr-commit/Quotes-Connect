package connects.quotes.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide client endpoint state. Changes are persisted by the platform store
 * so Android, Desktop and browser clients keep the user's selected server.
 */
object ServerEndpointManager {
    private val _currentEndpoint = MutableStateFlow(
        PlatformServerEndpointStore.load() ?: ServerEndpointConfig.DEFAULT
    )
    val currentEndpoint: StateFlow<ServerEndpointConfig> = _currentEndpoint.asStateFlow()

    fun get(): ServerEndpointConfig = _currentEndpoint.value

    fun update(config: ServerEndpointConfig) {
        _currentEndpoint.value = config
        PlatformServerEndpointStore.save(config)
    }

    fun updateFromInput(input: String): ServerEndpointConfig {
        val config = ServerEndpointConfig.fromInput(input)
        update(config)
        return config
    }

    /** Reload after a platform store has been initialized, e.g. Android Context. */
    fun reloadPersisted() {
        _currentEndpoint.value = PlatformServerEndpointStore.load() ?: ServerEndpointConfig.DEFAULT
    }

    fun resetToDefault() {
        _currentEndpoint.value = ServerEndpointConfig.DEFAULT
        PlatformServerEndpointStore.clear()
    }
}
