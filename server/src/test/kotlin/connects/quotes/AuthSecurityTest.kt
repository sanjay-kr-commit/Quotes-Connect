package connects.quotes

import connects.quotes.config.ServerConfig
import connects.quotes.database.DatabaseConfig
import connects.quotes.database.DatabaseMode
import connects.quotes.database.JdbcPassageRepository
import connects.quotes.database.ServerDatabase
import connects.quotes.graphql.GraphQLDocumentParser
import connects.quotes.graphql.OperationType
import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.routes.LoginRequest
import connects.quotes.security.PasswordHasher
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

class AuthSecurityTest {

    private val testDbFile = "build/test_auth_security.db"

    private val testConfig = ServerConfig(
        port = 8081,
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
    fun testPasswordHasherSaltingAndVerification() {
        val authorId = Id("test-author-socrates")
        val rawPassword = "CorrectHorseBatteryStaple123"
        val verifier = PasswordHasher.hashPassphrase(authorId, rawPassword.toCharArray())

        assertEquals(connects.quotes.security.PasswordVerifierRecord.SCHEME_PBKDF2_SHA256, verifier.scheme)
        assertEquals(100_000, verifier.iterations)
        assertTrue(verifier.saltBase64.isNotBlank())
        assertTrue(verifier.hashBase64.isNotBlank())

        // Correct password verifies successfully
        assertTrue(PasswordHasher.verifyPassphrase(rawPassword.toCharArray(), verifier))

        // Incorrect password fails verification
        assertFalse(PasswordHasher.verifyPassphrase("WrongPassword123".toCharArray(), verifier))

        // Different salt on re-hash produces distinct hash
        val verifier2 = PasswordHasher.hashPassphrase(authorId, rawPassword.toCharArray())
        assertNotEquals(verifier.saltBase64, verifier2.saltBase64)
        assertNotEquals(verifier.hashBase64, verifier2.hashBase64)
        assertTrue(PasswordHasher.verifyPassphrase(rawPassword.toCharArray(), verifier2))
    }

    @Test
    fun testGraphQLDocumentParserExtractsRootFieldsWithoutSubstringFalsePositives() {
        val query = """
            query MyQuery {
                billboard(limit: 10) {
                    isOrphan
                    passage {
                        id
                        content
                        tags
                        subscribers { id }
                    }
                }
            }
        """.trimIndent()

        val parsed = GraphQLDocumentParser.parse(query)
        assertEquals(OperationType.QUERY, parsed.type)
        assertEquals("MyQuery", parsed.name)
        assertEquals(1, parsed.rootFields.size)

        val rootField = parsed.rootFields.first()
        assertEquals("billboard", rootField.name)
        assertNull(rootField.alias)
        assertEquals("limit: 10", rootField.argsString)

        // Nested fields (passage, tags, subscribers) are NOT parsed as root fields
        val fieldNames = parsed.rootFields.map { it.name }
        assertFalse(fieldNames.contains("passage"))
        assertFalse(fieldNames.contains("subscribers"))
        assertFalse(fieldNames.contains("tags"))
    }

    @Test
    fun testAuthLifecycleLoginSessionRefreshAndLogout() = testApplication {
        application {
            module(testConfig)
        }
        val json = Json { ignoreUnknownKeys = true }

        // 1. Create Identity via GraphQL
        val createIdentityMutation = """
            mutation {
                createIdentity(moniker: "Socrates") {
                    passageId
                    passphrase
                }
            }
        """.trimIndent()

        val createRes = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": ${JsonPrimitive(createIdentityMutation)}}""")
        }
        assertEquals(HttpStatusCode.OK, createRes.status)
        val createBody = json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val identityObj = createBody["data"]!!.jsonObject["createIdentity"]!!.jsonObject
        val authorId = identityObj["passageId"]!!.jsonPrimitive.content
        val passphrase = identityObj["passphrase"]!!.jsonPrimitive.content

