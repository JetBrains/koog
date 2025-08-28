import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val libs = the<LibrariesForLibs>()

val kotlinLanguageVersion = KotlinVersion.KOTLIN_2_1
val kotlinApiVersion = KotlinVersion.KOTLIN_2_1
val kotlinCoreLibrariesVersion = "2.1.0"

extensions.getByType<KotlinProjectExtension>().apply {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets.all {
        languageSettings {
            // K/Common
            optIn("kotlin.RequiresOptIn")
            optIn("kotlinx.serialization.ExperimentalSerializationApi")
            optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            // K/JS
            optIn("kotlin.js.ExperimentalJsExport")
        }

        /*
         Advise the correct version of kotlin stdlib and core libraries.
         This does not fix the problem when some library brings the higher version of Kotlin transitively.
         We can't use enforcedPlatform() with implementation, because it would leak this Kotlin version constraint to the consumers.
         We can't use enforcedPlatform() with compileOnly either, because not all KMP targets support compileOnly.
         That's why below we configure resolutionStrategy manually, to pick the right version of Kotlin.
         */
        dependencies {
            implementation(project.dependencies.platform(libs.kotlin.bom))
        }

        coreLibrariesVersion = kotlinCoreLibrariesVersion
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        languageVersion.set(kotlinLanguageVersion)
        logger.info("'$path' Kotlin language version: $kotlinLanguageVersion")
        apiVersion.set(kotlinApiVersion)
        logger.info("'$path' Kotlin API version: $kotlinApiVersion")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            /*
             Manually align Kotlin version with the one defined in the Kotlin BOM without leaking strict version
             constraints consumers.
             */
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(requireNotNull(libs.kotlin.bom.get().version))
                because("Kotlin dependencies should use version from the Kotlin BOM")
            }
        }
    }
}
