import java.util.Properties

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

kotlin {
    compilerOptions.allWarningsAsErrors.set(true)
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
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)
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
