plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation(libs.coroutines.core)
}

application {
    mainClass.set("MainKt")
}
