plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.logback.classic)
    implementation(libs.koog)
    implementation(libs.koog.test)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
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
