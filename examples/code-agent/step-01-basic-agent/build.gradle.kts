plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":agents:agents-core"))
    implementation(project(":agents:agents-tools"))
    implementation(project(":agents:agents-ext"))

    implementation(project(":prompt:prompt-model"))
    implementation(project(":prompt:prompt-llm"))
    implementation(project(":prompt:prompt-executor:prompt-executor-model"))
    implementation(project(":prompt:prompt-executor:prompt-executor-llms-all"))

    implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
