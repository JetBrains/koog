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

    // Swift-callable iOS framework bundling the on-device smoke test. Dynamic (the
    // default), which the standard embedAndSignAppleFrameworkForXcode flow expects.
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "FoundationModelsSmoke"
        }
    }
}

// Intentionally no publishToMaven(): on-device example module, listed in the `excluded`
// sets in koog-agents/build.gradle.kts and koog-agents-additions/build.gradle.kts.
