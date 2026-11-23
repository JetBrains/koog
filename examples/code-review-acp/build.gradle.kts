plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "ai.koog.examples"
version = "1.0-SNAPSHOT"


application {
    // `installDist` produces build/install/code-review-agent/bin/code-review-agent —
    // that launcher is what an ACP host (IntelliJ IDEA, Zed, …) is configured to spawn.
    applicationName = "code-review-agent"
    mainClass.set("ai.codereview.agent.CodeReviewAcpAgentKt")
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.ext)
    implementation(libs.koog.ktor)
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
