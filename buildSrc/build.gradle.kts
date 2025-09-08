repositories {
    google()
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
    implementation(libs.android.tools.gradle)

    // Somewhat hacky way to access libs.version.toml in convention plugins.
    // IntelliJ can mark this code red, but it actually compiles.
    // https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("credentialsResolver") {
            id = "ai.koog.gradle.plugins.credentialsresolver"
            implementationClass = "ai.koog.gradle.plugins.CredentialsResolverPlugin"
        }
    }
}
