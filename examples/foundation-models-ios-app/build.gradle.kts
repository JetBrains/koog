import org.gradle.internal.os.OperatingSystem

// Marks the module as beta so the convention plugin disables ABI-validation tasks
// (mirrors the FM client). This module is an on-device example and is not published.
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
                // Leaf example module: everything is `implementation` (nothing is re-exported).
                implementation(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-foundationmodels-client"))
                implementation(project(":agents:agents-core"))
                implementation(project(":prompt:prompt-executor:prompt-executor-model"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }

    explicitApi()

    // Produce a Swift-callable iOS framework that bundles the FoundationModels on-device
    // smoke test (`runFoundationModelsSmokeTest`). We declare the framework on the two
    // mandatory iOS targets (device arm64 + simulator arm64) plus the x64 simulator.
    //
    // The framework is kept DYNAMIC (the default), which is what the standard
    // `embedAndSignAppleFrameworkForXcode` flow expects.
    //
    // FoundationModels is iOS-26-only, so each target's main compile task carries the same
    // K/N iOS-26 deployment-floor override the FM client uses (-Xoverride-konan-properties).
    //
    // The FM client links its Swift shim (libKoogFMBridge.a) + FoundationModels.framework via
    // linkerOpts on ITS OWN binaries; those opts live on the FM client's binaries, NOT in the
    // cinterop .def, so they do NOT propagate to this downstream framework's link. We therefore
    // re-declare the same native link here, pointing at the FM client's per-target shim output
    // dir, and add a task dependency on the FM client's `swiftc<Cap>` task so the `.a` exists
    // before this framework links. (-rpath /usr/lib/swift is needed at runtime so the statically
    // linked Swift shim can load the Swift runtime dylibs the OS ships — see the FM client build.)
    val fmClient = project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-foundationmodels-client")

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        val cap = target.targetName.replaceFirstChar { it.uppercase() } // e.g. IosArm64
        val fmShimDir = fmClient.layout.buildDirectory.dir("swiftShim/${target.targetName}")

        target.binaries.framework {
            baseName = "FoundationModelsSmoke"
        }

        // Raise the iOS deployment floor to 26 on the main compilation (FoundationModels is
        // iOS-26-only); same compiler-property override as the FM client.
        target.compilations.getByName("main").compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add(
                "-Xoverride-konan-properties=osVersionMin.${target.konanTarget.name}=26.0",
            )
        }

        // Re-declare the FM native link on this module's binaries (the FM client's linkerOpts do
        // not propagate through the cinterop klib — verified empirically: without this, the
        // framework link fails with `Undefined symbols: _OBJC_CLASS_$__TtC12KoogFMBridge...`) and
        // wait for its shim `.a` to be produced.
        target.binaries.all {
            linkerOpts(
                "-L${fmShimDir.get().asFile.absolutePath}",
                "-lKoogFMBridge",
                "-framework",
                "FoundationModels",
                "-rpath",
                "/usr/lib/swift",
            )
            linkTaskProvider.configure {
                onlyIf { OperatingSystem.current().isMacOsX }
                dependsOn("${fmClient.path}:swiftc$cap")
            }
        }
    }
}

// NOTE: intentionally no `publishToMaven()` — this is an on-device example module excluded from
// publishing (see the `excluded` set in koog-agents/build.gradle.kts and
// koog-agents-additions/build.gradle.kts).
