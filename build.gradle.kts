plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

group = "io.github.damian-rafael-lattenero"
version = "2.1.0"
