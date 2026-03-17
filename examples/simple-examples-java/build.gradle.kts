plugins {
    java
    id("ai.koog.gradle.plugins.credentialsresolver")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.features.chat.memory.sql)
    implementation(libs.koog.agents.features.persistence.jdbc)
    implementation(libs.koog.agents.features.chat.history.jdbc)
}

val envs = credentialsResolver.resolve(
    layout.projectDirectory.file(provider { "env.properties" })
)

fun registerRunExampleTask(name: String, mainClassName: String, vararg args: String) = tasks.register<JavaExec>(name) {
    doFirst {
        standardInput = System.`in`
        standardOutput = System.out
        environment(envs.get())
    }

    this.args = args.toList()
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

registerRunExampleTask("runExampleCalculator", "ai.koog.agents.example.calculator.Calculator")
registerRunExampleTask("runExampleCalculatorLocal", "ai.koog.agents.example.calculator.Calculator", "local")
registerRunExampleTask("runExampleFunctionalAgentChat", "ai.koog.agents.example.chat.FunctionalAgentChat")
registerRunExampleTask("runExampleChatMemoryJdbc", "ai.koog.agents.example.chatmemory.ChatMemoryJdbcExample")
registerRunExampleTask("runExamplePersistenceJdbc", "ai.koog.agents.example.snapshot.PersistenceJdbcExample")
registerRunExampleTask("runExampleFunctionalStrategy", "ai.koog.agents.example.strategies.functional.FunctionalStrategyExample")
registerRunExampleTask("runExampleGoapStrategy", "ai.koog.agents.example.strategies.GoapStrategyExample")
registerRunExampleTask("runExampleGraphStrategy", "ai.koog.agents.example.strategies.GraphStrategyExample")
