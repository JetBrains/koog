plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/koog-agents/tree/main")
            remoteLineSuffix = "#L"
        }
    }
}
