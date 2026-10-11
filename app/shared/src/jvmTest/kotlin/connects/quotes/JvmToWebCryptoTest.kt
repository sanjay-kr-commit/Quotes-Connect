package connects.quotes

import connects.quotes.crypto.PlatformCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class JvmToWebCryptoTest {
    @Test
    fun testGenerateArtifactForNode() = runBlocking {
        val vectorStr = File("../../webcrypto_vector.json").readText()
        val pubKeyB64 = vectorStr.substringAfter("\"publicKeyBase64\": \"").substringBefore("\"")
        
        val envelope = PlatformCrypto.encryptEnvelope("""{"query":"test-jvm-to-webcrypto"}""", pubKeyB64, "test-key-id:REQ-1234567890abcdef", "test-key-id")
        
        val json = """{"version":${envelope.version},"suite":"${envelope.suite}","keyId":"${envelope.keyId}","nonce":"${envelope.nonce}","ciphertext":"${envelope.ciphertext}","aad":"${envelope.aad}"}"""
        File("../../jvm_vector.json").writeText(json)
    }
}
