package connects.quotes

import connects.quotes.chat.KeyExchangePacket
import connects.quotes.chat.KeyExchangePacketType
import connects.quotes.chat.KeyExchangeRelay
import connects.quotes.config.ServerConfig
import connects.quotes.database.DatabaseConfig
import connects.quotes.database.DatabaseMode
import connects.quotes.database.JdbcPassageRepository
import connects.quotes.database.ServerDatabase
import connects.quotes.graph.GraphEngine
import connects.quotes.model.*
import connects.quotes.security.ChatSecurityPolicy
import connects.quotes.security.SecurityAuditLogger
import connects.quotes.security.SecurityDecision
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

class SecurityHardeningAuditTest {

    private val testDbFile = "build/test_security_hardening.db"

    private val testConfig = ServerConfig(
        port = 8085,
        host = "localhost",
        databaseConfig = DatabaseConfig(
            mode = DatabaseMode.DEMO,
            sqliteFilePath = testDbFile
        )
    )

    @BeforeTest
    fun setup() {
        val file = File(testDbFile)
        if (file.exists()) file.delete()
    }

    @AfterTest
    fun tearDown() {
        val file = File(testDbFile)
        if (file.exists()) file.delete()
    }

    @Test
    fun testDatabaseSchemaHasNoPendingKeysOrPlaintextCredentials() {
        val db = ServerDatabase(DatabaseConfig(DatabaseMode.DEMO, testDbFile))

        db.getConnection().use { conn ->
            // 1. Invariant: pending_chat_keys table MUST NOT exist
            conn.prepareStatement("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='pending_chat_keys'").use { stmt ->
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "CRITICAL: pending_chat_keys table must be dropped from database!")
            }

            // 2. Invariant: legacy plaintext credentials table MUST NOT exist
            conn.prepareStatement("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='credentials'").use { stmt ->
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "CRITICAL: Plaintext credentials table must not exist!")
            }

