package connects.quotes

import connects.quotes.security.SecurityConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityConfigurationTest {
    @Test
    fun canonical_parameters_are_stable() {
        assertEquals(1, SecurityConfiguration.CONFIGURATION_VERSION)
        assertEquals("PBKDF2-HMAC-SHA256", SecurityConfiguration.KEYSTORE_KDF)
        assertEquals(600_000, SecurityConfiguration.KEYSTORE_KDF_ITERATIONS)
        assertEquals(100_000, SecurityConfiguration.KEYSTORE_LEGACY_KDF_ITERATIONS)
        assertEquals(100_000, SecurityConfiguration.AUTH_PASSWORD_KDF_ITERATIONS)
        assertEquals(32, SecurityConfiguration.KEYSTORE_DERIVED_KEY_BYTES)
        assertEquals(16, SecurityConfiguration.KEYSTORE_SALT_BYTES)
        assertEquals("AES-256-GCM", SecurityConfiguration.KEYSTORE_CIPHER)
        assertEquals(12, SecurityConfiguration.KEYSTORE_NONCE_BYTES)
        assertEquals(16, SecurityConfiguration.KEYSTORE_AUTH_TAG_BYTES)
        assertEquals("P-256", SecurityConfiguration.ECDH_CURVE)
        assertEquals("SHA-256", SecurityConfiguration.HKDF_HASH)
        assertEquals("HmacSHA256", SecurityConfiguration.HMAC_ALGORITHM)
        assertEquals("SHA256withRSA", SecurityConfiguration.SIGNATURE_ALGORITHM)
        assertEquals(2048, SecurityConfiguration.RSA_KEY_BITS)
        assertEquals("SHA-256", SecurityConfiguration.RSA_OAEP_HASH)
        assertEquals("QC-ECDH-P256-HKDF-SHA256-RSA-SHA256", SecurityConfiguration.KEY_EXCHANGE_SUITE_ID)
    }
}
