repositories {
    google()
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
}

plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

// FIXME Duplicated from the root Koog project for simplicity. Maybe should publish it properly
gradlePlugin {
    plugins {
        create("credentialsResolver") {
            id = "ai.koog.gradle.plugins.credentialsresolver"
            implementationClass = "ai.koog.gradle.plugins.CredentialsResolverPlugin"
        }
    }
}
