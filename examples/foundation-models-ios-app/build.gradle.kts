val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        appleMain {
            dependencies {
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-foundationmodels-client"))
                implementation(project(":agents:agents-core"))
                implementation(project(":prompt:prompt-executor:prompt-executor-model"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }

    explicitApi()

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "FoundationModelsSmoke"
        }
    }
}
