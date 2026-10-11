package connects.quotes.routes

import connects.quotes.config.ServerConfig
import connects.quotes.graphql.GraphiqlHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Registers static web assets, health checks, network info, and GraphiQL explorer routes.
 */
fun Route.webRoutes(config: ServerConfig) {
    get("/") {
        call.respondText(connects.quotes.web.QuotesWebUi.html, ContentType.Text.Html)
    }

    // Static icons, favicons, and web manifest for browser and web clients
    get("/{resource}") {
        val resource = call.parameters["resource"] ?: return@get call.respond(HttpStatusCode.NotFound)
        val allowedExtensions = listOf(".ico", ".svg", ".png", ".webmanifest", ".json")
        if (allowedExtensions.any { resource.endsWith(it) }) {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("web/$resource")
            if (stream != null) {
                val contentType = when {
                    resource.endsWith(".ico") -> ContentType.Image.XIcon
                    resource.endsWith(".svg") -> ContentType.parse("image/svg+xml")
                    resource.endsWith(".png") -> ContentType.Image.PNG
                    resource.endsWith(".webmanifest") -> ContentType.parse("application/manifest+json")
                    else -> ContentType.Application.OctetStream
                }
                call.respondBytes(stream.readBytes(), contentType)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/status") {
        call.respondText(
            """
            === Quotes Connect Knowledge Network ===
            Protocol: ${config.protocol.name}
            Mode: ${config.databaseConfig.mode}
            Port: ${config.port}
            Host: ${config.host}
            GraphQL Endpoint: /graphql
            Interactive GraphiQL Explorer: /graphiql
            Status: ONLINE
            """.trimIndent(),
            ContentType.Text.Plain
        )
    }

    get("/server-info") {
        val lanIp = getLanIpAddress()
        val scheme = config.protocol.scheme
        call.respondText(
            """
            {
              "status": "ONLINE",
              "protocol": "${config.protocol.name}",
              "port": ${config.port},
              "localUrl": "$scheme://localhost:${config.port}",
              "lanUrl": "$scheme://$lanIp:${config.port}",
              "graphql": "/graphql"
            }
            """.trimIndent(),
            ContentType.Application.Json
        )
    }

    // Interactive GraphiQL UI
    get("/graphiql") {
        call.respondText(GraphiqlHtml.content, ContentType.Text.Html)
    }

    get("/graphql") {
        call.respondText(GraphiqlHtml.content, ContentType.Text.Html)
    }

    // Global real-time SSE event stream for adaptive and fluent UI updates (no manual refresh needed)
    get("/api/events") {
        val channel = connects.quotes.chat.LiveChatHub.registerEventListener()
        call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val initial = connects.quotes.model.LiveChatPacket(
                    type = "CONNECTED",
                    timestamp = System.currentTimeMillis()
                )
                write("data: ${kotlinx.serialization.json.Json.encodeToString(initial)}\n\n")
                flush()

                while (kotlin.coroutines.coroutineContext.isActive) {
                    val packet = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                        channel.receive()
                    }
                    if (packet != null) {
                        write("data: ${kotlinx.serialization.json.Json.encodeToString(packet)}\n\n")
                        flush()
                    } else {
                        write(":keepalive\n\n")
                        flush()
                    }
                }
            }
        } catch (_: Throwable) {
            // Client closed stream
        } finally {
            connects.quotes.chat.LiveChatHub.unregisterEventListener(channel)
        }
    }
}

/**
 * Resolves the primary LAN IPv4 address for local network access (e.g. from Android devices).
 */
fun getLanIpAddress(): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        var candidate: String? = null
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (iface.isLoopback || !iface.isUp) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        return host
                    }
                    if (candidate == null) candidate = host
                }
            }
        }
        candidate ?: "127.0.0.1"
    } catch (_: Exception) {
        "127.0.0.1"
    }
}
