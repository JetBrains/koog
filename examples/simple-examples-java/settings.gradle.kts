rootProject.name = "simple-examples-java"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
        // Koog will be published here from sources
        mavenLocal()
    }
}
