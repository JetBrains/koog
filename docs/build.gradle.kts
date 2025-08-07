group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

dependencies {
    api(project(":agents:agents-test"))
    api(project(":koog-agents"))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
}

knit {
    rootDir = project.rootDir
    files = fileTree("docs/") {
        include("**/*.md")
    }
    moduleDocs = "docs/modules.md"
    siteRoot = "https://docs.koog.ai/"
}
