package ai.koog.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.create
import java.io.File
import java.util.jar.JarFile

/**
 * Extension to configure CheckSplitPackagesPlugin from Gradle Kotlin DSL.
 */
open class CheckSplitPackagesExtension(project: Project) {
    /**
     * Names or path segments of projects to include (e.g., "rag", "prompt", "koog-agents").
     * If empty, defaults to current behavior: all subprojects (when applied to root) or just the current project.
     */
    val includeProjects: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * A set of package name prefixes to include when detecting split packages.
     * Intentional name per request: "includePackages".
     * If empty, all packages are considered.
     */
    val includePackages: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * When true (default), the task fails the build on detecting split packages.
     * When false, the task logs warnings but does not fail the build.
     */
    val failOnError: Property<Boolean> =
        project.objects.property(Boolean::class.java).convention(true)
}

class CheckSplitPackagesPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Create extension for configuration via DSL: checkSplitPackages { include = listOf("...") }
        val ext = project.extensions.create<CheckSplitPackagesExtension>("checkSplitPackages", project)

        // We defer wiring until after evaluation so that the extension is configured by the build script.
        project.afterEvaluate {
            val isRoot = project == project.rootProject

            // Compute target projects according to extension.include when provided.
            val includeList = ext.includeProjects.getOrElse(emptySet()).map { it.trim() }.filter { it.isNotEmpty() }

            val baseTargets: Iterable<Project> = if (isRoot) project.subprojects else listOf(project)

            val targetProjects: Iterable<Project> = if (includeList.isEmpty()) {
                baseTargets
            } else {
                // Match by exact project name or any occurrence in the Gradle path (e.g., ":agents:")
                val includeSet = includeList.toSet()
                val all = if (isRoot) project.subprojects else listOf(project)
                all.filter { p ->
                    p.name in includeSet || includeList.any { inc -> p.path.contains(inc) }
                }
            }

            // Register per-project task where a classpath exists
            val shouldFail = ext.failOnError.get()
            val packagePrefixes =
                ext.includePackages.getOrElse(emptySet()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            targetProjects.forEach { subproj ->
                configureProject(subproj, shouldFail, packagePrefixes)
            }

            // Aggregate task at the root
            if (isRoot) {
                // Provide a root-level task named 'checkSplitPackages' so `./gradlew checkSplitPackages` aggregates.
                project.tasks.register("checkSplitPackages") {
                    group = "verification"
                    description = "Aggregates split package checks across selected subprojects."
                    dependsOn(
                        project.provider {
                            targetProjects.mapNotNull { sp ->
                                sp.tasks.findByName("checkSplitPackages")
                                    ?: sp.tasks.matching { it.name == "checkSplitPackages" }.singleOrNull()
                            }
                        }
                    )
                }
            }
        }
    }

    fun configureProject(project: Project, failOnError: Boolean, packagePrefixes: Set<String>) {
        project.afterEvaluate {
            // Try to find a resolvable JVM-related compile/runtime classpath configuration.
            val configs = project.configurations
            val cpConf =
                configs.findByName("compileClasspath")
                    ?: configs.findByName("runtimeClasspath")
                    ?: configs.findByName("jvmRuntimeClasspath")
                    ?: configs.findByName("jvmCompileClasspath")
                    ?: configs.firstOrNull { conf ->
                        conf.isCanBeResolved &&
                            (conf.name.endsWith("RuntimeClasspath") || conf.name.endsWith("CompileClasspath")) &&
                            !conf.name.contains("Test", ignoreCase = true) &&
                            // prefer JVM-like configurations if possible
                            (
                                conf.name.startsWith(prefix = "jvm", ignoreCase = true) || conf.name.equals(
                                    other = "runtimeClasspath",
                                    ignoreCase = true
                                ) || conf.name.equals("compileClasspath", true)
                                )
                    }

            if (cpConf == null) {
                // Not a resolvable JVM classpath (or not configured yet); skip task registration
                return@afterEvaluate
            }

            project.tasks.register("checkSplitPackages") {
                group = "verification"
                description = "Fails the build if any package appears in more than one JAR on the classpath."

                doLast {
                    val files = cpConf.resolve().filter { it.extension == "jar" }
                    if (files.isEmpty()) {
                        logger.lifecycle("No JARs on classpath for project ${project.path}; skipping.")
                        return@doLast
                    }

                    val packagesToJars = mutableMapOf<String, MutableSet<File>>()

                    files.forEach { jarFile ->
                        JarFile(jarFile).use { jar ->
                            jar.entries().asSequence()
                                .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                                .filter { !it.name.startsWith("META-INF/versions/") } // ignore multi-release duplicates
                                .mapNotNull { jarEntry ->
                                    val name = jarEntry.name
                                    val slash = name.lastIndexOf('/')
                                    if (slash <= 0) null else name.take(slash).replace('/', '.')
                                }
                                .filter { it.isNotBlank() }
                                .filter { pkg ->
                                    // If no prefixes specified, include all packages; otherwise require match by prefix or exact.
                                    packagePrefixes.isEmpty() || packagePrefixes.any { prefix ->
                                        pkg == prefix || pkg.startsWith("$prefix.")
                                    }
                                }
                                .toSet() // de-dup per JAR
                                .forEach { pkg ->
                                    packagesToJars.getOrPut(pkg) { linkedSetOf() }.add(jarFile)
                                }
                        }
                    }

                    // Build a quick index of jar -> owning subproject path if jar lies under a buildDir
                    val allProjects = project.rootProject.allprojects
                    fun ownerOf(jar: File): String? {
                        val abs = jar.absolutePath
                        return allProjects.firstOrNull { p -> abs.startsWith(p.layout.buildDirectory.asFile.get().absolutePath + File.separator) }?.path
                    }

                    val split = packagesToJars
                        .mapValues { (_, jars) -> jars.map { it to (ownerOf(it)) } }
                        .filterValues { pairs ->
                            pairs.map { it.first.absolutePath }.toSet().size > 1
                        } // distinct jar files > 1
                        .toSortedMap()

                    if (split.isNotEmpty()) {
                        val affected = split.keys.joinToString()
                        if (failOnError) {
                            logger.error("Detected split packages in ${project.path} (${split.size} packages): $affected")
                            printSplitDetails(logger, split, warnMode = false)
                            throw GradleException("️😱 Split packages detected in ${project.path}.")
                        } else {
                            logger.warn("Detected split packages in ${project.path} (${split.size} packages): $affected")
                            printSplitDetails(logger, split, warnMode = true)
                            logger.warn("⚠️ Split packages detected in ${project.path}, but failOnError=false. See details above.")
                        }
                    } else {
                        logger.info("✅ No split packages detected in ${project.path}.")
                    }
                }
            }
        }
    }

    private fun printSplitDetails(
        logger: Logger,
        split: Map<String, List<Pair<File, String?>>>,
        warnMode: Boolean
    ) {
        split.forEach { (pkg, jarPairs) ->
            val logLevel = if (warnMode) LogLevel.WARN else LogLevel.ERROR
            logger.log(logLevel, "- $pkg")
            jarPairs.map { it.first }.sortedBy { it.name }.forEach { jf ->
                val owner = jarPairs.firstOrNull { it.first == jf }?.second
                if (owner != null) {
                    logger.log(logLevel, "    in: ${jf.name}  (project: $owner)")
                } else {
                    logger.log(logLevel, "    in: ${jf.name}")
                }
            }
        }
    }
}
