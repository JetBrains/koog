import ai.grazie.gradle.fixups.DisableDistTasks.disableDistTasks
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.util.*

group = "ai.koog"
version = run {
    // our version follows the semver specification

    val main = "0.2.0"

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

    "$main$feat"
}

buildscript {
    dependencies {
        classpath("com.squareup.okhttp3:okhttp:4.12.0")
    }
}

plugins {
    alias(libs.plugins.grazie)
    id("ai.kotlin.dokka")
}

allprojects {
    repositories {
        mavenCentral()
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
                "GEMINI_API_TEST_KEY" to System.getenv("GEMINI_API_TEST_KEY"),
                "OPEN_ROUTER_API_TEST_KEY" to System.getenv("OPEN_ROUTER_API_TEST_KEY"),
                "OLLAMA_IMAGE_URL" to System.getenv("OLLAMA_IMAGE_URL"),
            )
        )
    }
}

task("reportProjectVersionToTeamCity") {
    doLast {
        println("##teamcity[buildNumber '${project.version}']")
    }
}

tasks {
    val packSonatypeCentralBundle by registering(Zip::class) {
        group = "publishing"

        subprojects {
            dependsOn(tasks.withType<PublishToMavenRepository>())
        }

        from(rootProject.layout.buildDirectory.dir("artifacts/maven"))
        archiveFileName.set("bundle.zip")
        destinationDirectory.set(layout.buildDirectory)
    }

    val publishMavenToCentralPortal by registering {
        group = "publishing"

        dependsOn(packSonatypeCentralBundle)

        doLast {
            val uriBase = "https://central.sonatype.com/api/v1/publisher/upload"

            val defaultBranch = System.getenv("CE_IS_RELEASING_FROM_THE_DEFAULT_BRANCH") == "true"
            val publishingType = if (defaultBranch) {
                println("Publishing from the default branch, so publishing as AUTOMATIC.")
                "AUTOMATIC"
            } else {
                println("Publishing from the non-default branch, so publishing as USER_MANAGED.")
                "USER_MANAGED" // do not publish releases from non-main branches without approval
            }

            val deploymentName = "${project.name}-$version"
            val uri = "$uriBase?name=$deploymentName&publishingType=$publishingType"

            val userName = System.getenv("CE_MVN_CLIENT_USERNAME") as String
            val token = System.getenv("CE_MVN_CLIENT_PASSWORD") as String
            val base64Auth = Base64.getEncoder().encode("$userName:$token".toByteArray()).toString(Charsets.UTF_8)
            val bundleFile = packSonatypeCentralBundle.get().archiveFile.get().asFile

            println("Sending request to $uri...")

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(uri)
                .header("Authorization", "Bearer $base64Auth")
                .post(
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("bundle", bundleFile.name, bundleFile.asRequestBody())
                        .build()
                )
                .build()
            client.newCall(request).execute().use { response ->
                val statusCode = response.code
                println("Upload status code: $statusCode")
                println("Upload result: ${response.body!!.string()}")
                if (statusCode != 201) {
                    error("Upload error to Central repository. Status code $statusCode.")
                }
            }
        }
    }
}

dependencies {
    dokka(project(":agents:agents-core"))
    dokka(project(":agents:agents-features:agents-features-common"))
    dokka(project(":agents:agents-features:agents-features-memory"))
    dokka(project(":agents:agents-features:agents-features-trace"))
    dokka(project(":agents:agents-features:agents-features-tokenizer"))
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
    dokka(project(":prompt:prompt-executor:prompt-executor-clients"))
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
    dokka(project(":prompt:prompt-tokenizer"))
    dokka(project(":prompt:prompt-xml"))
}
