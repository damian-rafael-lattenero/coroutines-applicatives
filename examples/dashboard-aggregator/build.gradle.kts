plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":kap-core"))
    implementation(libs.coroutines.core)
}

application {
    mainClass.set("MainKt")
}
