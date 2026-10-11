package connects.quotes.crypto

import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => globalThis.crypto.subtle.generateKey({ name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['encrypt', 'decrypt'])")
external fun cryptoGenerateRSAKeyPair(): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyPair) => keyPair.publicKey")
external fun getPublicKey(keyPair: JsAny): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyPair) => keyPair.privateKey")
external fun getPrivateKey(keyPair: JsAny): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(format, key) => globalThis.crypto.subtle.exportKey(format, key)")
external fun cryptoExportKey(format: String, key: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(buffer) => new Uint8Array(buffer)")
external fun arrayBufferToUint8Array(buffer: JsAny): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(arr) => arr.length")
external fun getUint8ArrayLength(arr: JsAny): Int

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(arr, index) => arr[index]")
external fun getUint8ArrayElement(arr: JsAny, index: Int): Int

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(size) => { const arr = new Uint8Array(size); for(let i=0; i<size; i++) arr[i] = 0; return arr; }")
external fun createUint8Array(size: Int): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(arr, index, value) => { arr[index] = value; }")
external fun setUint8ArrayElement(arr: JsAny, index: Int, value: Byte)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
fun byteArrayToUint8Array(bytes: ByteArray): JsAny {
    val arr = createUint8Array(bytes.size)
    for (i in bytes.indices) {
        setUint8ArrayElement(arr, i, bytes[i])
    }
    return arr
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
fun uint8ArrayToByteArray(arr: JsAny): ByteArray {
    val length = getUint8ArrayLength(arr)
    val result = ByteArray(length)
    for (i in 0 until length) {
        result[i] = getUint8ArrayElement(arr, i).toByte()
    }
    return result
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('spki', keyData, { name: 'RSA-OAEP', hash: 'SHA-256' }, true, ['encrypt'])")
external fun cryptoImportRSAPublicKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('pkcs8', keyData, { name: 'RSA-OAEP', hash: 'SHA-256' }, true, ['decrypt'])")
external fun cryptoImportRSAPrivateKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, data) => globalThis.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, key, data)")
external fun cryptoEncryptRSA(key: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, data) => globalThis.crypto.subtle.decrypt({ name: 'RSA-OAEP' }, key, data)")
external fun cryptoDecryptRSA(key: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('raw', keyData, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt'])")
external fun cryptoImportAESKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data) => globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv }, key, data)")
external fun cryptoEncryptAESGCM(key: JsAny, iv: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data, aad) => globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv, additionalData: aad }, key, data)")
external fun cryptoEncryptAESGCMWithAAD(key: JsAny, iv: JsAny, data: JsAny, aad: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data) => globalThis.crypto.subtle.decrypt({ name: 'AES-GCM', iv: iv }, key, data)")
external fun cryptoDecryptAESGCM(key: JsAny, iv: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data) => globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv, tagLength: 128 }, key, data)")
external fun cryptoEncryptKeystoreAESGCM(key: JsAny, iv: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data) => globalThis.crypto.subtle.decrypt({ name: 'AES-GCM', iv: iv, tagLength: 128 }, key, data)")
external fun cryptoDecryptKeystoreAESGCM(key: JsAny, iv: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, iv, data, aad) => globalThis.crypto.subtle.decrypt({ name: 'AES-GCM', iv: iv, additionalData: aad }, key, data)")
external fun cryptoDecryptAESGCMWithAAD(key: JsAny, iv: JsAny, data: JsAny, aad: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(passwordData) => globalThis.crypto.subtle.importKey('raw', passwordData, { name: 'PBKDF2' }, false, ['deriveKey'])")
external fun cryptoImportPBKDF2Password(passwordData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(passwordKey, salt, iterations) => globalThis.crypto.subtle.deriveKey({ name: 'PBKDF2', salt: salt, iterations: iterations, hash: 'SHA-256' }, passwordKey, { name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])")
external fun cryptoDerivePBKDF2Key(passwordKey: JsAny, salt: JsAny, iterations: Int): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(algo, data) => globalThis.crypto.subtle.digest(algo, data)")
external fun cryptoDigest(algo: String, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(size) => { const arr = new Uint8Array(size); globalThis.crypto.getRandomValues(arr); return arr; }")
external fun cryptoGetRandomValues(size: Int): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(str) => new TextEncoder().encode(str)")
external fun encodeUtf8(str: String): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(buffer) => new TextDecoder().decode(new Uint8Array(buffer))")
external fun decodeUtf8(buffer: JsAny): String



@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => globalThis.crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveKey', 'deriveBits'])")
external fun cryptoGenerateECDHKeyPair(): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('pkcs8', keyData, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'])")
external fun cryptoImportECDHPrivateKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('spki', keyData, { name: 'ECDH', namedCurve: 'P-256' }, false, [])")
external fun cryptoImportECDHPublicKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(pubKey, privKey) => globalThis.crypto.subtle.deriveBits({ name: 'ECDH', public: pubKey }, privKey, 256)")
external fun cryptoDeriveECDHBits(pubKey: JsAny, privKey: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('pkcs8', keyData, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'])")
external fun cryptoImportRSASignPrivateKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData) => globalThis.crypto.subtle.importKey('spki', keyData, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify'])")
external fun cryptoImportRSAVerifyPublicKey(keyData: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(privKey, message) => globalThis.crypto.subtle.sign({ name: 'RSASSA-PKCS1-v1_5' }, privKey, message)")
external fun cryptoSignRSA(privKey: JsAny, message: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(pubKey, signature, message) => globalThis.crypto.subtle.verify({ name: 'RSASSA-PKCS1-v1_5' }, pubKey, signature, message)")
external fun cryptoVerifyRSA(pubKey: JsAny, signature: JsAny, message: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(v) => v === true")
external fun cryptoIsTrue(v: JsAny): Boolean

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(keyData, data) => globalThis.crypto.subtle.importKey('raw', keyData, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']).then(key => globalThis.crypto.subtle.sign({ name: 'HMAC' }, key, data))")
external fun cryptoHmacSHA256(keyData: JsAny, data: JsAny): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(ikm, salt, info, lengthBits) => globalThis.crypto.subtle.importKey('raw', ikm, { name: 'HKDF' }, false, ['deriveBits']).then(key => globalThis.crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: salt, info: info }, key, lengthBits))")
external fun cryptoHKDFSHA256(ikm: JsAny, salt: JsAny, info: JsAny, lengthBits: Int): Promise<JsAny>

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(passwordKey, salt, iterations, lengthBits) => globalThis.crypto.subtle.deriveBits({ name: 'PBKDF2', salt: salt, iterations: iterations, hash: 'SHA-256' }, passwordKey, lengthBits)")
external fun cryptoDerivePBKDF2Bits(passwordKey: JsAny, salt: JsAny, iterations: Int, lengthBits: Int): Promise<JsAny>
