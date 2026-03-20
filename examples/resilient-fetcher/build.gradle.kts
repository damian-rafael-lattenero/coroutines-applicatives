plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

// Modules used: kap-core + kap-resilience
// Maven equivalents:
//   io.github.damian-rafael-lattenero:kap-core:2.0.0
//   io.github.damian-rafael-lattenero:kap-resilience:2.0.0
dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

application {
    mainClass.set("MainKt")
}
