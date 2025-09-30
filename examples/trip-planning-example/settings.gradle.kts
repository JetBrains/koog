rootProject.name = "trip-planning-example"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // Public JetBrains repo with dev Koog builds
        maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    }
}
