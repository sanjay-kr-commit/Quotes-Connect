package connects.quotes.util

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date.now().toLong()
