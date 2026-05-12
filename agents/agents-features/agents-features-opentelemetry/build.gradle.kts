import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import com.android.build.api.variant.LibraryAndroidComponentsExtension


plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

val rootProjectVersion = rootProject.version.toString()
val rootProjectGroup = rootProject.group.toString()

abstract class GenerateProductProperties : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val productVersion: Property<String>

    @get:Input
    abstract val productName: Property<String>

    @TaskAction
    fun generate() {
        val file = outputDir.get().file("product.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=${productVersion.get()}\nname=${productName.get()}\n")
    }
}

val generateProductProperties = tasks.register<GenerateProductProperties>("generateProductProperties") {
    outputDir.set(layout.buildDirectory.dir("generated/resources"))
    productVersion.set(rootProjectVersion)
    productName.set(rootProjectGroup)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-utils"))
                api(libs.kotlinx.serialization.json)
                implementation(project(":agents:agents-mcp-metadata"))

                api(libs.opentelemetry.kotlin.core)
                api(libs.opentelemetry.kotlin.sdk.api)
                api(libs.opentelemetry.kotlin.implementation)
                api(libs.opentelemetry.kotlin.exporters.core)
                api(libs.opentelemetry.kotlin.semconv)

                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        // The Kotlin SDK ships no metrics module.
        // Keep the Java OTel SDK on JVM target for metrics support and for users who need to wrap
        // a Java OTel SpanExporter via `toOtelKotlinSpanExporter()` and pass it to `addSpanProcessor`.
        jvmCommonMain {
            dependencies {
                api(project.dependencies.platform(libs.opentelemetry.bom))
                api(libs.opentelemetry.sdk)
                api(libs.opentelemetry.kotlin.compat)
            }

            // Provider form auto-wires jvmProcessResources -> generateProductProperties.
            // For the AAR, see the androidComponents block below — AGP ignores
            // kotlin.sourceSets resources entirely.
            resources.srcDir(generateProductProperties.map { it.outputDir })
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.opentelemetry.kotlin.exporters.inMemory)
                implementation(libs.ktor.client.mock)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(project(":agents:agents-test"))
                implementation(libs.junit.jupiter.params)

                // Real Ktor engine for jvmTest paths that need an actual HTTP transport
                // (env-var-gated Langfuse / Weave integration tests against the real cloud).
                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()

    sourceSets.all {
        languageSettings.optIn("io.opentelemetry.kotlin.ExperimentalApi")
        languageSettings.optIn("io.opentelemetry.kotlin.semconv.IncubatingApi")
    }
}

// AGP ignores kotlin.sourceSets resources — register the generated dir with the Android variant
// pipeline so it lands in the AAR's classes.jar. addGeneratedSourceDirectory both adds the dir as
// a source for AGP's java-resource processing and wires the producing task automatically.
extensions.configure<LibraryAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(
            generateProductProperties,
            GenerateProductProperties::outputDir,
        )
    }
}

// OTel Kotlin SDK 0.3.0 doesn't publish wasmJs artifacts, so the wasmJs target can't
// actually compile against OTel. We keep the target registered (so its KMP publication
// has the standard `agents-features-opentelemetry-wasm-js` coordinates that KMP consumers
// expect) but exclude OTel from wasmJs configurations and skip wasmJs tasks so nothing
// tries to build/publish broken wasmJs artifacts.
configurations.matching { it.name.startsWith("wasmJs") }.configureEach {
    exclude(group = "io.opentelemetry.kotlin")
}
tasks.matching { it.name.startsWith("wasmJs") || it.name == "compileKotlinWasmJs" || it.name == "compileTestKotlinWasmJs" }.configureEach {
    onlyIf("OTel Kotlin SDK 0.3.0 does not publish wasmJs artifacts") { false }
}

publishToMaven()
