plugins {
    id("ai.kotlin.jvm")
    application
}

application.mainClass.set("ai.koog.agents.examples.codeagent.step01.MainKt")

dependencies {
    implementation(project(":agents:agents-core"))
    implementation(project(":agents:agents-ext"))
    implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
