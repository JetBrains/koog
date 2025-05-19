plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/koog/tree/main")
            remoteLineSuffix = "#L"
        }
    }
}
