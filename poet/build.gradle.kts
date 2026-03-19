
plugins {
    id("ai.kotlin.jvm")
}

group = rootProject.group
version = rootProject.version

kotlin {
    explicitApi()
}

kotlin {
    dependencies {
        // KSP2 API for programmatic invocation
        implementation(libs.ksp.symbol.processing.api)
        implementation(libs.ksp.symbol.processing.aa.embeddable)
        implementation(libs.ksp.symbol.processing.common.deps)

        implementation(libs.ksp.api)
        testImplementation(kotlin("test-junit5"))
        testImplementation(project(":test-utils"))
    }
}

tasks.test {
    useJUnitPlatform()
}
