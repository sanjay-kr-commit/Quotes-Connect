package connects.quotes.config

import java.io.File

actual object PlatformServerEndpointStore {
    private fun file(): File {
        val dir = File(System.getProperty("user.home") ?: ".", ".quotes_connect")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "server_endpoint.txt")
    }

    actual fun load(): ServerEndpointConfig? = try {
        val f = file()
        if (!f.exists()) null else ServerEndpointConfig.fromInput(f.readText())
    } catch (_: Exception) { null }

    actual fun save(config: ServerEndpointConfig) {
        file().writeText(config.baseUrl)
    }

    actual fun clear() { file().takeIf { it.exists() }?.delete() }
}
