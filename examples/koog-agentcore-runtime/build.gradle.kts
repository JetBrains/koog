plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "com.example.koogagentcore"
version = "1.0.0"

repositories {
    mavenLocal() // for the locally-published koog-bedrock-agentcore-runtime plugin
    mavenCentral()
}

dependencies {
    // Module under test. Publish it to mavenLocal first (see settings.gradle.kts).
    implementation("ai.koog:koog-bedrock-agentcore-runtime:1.1.1-beta-SNAPSHOT")

    // Bedrock SDK — used directly to call converseStream from the agent.
    implementation("aws.sdk.kotlin:bedrockruntime:1.6.98")

    // kotlinx-serialization for parsing the request envelope.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor server engine (the plugin doesn't impose one).
    implementation("io.ktor:ktor-server-netty:3.3.3")
    implementation("io.ktor:ktor-server-call-logging:3.3.3")

    implementation("ch.qos.logback:logback-classic:1.5.13")
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("com.example.koogagentcore.AppKt")
}

// Build a fat jar named app.jar so the Dockerfile copies a stable name.
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "com.example.koogagentcore.AppKt" }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
