package connects.quotes

import connects.quotes.config.ServerEndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerEndpointConfigTest {

    @Test
    fun testDefaultValues() {
        val cfg = ServerEndpointConfig.DEFAULT
        assertEquals("localhost", cfg.host)
        assertEquals(8080, cfg.port)
        assertFalse(cfg.useSsl)
        assertEquals("http://localhost:8080", cfg.baseUrl)
        assertEquals("http://localhost:8080/graphql", cfg.graphqlUrl)
        assertTrue(cfg.isLocalhost())
    }

    @Test
    fun testParseHostOnly() {
        val cfg = ServerEndpointConfig.fromInput("192.168.1.105")
        assertEquals("192.168.1.105", cfg.host)
        assertEquals(8080, cfg.port)
        assertFalse(cfg.useSsl)
        assertEquals("http://192.168.1.105:8080", cfg.baseUrl)
        assertFalse(cfg.isLocalhost())
    }

    @Test
    fun testParseHostWithPort() {
        val cfg = ServerEndpointConfig.fromInput("192.168.1.105:9000")
        assertEquals("192.168.1.105", cfg.host)
        assertEquals(9000, cfg.port)
        assertEquals("http://192.168.1.105:9000", cfg.baseUrl)
    }

    @Test
    fun testParseFullUrlWithProtocolAndPath() {
        val cfg = ServerEndpointConfig.fromInput("http://10.0.2.2:8080/graphql")
        assertEquals("10.0.2.2", cfg.host)
        assertEquals(8080, cfg.port)
        assertFalse(cfg.useSsl)
        assertEquals("http://10.0.2.2:8080", cfg.baseUrl)
    }

    @Test
    fun testParseHttps() {
        val cfg = ServerEndpointConfig.fromInput("https://quotes.example.com/status")
        assertEquals("quotes.example.com", cfg.host)
        assertEquals(443, cfg.port)
        assertTrue(cfg.useSsl)
        assertTrue(cfg.isSecureTransport)
        assertEquals("https://quotes.example.com", cfg.baseUrl)
        assertEquals("https://quotes.example.com/graphql", cfg.graphqlUrl)
    }

    @Test
    fun testPortBasedSecurityDetection() {
        val port443 = ServerEndpointConfig.fromInput("secure.quotes.local:443")
        assertTrue(port443.useSsl)
        assertTrue(port443.isSecureTransport)

        val port8080 = ServerEndpointConfig.fromInput("insecure.quotes.local:8080")
        assertFalse(port8080.useSsl)
        assertFalse(port8080.isSecureTransport)

        val defaultCfg = ServerEndpointConfig.DEFAULT
        assertFalse(defaultCfg.isSecureTransport)

        // Verify ChannelSecurityMode enum labels
        assertEquals("SSL / TLS SECURED", connects.quotes.model.ChannelSecurityMode.TLS_SSL.label)
        assertEquals("ENCRYPTED CHANNEL", connects.quotes.model.ChannelSecurityMode.ASYM_ENCRYPTED.label)
    }
}
