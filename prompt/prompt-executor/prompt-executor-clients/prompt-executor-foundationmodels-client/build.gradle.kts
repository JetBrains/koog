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

    // --- FoundationModels @objc Swift shim + hand-written cinterop wiring ---
    // This is the repo's first cinterop/Swift integration. For each iOS target we:
    //   1. compile src/appleInterop/swift/KoogFMBridge.swift with `swiftc` into a static
    //      lib + Obj-C header (per-target SDK + triple),
    //   2. feed that header to a hand-written cinterop (.def) to generate Kotlin bindings,
    //   3. link the static lib + FoundationModels.framework into every binary.
    // Verified on this machine (Xcode 26.3, Swift 6.2.4, iPhoneSimulator26.2 SDK): the
    // simulator/device/x64 swiftc + cinterop + link all succeed with the flags below and need
    // no extra Swift-runtime -L paths at LINK time. They do need one -rpath at RUNTIME so the
    // statically-linked Swift shim can load libswift_Concurrency.dylib etc. — see the -rpath
    // note on target.binaries.all{} below (first surfaces when a simulator test that links the
    // shim is actually run, not at link time).
    //
    // NOTE for the integration author (Task 6): the `foundationModels` cinterop is created
    // on each iOS target, so its generated bindings are visible ONLY from the leaf
    // `iosArm64Main` / `iosSimulatorArm64Main` / `iosX64Main` source sets — NOT from shared
    // `appleMain`. This is intentional: the cinterop-touching binding (CInteropFoundationModelsSession)
    // lives in those three leaf source sets and is surfaced to appleMain via an
    // `internal expect fun defaultFoundationModelsSession()` factory (actual per leaf target).
    // All other client logic stays in appleMain and never references the binding directly.
    // Do NOT enable `kotlin.mpp.enableCInteropCommonization` — the design (spec §5c) deliberately
    // rejects that repo-global experimental flag for this first beta cinterop; the
    // "CInterop Commonization Disabled" warning KGP prints is expected and benign.
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
            // KGP 2.3.10 exposes the `definitionFile` Property (confirmed: `defFile` is the
            // deprecated alias). The .def lists only `headers = KoogFMBridge.h`; the include
            // dir for that header is injected here via compilerOpts so the .def stays
            // machine-independent.
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/foundationModels.def"))
            compilerOpts("-I${outDir.get().asFile.absolutePath}")
        }

        // The generated cinterop task is `cinteropFoundationModels<Target>`
        // (e.g. cinteropFoundationModelsIosSimulatorArm64), confirmed during the spike.
        // Use matching/configureEach so a name mismatch fails loudly rather than silently
        // skipping the swiftc dependency.
        tasks.matching { it.name == "cinteropFoundationModels$cap" }
            .configureEach { dependsOn(swiftcTask) }

        // Raise the iOS deployment floor to 26 (FoundationModels is iOS-26-only). This is a
        // K/N COMPILER-property override and must apply during cinterop + compileKotlin*Native
        // (which run before linking and first type the 26-only @objc symbol), so it goes on the
        // COMPILATION, not on binaries.all{}. The per-target konan property key is
        // osVersionMin.<konanTarget.name> (ios_arm64 / ios_simulator_arm64 / ios_x64).
        mainCompilation.compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add(
                "-Xoverride-konan-properties=osVersionMin.${target.konanTarget.name}=26.0",
            )
        }

        // Link the static shim + the framework into every binary; the link must also wait for
        // the .a so `-lKoogFMBridge` resolves.
        //
        // -rpath /usr/lib/swift: the static Swift shim references the Swift runtime
        // (libswift_Concurrency.dylib etc. — KoogFMBridge uses `Task`/async). The OS ships those
        // dylibs in `/usr/lib/swift`: on a real iOS-26 device that is the literal path, and on the
        // simulator the loader reroots it under the runtime root via DYLD_ROOT_PATH
        // (…/<runtime>.simruntime/Contents/Resources/RuntimeRoot/usr/lib/swift). This is the OS's
        // own copy, so it loads exactly once. Do NOT point this at the Xcode toolchain's
        // usr/lib/swift-5.5 overlay — that loads a second, back-deploy runtime alongside the OS
        // one and triggers "Class … implemented in both …" objc duplicate-class warnings
        // ("may cause spurious casting failures and mysterious crashes"). First surfaces when a
        // simulator TEST that links the shim is actually run (Task 7), not at link time.
        target.binaries.all {
            linkerOpts(
                "-L${outDir.get().asFile.absolutePath}",
                "-lKoogFMBridge",
                "-framework", "FoundationModels",
                "-rpath", "/usr/lib/swift",
            )
            linkTaskProvider.configure { dependsOn(swiftcTask) }
        }
    }
}

publishToMaven()
