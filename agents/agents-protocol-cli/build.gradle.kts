group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    application
}

application {
    mainClass.set("ai.koog.protocol.cli.MainKt")
    applicationName = "flow"
}

dependencies {
    implementation(project(":agents:agents-protocol"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit5"))
}

tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("installDist"))
    systemProperty("projectDir", project.projectDir.absolutePath)
}
