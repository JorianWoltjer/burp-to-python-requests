plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

group = "com.jorianwoltjer"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.portswigger.burp.extensions:montoya-api:2025.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
}

tasks.test {
    useJUnitPlatform()
}
tasks.shadowJar {
    archiveFileName.set("to-python-requests.jar")
}
kotlin {
    jvmToolchain(22)
}