import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.konan.target.KonanTarget

val isBeta by extra(true)

plugins {
    id("ai.kotlin.multiplatform")
}

group = rootProject.group
version = rootProject.version

kotlin {
    sourceSets {
        appleMain {
            dependencies {
                api(project(":prompt:prompt-executor:prompt-executor-clients"))
                api(project(":prompt:prompt-llm"))
            }
        }
        appleTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":agents:agents-core"))
                implementation(project(":prompt:prompt-executor:prompt-executor-model"))
            }
        }
    }

    explicitApi()

    // Per target: swiftc compiles the Swift shim into a static lib + Obj-C header;
    // cinterop embeds the lib into the klib and carries the link flags, so downstream
    // binaries link with no extra wiring. Bindings are leaf-source-set-only; appleMain
    // reaches them via the expect/actual defaultFoundationModelsSession().
    val swiftSrc = layout.projectDirectory.file("src/appleInterop/swift/KoogFMBridge.swift")

    // iosArm64() / iosSimulatorArm64() / iosX64() are already registered by the
    // convention plugin; calling the accessors again returns the existing targets.
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        val cap = target.targetName.replaceFirstChar { it.uppercase() } // e.g. IosArm64
        val outDir = layout.buildDirectory.dir("swiftShim/${target.targetName}")

        val (sdk, triple) = when (target.konanTarget) {
            KonanTarget.IOS_ARM64 -> "iphoneos" to "arm64-apple-ios26.0"
            KonanTarget.IOS_SIMULATOR_ARM64 -> "iphonesimulator" to "arm64-apple-ios26.0-simulator"
            KonanTarget.IOS_X64 -> "iphonesimulator" to "x86_64-apple-ios26.0-simulator"
            else -> error("Unexpected target ${target.konanTarget}")
        }

        val swiftcTask = tasks.register<Exec>("swiftc$cap") {
            onlyIf { OperatingSystem.current().isMacOsX }
            inputs.file(swiftSrc)
            outputs.dir(outDir)
            executable = "xcrun"
            doFirst {
                val sdkPath = providers.exec {
                    commandLine("xcrun", "--sdk", sdk, "--show-sdk-path")
                }.standardOutput.asText.get().trim()
                val dir = outDir.get().asFile.also { it.mkdirs() }
                args(
                    "--sdk", sdk, "swiftc",
                    "-target", triple, "-sdk", sdkPath,
                    "-emit-library", "-static",
                    "-emit-objc-header",
                    "-emit-objc-header-path", dir.resolve("KoogFMBridge.h").absolutePath,
                    "-module-name", "KoogFMBridge",
                    "-o", dir.resolve("libKoogFMBridge.a").absolutePath,
                    "-swift-version", "6", "-strict-concurrency=complete",
                    swiftSrc.asFile.absolutePath,
                )
            }
        }

        val mainCompilation = target.compilations.getByName("main")

        mainCompilation.cinterops.create("foundationModels") {
            // The .def carries the machine-independent pieces; this block injects the
            // per-target header/library search paths. Keep -rpath on /usr/lib/swift
            // (the OS Swift runtime): the toolchain's swift-5.5 overlay loads a second
            // runtime and duplicates objc classes.
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/foundationModels.def"))
            compilerOpts("-I${outDir.get().asFile.absolutePath}")
            extraOpts("-libraryPath", outDir.get().asFile.absolutePath)
        }

        // cinterop's built-in up-to-date inputs cover the .def only, not the header/.a
        // it embeds from outDir; track them so a shim rebuild re-embeds instead of
        // shipping a stale klib. dependsOn also guarantees the .a exists on first build.
        tasks.matching { it.name == "cinteropFoundationModels$cap" }
            .configureEach {
                dependsOn(swiftcTask)
                inputs.dir(outDir)
            }

        // FoundationModels is iOS-26-only: raise the K/N deployment floor on the
        // compilation so cinterop + compileKotlin*Native type the 26-only @objc symbol.
        mainCompilation.compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add(
                "-Xoverride-konan-properties=osVersionMin.${target.konanTarget.name}=26.0",
            )
        }
    }
}

publishToMaven()
