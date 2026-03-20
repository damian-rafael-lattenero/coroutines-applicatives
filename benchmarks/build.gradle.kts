plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":kap-core"))
    implementation(project(":kap-resilience"))
    implementation(project(":kap-arrow"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.4")
    implementation("io.arrow-kt:arrow-core:1.2.4")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(2)
    includes.set(listOf("applicative.benchmarks.*"))
    jmhVersion.set("1.37")
}
