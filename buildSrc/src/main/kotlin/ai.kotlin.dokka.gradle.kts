import org.jetbrains.dokka.gradle.*

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/koan-agents/tree/main")
            remoteLineSuffix = "#L"
        }
    }
}
