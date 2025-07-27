import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

kotlin {
    // Override targets to exclude WASM (Kottage doesn't support WASM)
    jvm()
    js(IR) {
        browser {
            binaries.library()
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-features:agents-features-common"))
                api(project(":agents:agents-features:agents-features-memory"))
                api(project(":agents:agents-features:agents-features-snapshot"))

                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                
                // Kottage for SQLite-based key-value storage
                implementation("io.github.irgaly.kottage:kottage:1.8.0")
                
                // cryptography-kotlin for multiplatform encryption
                implementation("dev.whyoleg.cryptography:cryptography-core:0.5.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.5.0")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                // Additional JVM-specific crypto dependencies if needed
                implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(libs.mockk)
            }
        }
    }

    explicitApi()
}

// Configure JVM target for testing like in original multiplatform plugin
kotlin.jvm {
    testRuns["test"].executionTask.configure {
        useJUnitPlatform()
    }
}

publishToMaven()