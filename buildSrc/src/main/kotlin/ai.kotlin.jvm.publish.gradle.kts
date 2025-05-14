import ai.grazie.gradle.publish.maven.configureJvmJarManifest

plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

configureJvmJarManifest("jar")
