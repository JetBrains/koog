//import ai.grazie.gradle.tests.setupKarmaConfigs
import ai.grazie.gradle.tests.configureTests
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
    kotlin("multiplatform")
    id("ai.kotlin.configuration")
}

kotlin {
    jvm {
        configureTests()
    }

    js(IR) {
        browser {
            binaries.library()
        }

        configureTests()
    }
}

//setupKarmaConfigs()

plugins.withType<NodeJsRootPlugin>().configureEach {
    extensions.configure<NodeJsRootExtension> {
        downloadBaseUrl = "https://packages.jetbrains.team/files/p/grazi/node-mirror"
    }
}
