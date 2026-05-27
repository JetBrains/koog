import ai.koog.gradle.tests.configureJvmTests
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled = true
    }
}

configureJvmTests()

// Disable ABI validation tasks for beta modules. The isBeta extra property is set in
// each module's build.gradle.kts body, which runs after the plugins {} block applies
// this convention plugin, so we must defer the check to afterEvaluate.
afterEvaluate {
    if (extra["isBeta"] == true) {
        tasks.matching { it.name.contains("Abi") }.configureEach {
            enabled = false
        }
    }
}
