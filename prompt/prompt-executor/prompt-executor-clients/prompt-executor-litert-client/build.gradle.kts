import ai.koog.gradle.publish.maven.Publishing.publishToMaven

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.android.litertlm)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }

        // litertlm-kmp has no iosX64 build, so this code cannot be in iosMain.
        val iosArmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.ios.litertlm)
            }
        }

        val iosArmCommonTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        iosArm64Main {
            dependsOn(iosArmCommonMain)
        }

        iosSimulatorArm64Main {
            dependsOn(iosArmCommonMain)
        }

        iosArm64Test {
            dependsOn(iosArmCommonTest)
        }

        iosSimulatorArm64Test {
            dependsOn(iosArmCommonTest)
        }
    }

    explicitApi()
}

publishToMaven()
