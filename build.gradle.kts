import ai.grazie.gradle.fixups.DisableDistTasks.disableDistTasks
import ai.grazie.gradle.publish.maven.graziePublic
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

val graziePlatformVersion = libs.versions.grazie.platform.get()

group = "ai.koog"
version = run {
    // our version follows the semver specification

    val main = "0.1.0-alpha.5"

    val feat = run {
        val releaseBuild = !System.getenv("CE_IS_RELEASING_FROM_THE_DEFAULT_BRANCH").isNullOrBlank()
        val defaultBranch = System.getenv("CE_IS_RELEASING_FROM_THE_DEFAULT_BRANCH") == "true"
        val customVersion = System.getenv("CE_CUSTOM_VERSION")

        if (releaseBuild) {
            if (defaultBranch) {
                if (customVersion.isNullOrBlank()) {
                    ""
                } else {
                    throw GradleException("Custom version is not allowed during release from the default branch")
                }
            } else {
                if (!customVersion.isNullOrBlank()) {
                    "-feat-$customVersion"
                } else {
                    throw GradleException("Custom version is required during release from the non-default branch")
                }
            }
        } else {
            // do not care
            if (customVersion.isNullOrBlank()) {
                ""
            } else {
                "-feat-$customVersion"
            }
        }
    }

    "$main$feat+$graziePlatformVersion"
}

plugins {
    alias(libs.plugins.grazie)
    id("ai.kotlin.dokka")
}

allprojects {
    repositories {
        mavenCentral()
//        maven(url = "https://packages.jetbrains.team/maven/p/konfy/maven")
//        google()
        graziePublic(project)
    }
}

disableDistTasks()

subprojects {
    tasks.withType<Test> {
        testLogging {
            showStandardStreams = true
            showExceptions = true
            exceptionFormat = FULL
        }
        environment.putAll(
            mapOf(
                "ANTHROPIC_API_TEST_KEY" to System.getenv("ANTHROPIC_API_TEST_KEY"),
                "OPEN_AI_API_TEST_KEY" to System.getenv("OPEN_AI_API_TEST_KEY"),
            )
        )
    }
}

task("reportProjectVersionToTeamCity") {
    doLast {
        println("##teamcity[buildNumber '${project.version}']")
    }
}

/*
Based on https://docs.gradle.org/current/userguide/composite_builds.html.

Output example:

```
includeBuild(".../code-engine") { // path to the Code Engine location, it attaches the Code Engine sources to your project
    dependencySubstitution {
        ...
        // The next line replaces dependency on 'ai.jetbrains.code.features:code-features-kotlin' lib with dependency on ':code-features:code-features-kotlin' project
        // that comes from the project included above.
        substitute(module("ai.jetbrains.code.features:code-features-kotlin")).using(project(":code-features:code-features-kotlin"))
        ...
    }
}
```
*/
task("printConfigForLocalCodeEngine") {
    doLast {
        project.subprojects
            .filter { subproject -> subproject.buildFile.exists() }
            .map { subproject ->
                val artifact = subproject.group.toString() + ":" + subproject.name
                val projectPath = subproject.path
                artifact to projectPath
            }
            .sortedBy { (artifact, _) -> artifact }
            .joinToString(
                separator = "\n",
                prefix = """
                // add to settings.gradle.kts
                includeBuild("${project.rootDir.path}") {
                  dependencySubstitution {

            """.trimIndent(),
                postfix = """

                  }
                }
            """.trimIndent()
            ) { (artifact, projectPath) ->
                "    substitute(module(\"${artifact}\")).using(project(\"${projectPath}\"))"
            }
            .also { println(it) }
    }
}

dependencies {
    dokka(project(":agents:agents-core"))
    dokka(project(":agents:agents-features:agents-features-common"))
    dokka(project(":agents:agents-features:agents-features-memory"))
    dokka(project(":agents:agents-features:agents-features-trace"))
    dokka(project(":agents:agents-features:agents-features-event-handler"))
    dokka(project(":agents:agents-mcp"))
    dokka(project(":agents:agents-test"))
    dokka(project(":agents:agents-tools"))
    dokka(project(":agents:agents-utils"))
    dokka(project(":agents:agents-ext"))
    dokka(project(":embeddings:embeddings-base"))
    dokka(project(":embeddings:embeddings-llm"))
    dokka(project(":prompt:prompt-cache:prompt-cache-files"))
    dokka(project(":prompt:prompt-cache:prompt-cache-model"))
    dokka(project(":prompt:prompt-cache:prompt-cache-redis"))
    dokka(project(":prompt:prompt-executor:prompt-executor-cached"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms-all"))
    dokka(project(":prompt:prompt-executor:prompt-executor-model"))
    dokka(project(":prompt:prompt-llm"))
    dokka(project(":prompt:prompt-markdown"))
    dokka(project(":prompt:prompt-model"))
    dokka(project(":prompt:prompt-structure"))
    dokka(project(":prompt:prompt-xml"))
}
