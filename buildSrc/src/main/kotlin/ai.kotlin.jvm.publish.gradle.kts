import ai.koog.gradle.publish.maven.configureJvmJarManifest
import jetbrains.sign.GpgSignSignatoryProvider

plugins {
    kotlin("jvm")
    `maven-publish`
    id("signing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

configureJvmJarManifest("jar")

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        signatories = GpgSignSignatoryProvider()
        sign(publishing.publications)
    }
}
