import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties


plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

kotlin {
    compilerOptions.allWarningsAsErrors.set(true)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(java.toolchain.languageVersion.get().asInt())
    // never cache generated snippets compilation
    outputs.cacheIf { false }
}

tasks.withType<KotlinCompile>().configureEach {
    // never cache generated snippets compilation
    outputs.cacheIf { false }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":a2a:a2a-client"))
    implementation(project(":a2a:a2a-core"))
    implementation(project(":a2a:a2a-server"))
    implementation(project(":a2a:a2a-transport:a2a-transport-client-jsonrpc-http"))
    implementation(project(":a2a:a2a-transport:a2a-transport-core-jsonrpc"))
    implementation(project(":a2a:a2a-transport:a2a-transport-server-jsonrpc-http"))
    implementation(project(":agents:agents-features:agents-features-acp"))
    implementation(project(":agents:agents-test"))
    implementation(project(":koog-agents"))
    implementation(project(":agents:agents-ext"))
    implementation(project(":koog-agents-additions"))
    implementation(project(":serialization:serialization-jackson"))
    api(libs.opentelemetry.exporter.logging)
    api(libs.opentelemetry.exporter.otlp)
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}

val knitProperties: Provider<Properties> =
    providers.fileContents(layout.projectDirectory.file("knit.properties"))
        .asText
        .map { text ->
            Properties().apply {
                text.reader().use { load(it) }
            }
        }

val knitDir: Provider<String> =
    knitProperties.map { props ->
        requireNotNull(props.getProperty("knit.dir")) {
            "Missing 'knit.dir' in knit.properties"
        }
    }

ktlint {
    filter {
        exclude { it.file.path.contains("/docs/${knitDir.get()}/") }
    }
}

knit {
    rootDir = project.rootDir
    files = fileTree("docs/") {
        include("**/*.md")
    }
    moduleDocs = "docs/modules.md"
    siteRoot = "https://docs.koog.ai/"
}

tasks.register<Delete>("knitClean") {
    delete(
        fileTree(project.rootDir) {
            include("**/docs/${knitDir.get()}/**")
        }
    )
}

tasks.named("clean") {
    dependsOn("knitClean")
}

tasks.register<Delete>("knitAssemble") {
    dependsOn("knitClean", "knit", "assemble")
}

tasks.named("knit").configure { mustRunAfter("knitClean") }
tasks.named("assemble").configure { mustRunAfter("knit") }

// docs is not a published library, so ABI validation is not applicable
tasks.matching { it.name.contains("Abi") }.configureEach {
    enabled = false
}
