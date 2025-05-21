plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        pluginsConfiguration.html {
            footerMessage = "Copyright © 2000-2025 JetBrains s.r.o."
        }

        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/koog/tree/main")
            remoteLineSuffix = "#L"
        }
    }
}
