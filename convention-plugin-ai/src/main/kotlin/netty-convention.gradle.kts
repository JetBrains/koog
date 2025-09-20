/**
 * Netty convention plugin that adds platform-specific Netty native transport libraries
 * to the jvmTest source set. This is similar to how Maven handles OS-specific dependencies
 * with profile activation.
 *
 * This plugin should be applied to any module that uses Netty for testing.
 */

import org.gradle.accessors.dm.LibrariesForLibs

val nettyVersion = the<LibrariesForLibs>().versions.netty.get()

plugins {
    id("org.gradle.base")
}

// This plugin is applied to projects that already have the kotlin multiplatform plugin applied
// It adds the Netty native transport libraries to the jvmTest source set

afterEvaluate {

    extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()?.apply {
        sourceSets.findByName("jvmTest")?.apply {
            dependencies {
                // Netty native transport libraries for different platforms
                val osName = System.getProperty("os.name").lowercase()
                val osArch = System.getProperty("os.arch").lowercase()

                // Add the base Netty platform
                implementation(project.dependencies.platform("io.netty:netty-bom:$nettyVersion"))

                when {
                    osName.contains("linux") -> {
                        val archClassifier =
                            if (osArch.contains("aarch64")) {
                                "linux-aarch_64"
                            } else {
                                "linux-x86_64"
                            }
                        runtimeOnly(
                            "io.netty:netty-transport-native-epoll:$nettyVersion:$archClassifier",
                        )
                    }

                    osName.contains("mac") -> {
                        val archClassifier =
                            if (osArch.contains("aarch64")) {
                                "osx-aarch_64"
                            } else {
                                "osx-x86_64"
                            }
                        runtimeOnly(
                            "io.netty:netty-transport-native-kqueue:$nettyVersion:$archClassifier",
                        )
                        runtimeOnly(
                            "io.netty:netty-resolver-dns-native-macos:$nettyVersion:$archClassifier",
                        )
                    }
                }
            }
        }
    }
}
