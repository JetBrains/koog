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
                // Point to git tag for releases, develop branch for snapshots
                val versionString = project.version.toString()
                val sourceRef = if (versionString.contains("-")) {
                    // most likely source ref is smth like 0.7.0-SNAPSHOT
                    "develop"
                } else {
                    versionString
                }
                remoteUrl("https://github.com/JetBrains/koog/tree/$sourceRef")
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
