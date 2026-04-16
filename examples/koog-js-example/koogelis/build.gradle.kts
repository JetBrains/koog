
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// Force all dependencies to use the same version of kotlinx-coroutines and kotlinx-serialization
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                useVersion("1.10.2")
            }
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion("1.8.1")
            }
            if (requested.group == "io.ktor" && requested.name.startsWith("ktor")) {
                useVersion("3.2.2")
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xenable-suspend-function-exporting")
    }

    js {
        version = "0.0.1"
        outputModuleName = "koogelis"
        nodejs() // can also be browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
            implementation(libs.koog.agents)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.io)
            implementation(libs.kotlinx.coroutines.core)

            implementation("org.jetbrains.kotlinx:kotlinx-datetime-js:0.7.1-0.6.x-compat")

            implementation(kotlinWrappers.web)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime-js:0.7.1-0.6.x-compat")
        }
    }
}