            // 3. auth_credentials table MUST exist for salted PBKDF2 verifiers
            conn.prepareStatement("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='auth_credentials'").use { stmt ->
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1), "auth_credentials table must exist for secure salted verifiers")
            }

            // 4. Invariant: PRAGMA foreign_keys = ON
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("PRAGMA foreign_keys")
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1), "SQLite foreign keys must be active (PRAGMA foreign_keys = 1)")
            }

            // 5. Invariant: PRAGMA journal_mode = WAL
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("PRAGMA journal_mode")
                assertTrue(rs.next())
                val mode = rs.getString(1).lowercase()
                assertTrue(mode == "wal" || mode == "memory", "SQLite journal mode must be WAL (was $mode)")
            }

            // 6. Performance indexes must exist
            val expectedIndexes = listOf(
                "idx_passages_parent_created",
                "idx_subscriptions_source_created",
                "idx_subscriptions_target_created",
                "idx_notifications_subscriber_read_created",
                "idx_encrypted_cloud_messages_chat_created",
                "idx_billboard_advertised",
                "idx_auth_sessions_author_active_expiry",
                "idx_author_activity_author_timestamp",
                "idx_security_audit_actor_timestamp"
            )
            for (idx in expectedIndexes) {
                conn.prepareStatement("SELECT count(*) FROM sqlite_master WHERE type='index' AND name=?").use { stmt ->
                    stmt.setString(1, idx)
                    val rs = stmt.executeQuery()
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1), "Performance index $idx must exist")
                }
            }

            // 7. Schema migrations must be tracked in schema_migrations
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT version FROM schema_migrations ORDER BY version")
                val versions = mutableListOf<Int>()
                while (rs.next()) {
                    versions.add(rs.getInt("version"))
                }
                assertTrue(versions.contains(1), "Migration 1 must be recorded")
                assertTrue(versions.contains(2), "Migration 2 must be recorded")
                assertTrue(versions.contains(3), "Migration 3 must be recorded")
                assertTrue(versions.contains(4), "Migration 4 must be recorded")
                assertTrue(versions.contains(5), "Migration 5 must be recorded")
            }
        }
    }

    @Test
    fun testKeyExchangeRelayEphemeralInvariants() = runBlocking {
        val chatId = "chat-relay-test-123"
        val aliceId = "alice-author"
        val bobId = "bob-author"

        // 1. Session creation
        val session = KeyExchangeRelay.getOrCreateSession(chatId, aliceId, bobId)
        assertEquals(chatId, session.chatId)
        assertEquals(aliceId, session.participantA)
        assertEquals(bobId, session.participantB)
        assertFalse(session.isBothConnected)

        // 2. Participant A connects -> receives WAITING_FOR_PEER
        val (channelA, bothActive1) = KeyExchangeRelay.registerParticipant(session, aliceId)
        assertFalse(bothActive1)
        val packetA1 = channelA.receive()
        assertEquals(KeyExchangePacketType.WAITING_FOR_PEER, packetA1.type)

        // 3. Participant B connects -> both receive PEER_CONNECTED
        val (channelB, bothActive2) = KeyExchangeRelay.registerParticipant(session, bobId)
        assertTrue(bothActive2)
        assertTrue(session.isBothConnected)

        val packetB1 = channelB.receive()
        assertEquals(KeyExchangePacketType.PEER_CONNECTED, packetB1.type)
        val packetA2 = channelA.receive()
        assertEquals(KeyExchangePacketType.PEER_CONNECTED, packetA2.type)

        // 4. Relay packet exchange from Alice to Bob
        val keySharePacket = KeyExchangePacket(
            sessionId = session.id,
            sequence = 1L,
            type = KeyExchangePacketType.HELLO,
            payload = "OPAQ-KEY-SHARE-DATA-BASE64",
            senderId = aliceId,
            recipientId = bobId
        )
        val relayed = KeyExchangeRelay.relayPacket(keySharePacket)
        assertTrue(relayed)

        val receivedByBob = channelB.receive()
        assertEquals(KeyExchangePacketType.HELLO, receivedByBob.type)
        assertEquals("OPAQ-KEY-SHARE-DATA-BASE64", receivedByBob.payload)

        // 5. Non-participant cannot join or relay
        assertFailsWith<IllegalArgumentException> {
            KeyExchangeRelay.registerParticipant(session, "eve-attacker")
        }

        // 6. Disconnect or completion closes session
        KeyExchangeRelay.closeSession(session.id, "TEST_COMPLETE")
        assertNull(KeyExchangeRelay.getSession(session.id))
        assertTrue(channelA.isClosedForSend)
        assertTrue(channelB.isClosedForSend)
    }

    @Test
    fun testThreeChatModeInvariants() = testApplication {
        application {
            module(testConfig)
        }

        val json = Json { ignoreUnknownKeys = true }

        // Create Author A and Author B
        val resA = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"ChatAuthorA\") { passageId passphrase } }"}""")
        }
        val authorA = json.parseToJsonElement(resA.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject["passageId"]!!.jsonPrimitive.content
        val passA = json.parseToJsonElement(resA.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject["passphrase"]!!.jsonPrimitive.content

        val resB = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"ChatAuthorB\") { passageId passphrase } }"}""")
        }
        val authorBObj = json.parseToJsonElement(resB.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject
        val authorB = authorBObj["passageId"]!!.jsonPrimitive.content
        val passB = authorBObj["passphrase"]!!.jsonPrimitive.content

        // Login as Author A to get token
        val loginRes = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"authorId": "$authorA", "passphrase": "$passA"}""")
        }
        val tokenA = json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Login as Author B to get token
        val loginResB = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"authorId": "$authorB", "passphrase": "$passB"}""")
        }
        val tokenB = json.parseToJsonElement(loginResB.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // --- MODE 1: UNENCRYPTED_CLOUD ---
        val reqUnencrypted = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"UNENCRYPTED\") { id status } }"}""")
        }
        val unencryptedChatId = json.parseToJsonElement(reqUnencrypted.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject["id"]!!.jsonPrimitive.content

        // Accept chat
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$unencryptedChatId\", authorId: \"$authorB\", accept: true) { id status } }"}""")
        }

        // Send unencrypted message -> allowed and stored in chat_messages table
        val sendUnencrypted = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$unencryptedChatId\", senderId: \"$authorA\", recipientId: \"$authorB\", content: \"Hello in plaintext\", isEncrypted: false) { id } }"}""")
        }
        val sendUnencryptedBody = sendUnencrypted.bodyAsText()
        assertFalse(sendUnencryptedBody.contains("Security Policy Violation"))
        assertTrue(sendUnencryptedBody.contains("sendChatMessage"))

        // --- MODE 2: ENCRYPTED_CLOUD ---
        val reqEncCloud = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"ENCRYPTED_CLOUD\") { id status } }"}""")
        }
        val encCloudChatId = json.parseToJsonElement(reqEncCloud.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject["id"]!!.jsonPrimitive.content

        // Accept chat
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$encCloudChatId\", authorId: \"$authorB\", accept: true) { id status } }"}""")
        }

        // INVARIANT VIOLATION: Sending plaintext to ENCRYPTED_CLOUD is strictly rejected
        val sendPlaintextToCloud = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$encCloudChatId\", senderId: \"$authorA\", recipientId: \"$authorB\", content: \"Plaintext forbidden\", isEncrypted: false) { id } }"}""")
        }
        val sendPlaintextToCloudBody = sendPlaintextToCloud.bodyAsText()
        assertTrue(sendPlaintextToCloudBody.contains("Security Policy Violation"), "ENCRYPTED_CLOUD must reject unencrypted plaintext")

        // Sending ciphertext envelope to ENCRYPTED_CLOUD succeeds
        val validCiphertextEnvelope = "ASYM:VmVyeVNlY3VyZUNpcGhlcnRleHRFbnZlbG9wZUZvclF1b3Rlc0Nvbm5lY3Q="
        val sendCiphertextToCloud = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$encCloudChatId\", senderId: \"$authorA\", recipientId: \"$authorB\", content: \"$validCiphertextEnvelope\", isEncrypted: true) { id } }"}""")
        }
        val sendCiphertextToCloudBody = sendCiphertextToCloud.bodyAsText()
        assertFalse(sendCiphertextToCloudBody.contains("Security Policy Violation"))
        assertTrue(sendCiphertextToCloudBody.contains("sendChatMessage"))

        // --- MODE 3: ENCRYPTED_ON_DEVICE ---
        val reqDevice = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { requestChat(initiatorId: \"$authorA\", recipientId: \"$authorB\", type: \"ENCRYPTED\") { id status } }"}""")
        }
        val deviceChatId = json.parseToJsonElement(reqDevice.bodyAsText()).jsonObject["data"]!!.jsonObject["requestChat"]!!.jsonObject["id"]!!.jsonPrimitive.content

        // Accept chat
        client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenB")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { respondChat(chatId: \"$deviceChatId\", authorId: \"$authorB\", accept: true) { id status } }"}""")
        }

        // Send encrypted message to device-only chat
        val sendDeviceMsg = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { sendChatMessage(chatId: \"$deviceChatId\", senderId: \"$authorA\", recipientId: \"$authorB\", content: \"$validCiphertextEnvelope\", isEncrypted: true) { id } }"}""")
        }
        val sendDeviceMsgBody = sendDeviceMsg.bodyAsText()
        assertTrue(sendDeviceMsgBody.contains("sendChatMessage"))

        // VERIFY DATABASE PERSISTENCE INVARIANTS:
        val db = ServerDatabase(DatabaseConfig(DatabaseMode.DEMO, testDbFile))
        db.getConnection().use { conn ->
            // Unencrypted messages table has the unencrypted message
            conn.prepareStatement("SELECT count(*) FROM unencrypted_chat_messages WHERE chat_id = ?").use { stmt ->
                stmt.setString(1, unencryptedChatId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1), "Unencrypted chat message must be persisted")
            }

            // Encrypted cloud messages table has the ciphertext
            conn.prepareStatement("SELECT count(*) FROM encrypted_cloud_chat_messages WHERE chat_id = ?").use { stmt ->
                stmt.setString(1, encCloudChatId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(1, rs.getInt(1), "Encrypted cloud ciphertext message must be persisted")
            }

            // Unencrypted messages table contains ZERO messages for encrypted cloud or device chat
            conn.prepareStatement("SELECT count(*) FROM unencrypted_chat_messages WHERE chat_id IN (?, ?)").use { stmt ->
                stmt.setString(1, encCloudChatId)
                stmt.setString(2, deviceChatId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "unencrypted_chat_messages must have 0 rows for encrypted channels")
            }

            // Device-only messages are NEVER persisted to ANY database table
            conn.prepareStatement("SELECT count(*) FROM encrypted_cloud_chat_messages WHERE chat_id = ?").use { stmt ->
                stmt.setString(1, deviceChatId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(0, rs.getInt(1), "CRITICAL: ENCRYPTED (device only) messages must NEVER be persisted to database")
            }
        }
    }

    @Test
    fun testKeystoreCloudBackupAndLockUnlockLifecycle() = testApplication {
        application {
            module(testConfig)
        }

        val json = Json { ignoreUnknownKeys = true }

        // Create author
        val res = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"KeystoreAuthor\") { passageId passphrase token } }"}""")
        }
        val authorObj = json.parseToJsonElement(res.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject
        val authorId = authorObj["passageId"]!!.jsonPrimitive.content
        val token = authorObj["token"]!!.jsonPrimitive.content

        // 1. Initial query: No keystore on cloud
        val initialQuery = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { keystore(authorId: \"$authorId\") { authorId encryptedPayload } }"}""")
        }
        val initialKs = json.parseToJsonElement(initialQuery.bodyAsText()).jsonObject["data"]?.jsonObject?.get("keystore")
        assertTrue(initialKs == null || initialKs is JsonNull)

        // 2. Backup client-encrypted keystore to cloud
        val encPayload = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 0 })
        val salt = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { 1 })
        val iv = java.util.Base64.getEncoder().encodeToString(ByteArray(12) { 2 })
        val backupMutation = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { backupKeyStore(authorId: \"$authorId\", encryptedPayload: \"$encPayload\", salt: \"$salt\", iv: \"$iv\", version: 3) }"}""")
        }
        val backupBody = backupMutation.bodyAsText()
        assertTrue(backupBody.contains("\"backupKeyStore\":true") || backupBody.contains("\"backupKeyStore\": true"))

        // 3. Query keystore from cloud (returns encrypted ciphertext, server has zero private keys)
        val fetchedQuery = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { keystore(authorId: \"$authorId\") { authorId encryptedPayload salt iv version } }"}""")
        }
        val fetchedObj = json.parseToJsonElement(fetchedQuery.bodyAsText()).jsonObject["data"]!!.jsonObject["keystore"]!!.jsonObject
        assertEquals(authorId, fetchedObj["authorId"]!!.jsonPrimitive.content)
        assertEquals(encPayload, fetchedObj["encryptedPayload"]!!.jsonPrimitive.content)
        assertEquals(salt, fetchedObj["salt"]!!.jsonPrimitive.content)
        assertEquals(iv, fetchedObj["iv"]!!.jsonPrimitive.content)
        assertEquals(3, fetchedObj["version"]!!.jsonPrimitive.int)

        // Reject malformed/unsupported keystore envelopes without storing them.
        val invalidMutation = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { backupKeyStore(authorId: \"$authorId\", encryptedPayload: \"not-base64\", salt: \"$salt\", iv: \"$iv\", version: 99) }"}""")
        }
        assertTrue(invalidMutation.bodyAsText().contains("Unsupported keystore envelope version"))

        // 4. Delete keystore from cloud
        val delMutation = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { deleteKeyStore(authorId: \"$authorId\") }"}""")
        }
        val delBody = delMutation.bodyAsText()
        assertTrue(delBody.contains("\"deleteKeyStore\":true") || delBody.contains("\"deleteKeyStore\": true"))

        // 5. Query keystore after deletion returns null
        val afterDelQuery = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"query": "query { keystore(authorId: \"$authorId\") { authorId } }"}""")
        }
        val afterDelObj = json.parseToJsonElement(afterDelQuery.bodyAsText()).jsonObject["data"]?.jsonObject?.get("keystore")
        assertTrue(afterDelObj == null || afterDelObj is JsonNull)
    }

    @Test
    fun testSecurityAuditLoggerSanitizationBansSecretLogging() {
        val input = "login attempt with passphrase=SuperSecretPass123! and privateKey=MIIEvgIBADANBgkqhkiG9w0BAQEFAASC and publicKey=MIIBIjANBgkqhkiG"
        val sanitized = SecurityAuditLogger.sanitize(input)
        assertNotNull(sanitized)
        assertFalse(sanitized.contains("SuperSecretPass123!"))
        assertFalse(sanitized.contains("MIIEvgIBADANBgkqhkiG9w0BAQEFAASC"))
        assertTrue(sanitized.contains("passphrase=[REDACTED]"))
        assertTrue(sanitized.contains("privateKey=[REDACTED]"))
        assertTrue(sanitized.contains("publicKey=[REDACTED]"))
    }

    @Test
    fun testGraphEngineBoundedTraversalLimits() = runBlocking {
        val passages = mutableMapOf<Id, Passage>()
        val subscribers = mutableMapOf<Id, MutableList<Id>>()
        val neighbors = mutableMapOf<Id, MutableList<Id>>()

        val baseId = Id("node-0")
        passages[baseId] = StandardPassage(
            id = baseId,
            content = "Root node",
            createdAt = 0L
        )

        // Create a deep chain of 50 descendant nodes
        var prevId = baseId
        for (i in 1..50) {
            val currId = Id("node-$i")
            passages[currId] = StandardPassage(
                id = currId,
                content = "Child node $i",
                createdAt = 0L
            )
            subscribers.computeIfAbsent(prevId) { mutableListOf() }.add(currId)
            neighbors.computeIfAbsent(prevId) { mutableListOf() }.add(currId)
            neighbors.computeIfAbsent(currId) { mutableListOf() }.add(prevId)
            prevId = currId
        }

        // Traverse with maxDepth = 5 -> must not traverse beyond depth 5
        val treeDepth5 = GraphEngine.buildDescendantTree(
            rootId = baseId,
            maxDepth = 5,
            maxNodes = 200,
            getPassage = { passages[it] },
            getSubscribers = { subscribers[it] ?: emptyList() }
        )
        assertNotNull(treeDepth5)
        assertEquals(baseId, treeDepth5.passage.id)

        // Find max depth reached
        fun getDepth(node: GraphEngine.TreeNode, current: Int = 1): Int {
            if (node.children.isEmpty()) return current
            return node.children.maxOf { getDepth(it, current + 1) }
        }

        val reachedDepth = getDepth(treeDepth5)
        assertTrue(reachedDepth <= 6, "Traversal depth was $reachedDepth, expected <= 6")

        // Test shortest path bounded depth
        val targetId = Id("node-40")
        val pathBounded = GraphEngine.findShortestPath(
            startId = baseId,
            endId = targetId,
            maxDepth = 10,
            getNeighbors = { neighbors[it] ?: emptyList() }
        )
        assertTrue(pathBounded.isEmpty(), "Path exceeding maxDepth=10 must return empty list to prevent unbounded traversal DoS")

        val pathAllowed = GraphEngine.findShortestPath(
            startId = baseId,
            endId = targetId,
            maxDepth = 50,
            getNeighbors = { neighbors[it] ?: emptyList() }
        )
        assertEquals(41, pathAllowed.size)
    }
}
