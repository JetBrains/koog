plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets {
        configureEach {
            includes.from("Module.md")

            pluginsConfiguration.html {
                footerMessage = "Copyright © 2000-2025 JetBrains s.r.o."
            }

            sourceLink {
                localDirectory = rootDir
                remoteUrl("https://github.com/JetBrains/koog/tree/main")
                remoteLineSuffix = "#L"
            }

            externalDocumentationLinks.register("ktor-client") {
                url("https://api.ktor.io/ktor-client/")
                packageListUrl("https://api.ktor.io/package-list")
            }

            externalDocumentationLinks.register("kotlinx-coroutines") {
                url("https://kotlinlang.org/api/kotlinx.coroutines/")
                packageListUrl("https://kotlinlang.org/api/kotlinx.coroutines/package-list")
            }

            externalDocumentationLinks.register("kotlinx-serialization") {
                url("https://kotlinlang.org/api/kotlinx.serialization/")
                packageListUrl("https://kotlinlang.org/api/kotlinx.serialization/package-list")
            }

            externalDocumentationLinks.register("mcp-kotlin-sdk") {
                url("https://modelcontextprotocol.github.io/kotlin-sdk/")
                packageListUrl("https://modelcontextprotocol.github.io/kotlin-sdk/package-list")
            }
        }
    }
}
