package connects.quotes.client

import io.ktor.client.HttpClient

/**
 * Platform-specific factory for HttpClient to ensure the correct engine (e.g. CIO on JVM/Android)
 * is instantiated directly without relying on ServiceLoader reflection discovery.
 */
expect fun createPlatformHttpClient(): HttpClient
