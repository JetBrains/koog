import ai.grazie.gradle.Secrets
import ai.grazie.gradle.fixups.DisableDistTasks.disableDistTasks
import ai.grazie.gradle.publish.maven.graziePublic
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

val graziePlatformVersion = libs.versions.grazie.platform.get()

group = "ai.jetbrains.code"
version = run {
    // our version follows the semver specification

    val main = "1.0.0-beta.55"

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
        maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
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
                "USER_STGN_JWT_TOKEN" to
                        (project.properties["grazieUserStgnToken"] ?: System.getenv("USER_STGN_JWT_TOKEN")),
                "JB_SPACE_CLIENT_ID" to Secrets.Space.Maven.username(project),
                "JB_SPACE_CLIENT_SECRET" to Secrets.Space.Maven.password(project),
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

task("notifyAgentsReleaseToSlack") {
    doLast {
        val slackAppSecret = System.getenv("SLACK_AGENTS_APP_SECRET")
            ?: error("Slack APP secret not found. Please set the SLACK_AGENTS_APP_SECRET environment variable.")

        val gitLogCmd = "git log \$(git describe --tags --abbrev=0)..HEAD --pretty=format:%H\\ %s"
        val gitLog = ProcessBuilder("sh", "-c", gitLogCmd)
            .directory(project.rootDir)
            .start()
            .inputStream
            .bufferedReader()
            .readText()

        val agentCommits = gitLog.lines()
            .asSequence()
            .filter {
                it.contains("[agents") || it.contains("(agents") ||
                        it.contains("ideformer") || it.contains("IdeFormer") || it.contains("JBRes-2755")
            }
            .map {
                it.replace(Regex("""^[a-f0-9]{40}(?=\s)""")) { match ->
                    "<https://github.com/JetBrains/code-engine/commit/${match.value}|${match.value.take(8)}>"
                }.replace(Regex("""(JBAI|JBRes)-\d+""")) { match ->
                    "<https://youtrack.jetbrains.com/issue/${match.value}|${match.value}>"
                }.replace(Regex("""\(#\d+\)""")) { match ->
                    val number = match.value.removePrefix("(#").removeSuffix(")")
                    "<https://github.com/JetBrains/code-engine/pull/$number|#$number>"
                }
            }
            .joinToString(separator = "\n", prefix = "\n") { " - _${it}_" }

        if (agentCommits.isNotEmpty()) {
            val version = project.version.toString()
            val message = """
                <!here>, *Hey, a new release of the Agentic Platform is here!*
                *new version:* `$version`
                *changelist:*
            """.trimIndent() + agentCommits

            val slackPayload = """
                {
                    "channel": "ai-agents-ideformer",
                    "text": ${"\"$message\""}
                }
            """.trimIndent()

            ProcessBuilder(
                "curl", "-X", "POST", "-H", "Content-Type: application/json",
                "-H", "Authorization: Bearer $slackAppSecret",
                "-d", slackPayload,
                "https://slack.com/api/chat.postMessage"
            )
                .start()
                .waitFor()
        }
    }
}

dependencies {
    dokka(project(":agents:agents-core"))
    dokka(project(":agents:agents-features:agents-features-common"))
    dokka(project(":agents:agents-features:agents-features-memory"))
    dokka(project(":agents:agents-features:agents-features-trace"))
    dokka(project(":agents:agents-test"))
    dokka(project(":agents:agents-tools"))
    dokka(project(":embeddings:embeddings-base"))
    dokka(project(":embeddings:embeddings-local"))
    dokka(project(":prompt:prompt-agents"))
    dokka(project(":prompt:prompt-cache:prompt-cache-files"))
    dokka(project(":prompt:prompt-cache:prompt-cache-model"))
    dokka(project(":prompt:prompt-cache:prompt-cache-redis"))
    dokka(project(":prompt:prompt-executor:prompt-executor-cached"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms-all"))
    dokka(project(":prompt:prompt-executor:prompt-executor-model"))
    dokka(project(":prompt:prompt-executor:prompt-executor-ollama"))
    dokka(project(":prompt:prompt-executor:prompt-executor-tools"))
    dokka(project(":prompt:prompt-llm"))
    dokka(project(":prompt:prompt-markdown"))
    dokka(project(":prompt:prompt-model"))
    dokka(project(":prompt:prompt-structure"))
    dokka(project(":prompt:prompt-xml"))
}
