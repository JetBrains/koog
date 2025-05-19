repositories {
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.jetsign.gradle.plugin)
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
