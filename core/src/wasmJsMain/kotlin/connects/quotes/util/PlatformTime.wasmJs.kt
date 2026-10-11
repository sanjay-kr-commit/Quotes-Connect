@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package connects.quotes.util

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual fun currentTimeMillis(): Long = jsDateNow().toLong()
