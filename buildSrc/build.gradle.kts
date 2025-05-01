repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("credentialsResolver") {
            id = "ai.grazie.gradle.plugins.credentialsresolver"
            implementationClass = "ai.grazie.gradle.plugins.CredentialsResolverPlugin"
        }
    }
}
