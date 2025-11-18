plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

application.mainClass.set("ai.koog.agents.examples.codeagent.step02.MainKt")

dependencies {
    implementation("ai.koog:koog-agents")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("code-agent")
    mergeServiceFiles()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
