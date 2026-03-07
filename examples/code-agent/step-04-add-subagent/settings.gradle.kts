rootProject.name = "step-04-add-sub-agent"

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
        // TODO temp local maven repo for a patched version of kotlinx-schema, delete when the fix is published
        maven {
            url = uri(rootProject.projectDir.resolve("../../../libs/maven-repo"))
        }
    }
}

includeBuild("../../../.") {
    name = "koog"
}
