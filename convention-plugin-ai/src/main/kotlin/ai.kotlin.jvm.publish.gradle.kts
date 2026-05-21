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

// In KMP+Android projects, Android publication tasks implicitly consume .asc files produced by
// signing tasks for other publications (e.g. signJvmPublication). Declare an explicit dependency
// so Gradle's work validation does not flag it as an implicit dependency problem.
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}
