plugins {
    kotlin("jvm") version "2.2.21"
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "ai.coding"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

application {
    mainClass = "ai.coding.agent.AcpAgentKt"
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.acp)
    implementation(libs.logback.classic)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
