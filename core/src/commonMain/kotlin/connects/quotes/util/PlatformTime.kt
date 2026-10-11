package connects.quotes.util

/**
 * Returns the current time in milliseconds since Unix epoch (UTC).
 * Multiplatform abstraction replacing direct java.lang.System.currentTimeMillis().
 */
expect fun currentTimeMillis(): Long
