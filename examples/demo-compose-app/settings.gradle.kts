rootProject.name = "demo-compose-app"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public") {
            mavenContent {
                includeGroupAndSubgroups("ai.koog")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        // For Koog development only, skip this if you don't need to load any local dependencies.
        mavenLocal()
    }
}

include(
    ":androidApp",
    ":desktopApp",
    ":webApp",
    ":commonApp",
)
