import java.util.Properties

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

dependencies {
    implementation(project(":agents:agents-test"))
    implementation(project(":koog-agents"))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
}

val knitProperties = Properties().apply {
    file("knit.properties").inputStream().use { stream ->
        load(stream)
    }
}

val knitDirectory = knitProperties["knit.dir"]!!

ktlint {
    filter {
        exclude { it.file.path.contains("/docs/$knitDirectory/") }
    }
}

knit {
    rootDir = project.rootDir
    files = fileTree("docs/") {
        include("**/*.md")
    }
    moduleDocs = "docs/modules.md"
    siteRoot = "https://docs.koog.ai/"

    tasks.register<Delete>("knitClean") {
        delete(
            fileTree(project.rootDir) {
                include("**/docs/$knitDirectory/**")
            }
        )
    }

    tasks.named("clean") {
        dependsOn("knitClean")
    }

    tasks.register<Delete>("knitAssemble") {
        dependsOn("cleanKnit", "knit", "assemble")
        tasks.findByName("knit")?.mustRunAfter("cleanKnit")
        tasks.findByName("assemble")?.mustRunAfter("knit")
    }
}
