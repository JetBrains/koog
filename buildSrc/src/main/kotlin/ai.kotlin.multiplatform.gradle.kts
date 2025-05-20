//import ai.grazie.gradle.tests.setupKarmaConfigs
import ai.grazie.gradle.publish.maven.configureJvmJarManifest
import ai.grazie.gradle.tests.configureTests
import jetbrains.sign.GpgSignSignatoryProvider
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("ai.kotlin.configuration")
    id("ai.kotlin.dokka")
    id("signing")
}

kotlin {
    jvm {
        configureTests()
    }

    js(IR) {
        browser {
            binaries.library()
        }

        configureTests()
    }
}

configureJvmJarManifest("jvmJar")

publishing {
    val javadocJar = configureEmptyJavadocArtifact()

    publications.withType(MavenPublication::class).all {
        artifact(javadocJar)
    }
}

fun configureEmptyJavadocArtifact(): org.gradle.jvm.tasks.Jar {
    val javadocJar by project.tasks.creating(Jar::class) {
        archiveClassifier.set("javadoc")
        // contents are deliberately left empty
        // https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
    }
    return javadocJar
}

val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
signing {
    if (isUnderTeamCity) {
        signatories = GpgSignSignatoryProvider()
        sign(publishing.publications)
    }
}

//setupKarmaConfigs()

plugins.withType<NodeJsRootPlugin>().configureEach {
    extensions.configure<NodeJsRootExtension> {
        downloadBaseUrl = "https://packages.jetbrains.team/files/p/grazi/node-mirror"
    }
}
