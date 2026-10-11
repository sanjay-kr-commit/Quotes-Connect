plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "connects.quotes"
version = "1.0.0"
application {
    mainClass = "connects.quotes.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serializationJson)
    implementation(libs.ktor.networkTlsCertificates)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mysql.connector)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}