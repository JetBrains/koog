import ai.koog.gradle.fixups.DisableDistTasks.disableDistTasks
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.util.*

group = "ai.koog"
version = run {
    // our version follows the semver specification

    val main = "0.3.0"

    val feat = run {
        val releaseBuild = !System.getenv("BRANCH_KOOG_IS_RELEASING_FROM").isNullOrBlank()
        val branch = System.getenv("BRANCH_KOOG_IS_RELEASING_FROM")
        val customVersion = System.getenv("CE_CUSTOM_VERSION")
        val tcCounter = System.getenv("TC_BUILD_COUNTER")

        if (releaseBuild) {
            when (branch) {
                "main" -> {
                    if (customVersion.isNullOrBlank()) {
                        ""
                    } else {
                        throw GradleException("Custom version is not allowed during release from the main branch")
                    }
                }
                "develop" -> {
                    if (!customVersion.isNullOrBlank()) {
                        throw GradleException("Custom version is not allowed during release from the develop branch")
                    } else if (tcCounter.isNullOrBlank()) {
                        throw GradleException("TC_BUILD_COUNTER is required during release from the develop branch")
                    } else {
                        ".$tcCounter"
                    }
                }
                else -> {
                    if (!customVersion.isNullOrBlank()) {
                        "-feat-$customVersion"
                    } else {
                        throw GradleException("Custom version is required during release from a feature branch")
                    }
                }
            }
        } else {
            // do not care
            ""
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
    id("ai.kotlin.dokka")
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.ktlint)
}

allprojects {
    repositories {
        mavenCentral()
    }
}

disableDistTasks()

// Apply Kover and ktlint to all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

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
                "AWS_SECRET_ACCESS_KEY" to System.getenv("AWS_SECRET_ACCESS_KEY"),
                "AWS_ACCESS_KEY_ID" to System.getenv("AWS_ACCESS_KEY_ID"),
            )
        )
    }
}

tasks.register("reportProjectVersionToTeamCity") {
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

    @Suppress("unused")
    val publishMavenToCentralPortal by registering {
        group = "publishing"

        dependsOn(packSonatypeCentralBundle)

        doLast {
            val uriBase = "https://central.sonatype.com/api/v1/publisher/upload"

            val mainBranch = System.getenv("BRANCH_KOOG_IS_RELEASING_FROM") == "main"
            val publishingType = if (mainBranch) {
                println("Publishing from the main branch, so publishing as AUTOMATIC.")
                "AUTOMATIC"
            } else {
                println("Publishing from the non-main branch, so publishing as USER_MANAGED.")
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
    dokka(project(":agents:agents-features:agents-features-debugger"))
    dokka(project(":agents:agents-features:agents-features-event-handler"))
    dokka(project(":agents:agents-features:agents-features-memory"))
    dokka(project(":agents:agents-features:agents-features-opentelemetry"))
    dokka(project(":agents:agents-features:agents-features-snapshot"))
    dokka(project(":agents:agents-features:agents-features-trace"))
    dokka(project(":agents:agents-features:agents-features-tokenizer"))
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
    dokka(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms"))
    dokka(project(":prompt:prompt-executor:prompt-executor-llms-all"))
    dokka(project(":prompt:prompt-executor:prompt-executor-model"))
    dokka(project(":prompt:prompt-llm"))
    dokka(project(":prompt:prompt-markdown"))
    dokka(project(":prompt:prompt-model"))
    dokka(project(":prompt:prompt-structure"))
    dokka(project(":prompt:prompt-tokenizer"))
    dokka(project(":prompt:prompt-xml"))
    dokka(project(":koog-spring-boot-starter"))
    dokka(project(":koog-ktor"))
    dokka(project(":rag:rag-base"))
    dokka(project(":rag:vector-storage"))
}

kover {
    val excludedProjects = setOf(
        ":integration-tests",
        ":examples",
        ":buildSrc"
    )
    merge {
        subprojects {
            it.path !in excludedProjects
        }
    }
    reports {
        total {
            binary {
                file = file(".qodana/code-coverage/kover.ic")
            }
        }
    }
}
