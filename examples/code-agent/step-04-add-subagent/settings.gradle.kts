rootProject.name = "step-04-basic-agent"

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
