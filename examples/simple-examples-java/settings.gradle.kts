import java.net.URI

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
        maven { url = URI("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public") }
    }
}
