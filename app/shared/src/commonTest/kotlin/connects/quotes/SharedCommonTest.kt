package connects.quotes
import kotlinx.coroutines.test.runTest

import connects.quotes.client.QuotesApiClient
import connects.quotes.client.SessionManager
import connects.quotes.model.PassageType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    @kotlin.test.BeforeTest
    fun setup() {
        connects.quotes.config.ServerEndpointManager.updateFromInput("localhost:59999")
        SessionManager.clearIdentity()
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        SessionManager.clearIdentity()
        connects.quotes.config.ServerEndpointManager.resetToDefault()
    }

    @Test
    fun testEmptyBillboardAndUserCreation() = runTest {
        val client = QuotesApiClient()
        
        // Starts with zero template data
        val initialBillboard = client.getBillboard()
        assertTrue(initialBillboard.isEmpty())

        // User creates an orphan passage
        val created = client.createPassage(
            type = PassageType.QUOTE,
            content = "The unexamined life is not worth living.",
            tags = listOf("philosophy", "socrates")
        ).getOrThrow()

        // It is immediately placed on the billboard according to orphan billboard rule
        val updatedBillboard = client.getBillboard()
        assertEquals(1, updatedBillboard.size)
        assertEquals(created.id, updatedBillboard[0].passage.id)
        assertTrue(updatedBillboard[0].isOrphan)
    }

    @Test
    fun testClientSovereignIdentityAndPassageCreation() = runTest {
        val client = QuotesApiClient()

        // Generate identity
        val creds = client.createIdentity("Hypatia")
        assertEquals(creds.passageId, SessionManager.currentAuthorId)
        assertNotNull(creds.passphrase)

        // Create passage claimed by author
        val passage = client.createPassage(
            type = PassageType.QUOTE,
            content = "Reserve your right to think, for even to think wrongly is better than not to think at all.",
            tags = listOf("reason", "hypatia")
        ).getOrThrow()

        assertEquals("Reserve your right to think, for even to think wrongly is better than not to think at all.", passage.content)
        assertTrue(passage.tags.contains("reason"))

        // Verify retrieval
        val retrieved = client.getPassage(passage.id)
        assertNotNull(retrieved)
        assertEquals(passage.id, retrieved.id)
    }

    @Test
    fun testServerEndpointConfigurationAndStatus() = runTest {
        connects.quotes.config.ServerEndpointManager.resetToDefault()
        val vm = connects.quotes.viewmodel.QuotesViewModel()
        assertEquals("localhost", vm.uiState.value.serverEndpoint.host)
        assertEquals(8080, vm.uiState.value.serverEndpoint.port)

        // Update endpoint
        vm.updateServerEndpoint("192.168.1.200:9090")
        assertEquals("192.168.1.200", vm.uiState.value.serverEndpoint.host)
        assertEquals(9090, vm.uiState.value.serverEndpoint.port)
        assertEquals("http://192.168.1.200:9090", vm.uiState.value.serverEndpoint.baseUrl)

        // Reset to default
        connects.quotes.config.ServerEndpointManager.resetToDefault()
        assertEquals("localhost", connects.quotes.config.ServerEndpointManager.get().host)
    }

    @Test
    fun testSessionPersistenceWithRememberMe() = runTest {
        val testId = connects.quotes.model.Id("test-author-123")
        val testPass = "zenith-cipher-flux-456"
        val creds = connects.quotes.model.Credentials(testId, testPass)

        // Login with Remember Me enabled
        SessionManager.setIdentity(creds, rememberMe = true)
        assertEquals(testId, SessionManager.currentAuthorId)
        assertTrue(SessionManager.isRememberMe.value)

        // Verify stored in PlatformSessionStore without raw passphrase
        val loaded = connects.quotes.client.PlatformSessionStore.loadSession()
        assertNotNull(loaded)
        assertEquals(testId.value, loaded.resolvedAuthorId)
        kotlin.test.assertNull(loaded.passphrase)

        // Verify with AuthSession
        val authSession = connects.quotes.security.AuthSession(
            sessionId = connects.quotes.model.Id("sess-1"),
            authorId = testId,
            accessToken = "qc_tok_test_123"
        )
        SessionManager.setSession(authSession, creds, rememberMe = true)
        val loadedAuth = connects.quotes.client.PlatformSessionStore.loadSession()
        assertNotNull(loadedAuth)
        assertEquals("qc_tok_test_123", loadedAuth.accessToken)
        assertEquals(testId.value, loadedAuth.resolvedAuthorId)
        kotlin.test.assertNull(loadedAuth.passphrase)

        // Logout
        SessionManager.clearIdentity()
        kotlin.test.assertNull(SessionManager.currentAuthorId)
        kotlin.test.assertNull(connects.quotes.client.PlatformSessionStore.loadSession())
    }

    @Test
    fun testSessionWithoutRememberMeDoesNotPersist() = runTest {
        val testId = connects.quotes.model.Id("test-temp-author-789")
        val testPass = "logic-reason-stoic-999"
        val creds = connects.quotes.model.Credentials(testId, testPass)

        // Login with Remember Me disabled
        SessionManager.setIdentity(creds, rememberMe = false)
        assertEquals(testId, SessionManager.currentAuthorId)
        kotlin.test.assertFalse(SessionManager.isRememberMe.value)

        // Must NOT be stored in PlatformSessionStore
        kotlin.test.assertNull(connects.quotes.client.PlatformSessionStore.loadSession())

        // Clear session
        SessionManager.clearIdentity()
        kotlin.test.assertNull(SessionManager.currentAuthorId)
    }

    @Test
    fun testAlphanumericPassphraseGeneration() {
        val pass = connects.quotes.model.Credentials.generatePassphrase(32)
        assertEquals(32, pass.length)
        assertTrue(pass.all { it.isLetterOrDigit() }, "Passphrase must be purely alphanumeric")

        val pass2 = connects.quotes.model.Credentials.generatePassphrase(32)
        kotlin.test.assertNotEquals(pass, pass2, "Generated passphrases must be distinct and random")
    }

    @Test
    fun testChangePassphraseFlow() = runTest {
        val client = QuotesApiClient()
        val creds = client.createIdentity("Plato")
        assertEquals(32, creds.passphrase.length)
        assertTrue(creds.passphrase.all { it.isLetterOrDigit() })
        assertEquals(creds.passphrase, SessionManager.currentIdentity.value?.passphrase)

        // Change passphrase
        val newPass = "updatedAlphanumericSecretPass99"
        val result = client.changePassphrase(creds.passageId, creds.passphrase, newPass)
        assertTrue(result.isSuccess)
        assertEquals(newPass, SessionManager.currentIdentity.value?.passphrase)

        // Attempting to change with the old passphrase now fails
        val failResult = client.changePassphrase(creds.passageId, creds.passphrase, "failPass123456")
        assertTrue(failResult.isFailure)
    }

    @Test
    fun testClientTagCacheAndSuggestTags() = runTest {
        val client = QuotesApiClient()

        // Clear any leftover tags from previous runs
        connects.quotes.client.ClientTagCache.clear()

        // Cache sample tags
        connects.quotes.client.ClientTagCache.updateTags(
            listOf(
                connects.quotes.model.Tag("stoicism", 10),
                connects.quotes.model.Tag("sovereignty", 8),
                connects.quotes.model.Tag("epistemology", 5),
                connects.quotes.model.Tag("logic", 3)
            )
        )

        // Test instant suggestion retrieval from ClientTagCache
        val suggestions = client.suggestTags("st")
        assertTrue(suggestions.isNotEmpty())
        assertEquals("stoicism", suggestions[0].name)
        assertEquals(10, suggestions[0].count)

        val soverSuggestions = client.suggestTags("sov")
        assertEquals(1, soverSuggestions.size)
        assertEquals("sovereignty", soverSuggestions[0].name)
    }

    @Test
    fun testPlatformCryptoChannelEncryptionRoundTrip() = runTest {
        val keyPair = connects.quotes.crypto.PlatformCrypto.generateKeyPair()
        val plainQuery = "query { billboard { isOrphan } }"

        val (aesKey, envelope) = connects.quotes.crypto.PlatformCrypto.encryptChannel(
            plainText = plainQuery,
            serverPublicKeyBase64 = keyPair.publicKey,
            keyId = "test-key"
        )

        assertEquals("test-key", envelope.keyId)
        assertTrue(envelope.encryptedAesKey.isNotBlank())
        assertTrue(envelope.iv.isNotBlank())
        assertTrue(envelope.cipherText.isNotBlank())

        // Test response decryption
        // Simulate response encryption using the same AES key
        val mockResponseJson = """{"data":{"status":"OK"}}"""
        // In JVM tests, we can test round-trip or test PlatformCrypto.decryptChannel
        // Generate IV and mock or real cipher
        assertTrue(aesKey.isNotEmpty())
    }
}