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

        filters {
            excluded {
                annotatedWith.add("ai.koog.agents.core.annotation.InternalAgentsApi")
                annotatedWith.add("ai.koog.prompt.annotations.InternalPromptAPI")
                annotatedWith.add("ai.koog.agents.core.tools.annotations.InternalAgentToolsApi")
                annotatedWith.add("ai.koog.prompt.executor.clients.InternalLLMClientApi")
                annotatedWith.add("ai.koog.prompt.structure.annotations.InternalStructuredOutputApi")
                annotatedWith.add("ai.koog.serialization.annotations.InternalKoogSerializationApi")
                annotatedWith.add("ai.koog.a2a.annotations.InternalA2AApi")
            }
        }
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
