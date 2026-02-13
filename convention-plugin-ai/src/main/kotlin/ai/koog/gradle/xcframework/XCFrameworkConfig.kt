package ai.koog.gradle.xcframework

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

/**
 * Helper object for configuring XCFramework builds in Kotlin Multiplatform projects.
 *
 * XCFramework building is opt-in via the Gradle property `koog.build.xcframework=true`.
 * This prevents slow framework compilation during regular CI runs (assemble, test, etc.).
 *
 * Usage:
 * ```
 * ./gradlew {module} assembleKoogXCFramework -Pkoog.build.xcframework=true
 * ```
 */
object XCFrameworkConfig {

    private const val PROPERTY_NAME = "koog.build.xcframework"

    /**
     * Checks if XCFramework building is enabled via the Gradle property.
     */
    fun isEnabled(project: Project): Boolean =
        project.findProperty(PROPERTY_NAME)?.toString()?.toBoolean() ?: false

    /**
     * Configures XCFramework for iOS targets if enabled.
     * Creates framework binaries for all targets defined for the project.
     *
     * @param project The Gradle project
     */
    fun KotlinMultiplatformExtension.configureXCFrameworkIfRequested(project: Project) {
        if (isEnabled(project)) {
            val xcf = project.XCFramework("Koog")
            targets.withType<KotlinNativeTarget>()
                .matching { it.konanTarget.family.isAppleFamily }
                .configureEach {
                    binaries.framework {
                        baseName = "Koog"
                        binaryOption("bundleId", "ai.koog")
                        xcf.add(this)
                    }
                }
        }
    }

    /**
     * Configures framework exports for all native targets if XCFramework building is enabled.
     * This exports all specified projects to the iOS framework, making them available to Swift.
     *
     * @param project The Gradle project
     * @param projectPaths Set of project paths to export (e.g., ":agents:agents-core")
     */
    fun KotlinMultiplatformExtension.configureFrameworkExportsIfRequested(
        project: Project,
        projectPaths: Set<String>
    ) {
        if (isEnabled(project)) {
            targets.withType<KotlinNativeTarget>().configureEach {
                binaries.withType(Framework::class.java).configureEach {
                    projectPaths.forEach {
                        export(project.project(it))
                    }
                }
            }
        }
    }
}
