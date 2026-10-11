package connects.quotes

import connects.quotes.crypto.ServerChannelCrypto
import connects.quotes.model.EncryptedChannelPayload
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import connects.quotes.security.ReplayDetected

class ChannelReplayTest {
    
    private fun generateEnvelope(reqId: String, payload: String = """{"query":"test"}"""): EncryptedChannelPayload {
        val handshake = ServerChannelCrypto.getHandshake()
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val aesKey = keyGen.generateKey()
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val aadString = "${handshake.keyId}:$reqId"
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        aesCipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))
        val cipherTextBytes = aesCipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val pubBytes = Base64.getDecoder().decode(handshake.serverPublicKey)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pubBytes))
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepParams = javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT)
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepParams)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        return EncryptedChannelPayload(
            keyId = handshake.keyId,
            requestId = reqId,
            encryptedAesKey = Base64.getEncoder().encodeToString(encryptedAesKey),
            iv = Base64.getEncoder().encodeToString(iv),
            cipherText = Base64.getEncoder().encodeToString(cipherTextBytes)
        )
    }

    @Test
    fun testReplayPreventionExactDuplicateRejected() {
        ServerChannelCrypto.rotateKeys()
        val env = generateEnvelope("REQ-DUP-1")
        
        // 1st request should pass
        ServerChannelCrypto.decryptRequest(env)
        
        // 2nd request should fail with ReplayDetected
        assertFailsWith<ReplayDetected> {
            ServerChannelCrypto.decryptRequest(env)
        }
    }
    
    @Test
    fun testTamperingWithCiphertext() {
        ServerChannelCrypto.rotateKeys()
        val env = generateEnvelope("REQ-TAMP-1")
        
        // Corrupt ciphertext
        val tamperedEnv = env.copy(cipherText = env.cipherText.dropLast(5) + "XXXXX")
        
        // Should fail authentication (mapped to SecurityException by ServerChannelCrypto or AuthenticationFailed)
        assertFailsWith<SecurityException> {
            ServerChannelCrypto.decryptRequest(tamperedEnv)
        }
    }
    
    @Test
    fun testTamperingWithRequestId() {
        ServerChannelCrypto.rotateKeys()
        val env = generateEnvelope("REQ-ID-1")
        
        // Change request ID without resigning MAC
        val tamperedEnv = env.copy(requestId = "REQ-ID-2")
        
        // Should fail MAC tag because AAD won't match
        assertFailsWith<SecurityException> {
            ServerChannelCrypto.decryptRequest(tamperedEnv)
        }
    }

    @Test
    fun testConcurrentDuplicateRequests() {
        ServerChannelCrypto.rotateKeys()
        val env = generateEnvelope("REQ-CONC-1")
        
        val threads = 10
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        
        val successCount = AtomicInteger(0)
        val replayCount = AtomicInteger(0)
        
        for (i in 0 until threads) {
            executor.submit {
                latch.await()
                try {
                    ServerChannelCrypto.decryptRequest(env)
                    successCount.incrementAndGet()
                } catch (e: ReplayDetected) {
                    replayCount.incrementAndGet()
                } catch (e: Exception) {
                    // other failures
                }
            }
        }
        
        latch.countDown()
        executor.shutdown()
        while (!executor.isTerminated) {
            Thread.sleep(10)
        }
        
        // Exactly ONE should succeed
        assertEquals(1, successCount.get())
        // The remaining 9 should be rejected as replays
        assertEquals(9, replayCount.get())
    }
}
