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
    implementation(libs.koog.agents.features.chat.history.jdbc)
    implementation(libs.koog.agents.features.persistence.jdbc)
    implementation(libs.jackson.databind)
    implementation(libs.logback.classic)
}

/*
 Java code doesn't complete well and produces false positive red code when it depends on a Kotlin project via Gradle composite build,
 so hacking our own "composite build" version via publishing to maven local
*/

interface ExecOps {
    @get:Inject
    val execOps: ExecOperations
}

val execOps = objects.newInstance<ExecOps>().execOps

/**
 * Gradle composite build with direct dependency on Koog breaks intellisense in IntelliJ when writing Java (false positive red code, etc.).
 * So for Java simple examples this "publish to maven local" hack is used.
 */
//region "Composite build" hack

// Auto-publish Koog to mavenLocal before compilation tasks
val koogVersion: String = libs.versions.koog.get()
val koogRootDir = rootProject.projectDir.resolve("../..")
val koogLocalMarker = File(
    System.getProperty("user.home"),
    ".m2/repository/ai/koog/koog-agents/$koogVersion/koog-agents-$koogVersion.pom"
)

fun ExecSpec.doPublishKoogToMavenLocal() {
    workingDir = koogRootDir
    commandLine(
        "${koogRootDir.resolve("gradlew").absolutePath}",
        "publishJvmPublicationToMavenLocal",
        "-Pversion=${koogVersion.removeSuffix("-SNAPSHOT")}",
    )
}

// Check if koog artifacts exist in mavenLocal. If not, publish them when initializing the script
if (!koogLocalMarker.exists()) {
    logger.lifecycle("Koog $koogVersion not found in mavenLocal — publishing")
    execOps.exec {
        doPublishKoogToMavenLocal()
    }
    logger.lifecycle("Koog $koogVersion published")
}

val publishKoogToMavenLocal by tasks.registering(Exec::class) {
    group = "setup"
    description = "Publishes the main Koog project to mavenLocal with version $koogVersion (cached by Gradle)"

    doPublishKoogToMavenLocal()
}

//endregion

tasks.withType<JavaCompile>().configureEach {
    dependsOn(publishKoogToMavenLocal)
    this.options.compilerArgs.addAll(listOf("-parameters", "-g"))
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

registerRunExampleTask("runExampleStrategyGraph", "ai.koog.agents.example.strategies.GraphStrategyExample")
registerRunExampleTask("runExampleStrategyGoap", "ai.koog.agents.example.strategies.GoapStrategyExample")
registerRunExampleTask("runExampleStrategyFunctional", "ai.koog.agents.example.strategies.functional.FunctionalStrategyExample")
registerRunExampleTask("runExampleCalculator", "ai.koog.agents.example.calculator.Calculator")
registerRunExampleTask("runExampleCalculatorLocal", "ai.koog.agents.example.calculator.Calculator", "local")
registerRunExampleTask("runExampleFunctionalAgentChat", "ai.koog.agents.example.chat.FunctionalAgentChat")
registerRunExampleTask("runExampleChatMemoryJdbc", "ai.koog.agents.example.chatmemory.ChatMemoryJdbcExample")
registerRunExampleTask("runExamplePersistenceJdbc", "ai.koog.agents.example.snapshot.PersistenceJdbcExample")
