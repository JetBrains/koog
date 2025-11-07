rootProject.name = "step-01-basic-agent"

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
    }
}

includeBuild("../../../.") {
    name = "koog"
}