        // 2. Login with valid credentials
        val loginReq = LoginRequest(authorId = authorId, passphrase = passphrase)
        val loginRes = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest.serializer(), loginReq))
        }
        assertEquals(HttpStatusCode.OK, loginRes.status)
        val sessionJson = json.parseToJsonElement(loginRes.bodyAsText()).jsonObject
        val accessToken = sessionJson["accessToken"]!!.jsonPrimitive.content
        assertEquals(64, accessToken.length)
        assertTrue(accessToken.all { it.isDigit() || it in 'a'..'f' })

        // 3. Verify session endpoint using Bearer token
        val sessionRes = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, sessionRes.status)
        val principalJson = json.parseToJsonElement(sessionRes.bodyAsText()).jsonObject
        assertEquals(authorId, principalJson["authorId"]!!.jsonPrimitive.content)

        // 4. Unauthorized access with invalid token fails
        val failRes = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer invalid-token-xyz")
        }
        assertEquals(HttpStatusCode.Unauthorized, failRes.status)

        // 5. Refresh token
        val refreshRes = client.post("/api/auth/refresh") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, refreshRes.status)
        val refreshedSessionJson = json.parseToJsonElement(refreshRes.bodyAsText()).jsonObject
        val refreshedToken = refreshedSessionJson["accessToken"]!!.jsonPrimitive.content
        assertNotEquals(accessToken, refreshedToken)

        // Old token is revoked
        val oldTokenCheck = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, oldTokenCheck.status)

        // New token is valid
        val newTokenCheck = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $refreshedToken")
        }
        assertEquals(HttpStatusCode.OK, newTokenCheck.status)

        // 6. Logout revokes token
        val logoutRes = client.post("/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $refreshedToken")
        }
        assertEquals(HttpStatusCode.OK, logoutRes.status)

        // Subsequent check fails
        val postLogoutCheck = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $refreshedToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, postLogoutCheck.status)
    }

    @Test
    fun testAuthorizationMatrixRejectsCallerIdentitySpoofing() = testApplication {
        application {
            module(testConfig)
        }
        val json = Json { ignoreUnknownKeys = true }

        // Create Author A and Author B
        val resA = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"AuthorA\") { passageId passphrase } }"}""")
        }
        val authorAId = json.parseToJsonElement(resA.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject["passageId"]!!.jsonPrimitive.content
        val authorAPass = json.parseToJsonElement(resA.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject["passphrase"]!!.jsonPrimitive.content

        val resB = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "mutation { createIdentity(moniker: \"AuthorB\") { passageId passphrase } }"}""")
        }
        val authorBId = json.parseToJsonElement(resB.bodyAsText()).jsonObject["data"]!!.jsonObject["createIdentity"]!!.jsonObject["passageId"]!!.jsonPrimitive.content

        // Login as Author A
        val loginRes = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest.serializer(), LoginRequest(authorAId, authorAPass)))
        }
        val tokenA = json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Author A attempts to modify Author B's settings via REST -> 403 Forbidden
        val spoofSettingsRes = client.post("/api/author/settings") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            setBody(listOf("authorId" to authorBId, "inactivityPeriodDays" to "60").formUrlEncode())
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        }
        assertEquals(HttpStatusCode.Forbidden, spoofSettingsRes.status)

        // Author A attempts to create owned passage spoofing parentId as Author B -> GraphQL Forbidden Error
        val spoofPassageMutation = """
            mutation {
                createPassage(
                    type: "QUOTE",
                    content: "Spoofed quote attributed to B",
                    parentId: "$authorBId"
                ) {
                    id
                }
            }
        """.trimIndent()
        val spoofPassageRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": ${JsonPrimitive(spoofPassageMutation)}}""")
        }
        val passageBody = spoofPassageRes.bodyAsText()
        assertTrue(passageBody.contains("Forbidden") || passageBody.contains("errors"))

        // Author A creating owned passage with own identity succeeds
        val legitPassageMutation = """
            mutation {
                createPassage(
                    type: "QUOTE",
                    content: "Legitimate quote from A",
                    parentId: "$authorAId"
                ) {
                    id
                    content
                }
            }
        """.trimIndent()
        val legitPassageRes = client.post("/graphql") {
            header(HttpHeaders.Authorization, "Bearer $tokenA")
            contentType(ContentType.Application.Json)
            setBody("""{"query": ${JsonPrimitive(legitPassageMutation)}}""")
        }
        val legitBody = legitPassageRes.bodyAsText()
        assertTrue(legitBody.contains("Legitimate quote from A"))
    }
}
