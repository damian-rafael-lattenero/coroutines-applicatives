plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-arrow"))
    implementation(libs.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.serialization.json)
}

application {
    mainClass.set("MainKt")
}
