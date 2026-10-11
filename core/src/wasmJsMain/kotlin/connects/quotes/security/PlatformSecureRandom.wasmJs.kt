@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package connects.quotes.security

@JsFun("(size) => { const arr = new Uint8Array(size); globalThis.crypto.getRandomValues(arr); return arr; }")
private external fun getRandomUint8Array(size: Int): JsAny

@JsFun("(arr, index) => arr[index]")
private external fun getUint8ArrayElement(arr: JsAny, index: Int): Int

actual object PlatformSecureRandom : SecureRandomSource {
    actual override fun nextBytes(size: Int): ByteArray {
        val arr = getRandomUint8Array(size)
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = getUint8ArrayElement(arr, i).toByte()
        }
        return result
    }

    actual override fun nextLong(): Long {
        val b = nextBytes(8)
        var l = 0L
        for (i in 0 until 8) {
            l = (l shl 8) or (b[i].toLong() and 0xFF)
        }
        return l
    }

    actual override fun nextInt(bound: Int): Int {
        if (bound <= 0) throw IllegalArgumentException("bound must be positive")
        var bits: Long
        var valOut: Long
        do {
            bits = nextLong() and 0x7FFFFFFFFFFFFFFFL
            valOut = bits % bound
        } while (bits - valOut + (bound - 1) < 0)
        return valOut.toInt()
    }
}
