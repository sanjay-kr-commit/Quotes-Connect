package connects.quotes

import connects.quotes.config.ServerConfig
import connects.quotes.config.ServerProtocol
import connects.quotes.config.SslConfig
import connects.quotes.database.JdbcPassageRepository
import connects.quotes.database.ServerDatabase
import connects.quotes.graphql.GraphQLGateway
import connects.quotes.graphql.GraphQLRequest
import connects.quotes.routes.authRoutes
import connects.quotes.routes.authorRoutes
import connects.quotes.routes.chatRoutes
import connects.quotes.routes.getLanIpAddress
import connects.quotes.routes.webRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val config = ServerConfig.fromEnv()
    val lanIp = getLanIpAddress()
    val scheme = config.protocol.scheme

    val banner = """
        ======================================================================
                         QUOTES CONNECT KNOWLEDGE SERVER
               Algorithm-Free Sovereign Knowledge Graph Platform
        ======================================================================
          • Protocol Mode:          ${config.protocol.name} (${scheme.uppercase()})
          • Local Web & API:        $scheme://localhost:${config.port}/
          • Network / LAN Address:   $scheme://$lanIp:${config.port}/
          • Interactive Explorer:   $scheme://localhost:${config.port}/graphiql
          • GraphQL Endpoint:       $scheme://localhost:${config.port}/graphql
          • Status Health Check:    $scheme://localhost:${config.port}/status
        ----------------------------------------------------------------------
          Client Connection Guide:
          - Web Browser:            Access $scheme://localhost:${config.port}/ directly
                                    (Uses relative pulls; no IP setup needed)
          - Desktop Client:         Connect to $scheme://localhost:${config.port}
          - Android Emulator:       Connect to $scheme://10.0.2.2:${config.port}
          - Android Device (LAN):   Connect to $scheme://$lanIp:${config.port}
        ======================================================================
    """.trimIndent()
    println(banner)

    embeddedServer(
        factory = Netty,
        environment = applicationEnvironment {},
        configure = {
            if (config.protocol == ServerProtocol.HTTPS) {
                val ssl = config.sslConfig ?: SslConfig.defaultDevConfig()
                val resolved = ssl.resolveKeyStore()
                sslConnector(
                    keyStore = resolved.keyStore,
                    keyAlias = resolved.keyAlias,
                    keyStorePassword = { resolved.keyStorePassword.toCharArray() },
                    privateKeyPassword = { resolved.keyPassword.toCharArray() }
                ) {
                    this.port = config.port
                    this.host = config.host
                }
            } else {
                connector {
                    this.port = config.port
                    this.host = config.host
                }
            }
        }
    ) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig = ServerConfig.fromEnv()) {
    val logger = LoggerFactory.getLogger("QuotesConnectServer")
    logger.info("Quotes Connect Server active in ${config.protocol.name} mode (${config.databaseConfig.mode} storage) on port ${config.port}")

    val database = ServerDatabase(config.databaseConfig)
    val repository = JdbcPassageRepository(database)
    val gateway = GraphQLGateway(repository)

    // Periodic server-side inactivity purge worker
    launch(Dispatchers.IO) {
        while (isActive) {
            delay(15 * 60 * 1000L) // every 15 minutes
            try {
                repository.performInactivityCheck(System.currentTimeMillis())
            } catch (e: Exception) {
                logger.error("Error during scheduled inactivity check", e)
            }
        }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        })
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 45.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        // Sovereign author authentication and session management
        authRoutes(repository)

        // Static web assets, health checks, and GraphiQL explorer
        webRoutes(config)

        // Real-time live TCP streaming chat, typing, and notifications
        chatRoutes(repository)

        // Author settings, activity logging, and inactivity purge sweep
        authorRoutes(repository)

        // Global tags autocomplete and top tags endpoint
        get("/api/tags") {
            val query = call.request.queryParameters["query"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
            val tags = if (query.isNullOrBlank()) {
                repository.getTopTags(limit)
            } else {
                repository.suggestTags(query, limit)
            }
            call.respond(tags)
        }

        val channelJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        // Ephemeral Asymmetric Channel Handshake (for insecure HTTP networks)
        get("/api/channel/handshake") {
            call.respond(connects.quotes.crypto.ServerChannelCrypto.getHandshake())
        }

        // Dedicated Asymmetric Encrypted Channel endpoint
        post("/api/channel/secure") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val bearerToken = authHeader?.removePrefix("Bearer ")?.trim()
            val principal = if (!bearerToken.isNullOrBlank()) repository.validateSession(bearerToken) else null

            val envelope = call.receive<connects.quotes.model.EncryptedChannelPayload>()
            val (rawAesKey, plainQueryJson) = connects.quotes.crypto.ServerChannelCrypto.decryptRequest(envelope)
            val request = channelJson.decodeFromString<GraphQLRequest>(plainQueryJson)
            val response = gateway.execute(request, principal)
            val responseJson = channelJson.encodeToString(
                connects.quotes.graphql.GraphQLResponse.serializer(),
                response
            )
            val encryptedResp = connects.quotes.crypto.ServerChannelCrypto.encryptResponse(rawAesKey, responseJson, envelope.requestId, envelope.keyId)
            call.response.header("X-Encrypted-Channel", "true")
            call.respond(encryptedResp)
        }

        // GraphQL API Gateway execution (supports both plain JSON and encrypted channel envelopes)
        post("/graphql") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            val bearerToken = authHeader?.removePrefix("Bearer ")?.trim()
            val principal = if (!bearerToken.isNullOrBlank()) repository.validateSession(bearerToken) else null

            val isEncryptedHeader = call.request.headers["X-Encrypted-Channel"]?.equals("true", ignoreCase = true) == true
            val rawText = call.receiveText()

            if (isEncryptedHeader || rawText.contains("\"encryptedAesKey\"")) {
                val envelope = channelJson.decodeFromString<connects.quotes.model.EncryptedChannelPayload>(rawText)
                val (rawAesKey, plainQueryJson) = connects.quotes.crypto.ServerChannelCrypto.decryptRequest(envelope)
                val request = channelJson.decodeFromString<GraphQLRequest>(plainQueryJson)
                val response = gateway.execute(request, principal)
                val responseJson = channelJson.encodeToString(
                    connects.quotes.graphql.GraphQLResponse.serializer(),
                    response
                )
                val encryptedResp = connects.quotes.crypto.ServerChannelCrypto.encryptResponse(rawAesKey, responseJson, envelope.requestId, envelope.keyId)
                call.response.header("X-Encrypted-Channel", "true")
                call.respond(encryptedResp)
            } else {
                val request = channelJson.decodeFromString<GraphQLRequest>(rawText)
                val response = gateway.execute(request, principal)
                call.respond(response)
            }
        }
    }
}