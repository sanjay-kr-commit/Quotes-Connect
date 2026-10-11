package connects.quotes.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

actual fun createPlatformHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 5000
        connectTimeoutMillis = 4000
        socketTimeoutMillis = 5000
    }
}
