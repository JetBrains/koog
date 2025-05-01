package ai.grazie.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.process.internal.ExecException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * This plugin provides a way to read properties file while attempting to inject credentials using 1password CLI.
 * More info about injection here: https://developer.1password.com/docs/cli/reference/commands/inject
 */
@Suppress("unused")
class CredentialsResolverPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add("credentialsResolver", CredentialsResolverExtension(project))
    }
}

class CredentialsResolverExtension(private val project: Project) {
    private val logger = Logging.getLogger(javaClass)

    private fun doResolve(file: File): Map<String, String> {
        if (!file.exists()) {
            logger.warn("Cannot find credentials file '${file.absolutePath}'. No credentials will be loaded")
            return emptyMap()
        }

        val output = try {
            ByteArrayOutputStream().use {
                project.exec {
                    commandLine("op", "inject", "-i", file.absolutePath)
                    standardOutput = it
                }
                it.toByteArray()
            }
        } catch (e: ExecException) {
            logger.warn("Cannot use 1password CLI 2, reading file without credentials injection")
            file.readBytes()
        }

        return ByteArrayInputStream(output).use {
            Properties().run {
                load(it)
                map { (k, v) -> k.toString() to v.toString() }.toMap()
            }
        }
    }

    @Suppress("unused")
    fun resolve(file: Provider<RegularFile>): MapProperty<String, String> = project.objects.mapProperty<String, String>().apply {
        convention(file.map { file ->
            file.asFile.takeIf { it.name.endsWith(".properties") }
                ?.let { doResolve(it) }
                ?: throw IllegalArgumentException("Credentials file must be properties file")
        })

        disallowChanges()
    }
}