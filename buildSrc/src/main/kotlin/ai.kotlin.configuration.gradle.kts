import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
    }
}

val kotlinLanguageVersion = KotlinVersion.KOTLIN_2_1
val kotlinApiVersion = KotlinVersion.KOTLIN_2_1

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
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.1.21")
            }
        }
    }
}
