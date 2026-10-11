# ProGuard rules for Quotes Connect Compose Desktop Application

# Keep all Ktor classes and engines (prevents CIOEngineContainer from being stripped)
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.client.engine.cio.CIOEngineContainer { *; }
-keep class io.ktor.client.HttpClientEngineContainer { *; }

# Keep ServiceLoader files
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }

# Keep Kotlinx Coroutines and Serialization
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# Keep application classes
-keep class connects.quotes.** { *; }

# Keep Compose Desktop UI runtime
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }

# Keep standard reflection attributes
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable

# Keep SQLite JDBC driver and JNI native bridge classes
-keep class org.sqlite.** { *; }
-dontwarn org.sqlite.**

# Keep SLF4J and Logback runtime providers
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**

# Ignore warnings for optional third-party libraries
-dontwarn io.ktor.**
-dontwarn kotlinx.**
