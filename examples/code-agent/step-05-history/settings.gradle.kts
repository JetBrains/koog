rootProject.name = "step-05-basic-agent"

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
