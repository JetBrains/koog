import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

val excluded = setOf(
    ":agents:agents-test",
    ":agents:agents-ext",
    ":agents:agents-features:agents-features-sql", // Optional SQL persistence provider
    ":a2a:a2a-test", // Testing utilities for A2A protocol compliance
    ":agents:agents-mcp-server",
    ":integration-tests",
    ":test-utils",
    ":koog-spring-boot-starter",
    ":koog-ktor",
    ":docs",

    ":a2a:a2a-core",
    ":a2a:a2a-server",
    ":a2a:a2a-client",
    ":a2a:a2a-transport:a2a-transport-core-jsonrpc",
    ":a2a:a2a-transport:a2a-transport-server-jsonrpc-http",
    ":a2a:a2a-transport:a2a-transport-client-jsonrpc-http",
    ":a2a:a2a-transport:a2a-transport-core-rest",
    ":a2a:a2a-transport:a2a-transport-server-rest",
    ":a2a:a2a-transport:a2a-transport-client-rest",

    project.path, // the current project should not depend on itself
)

val included = setOf(
    ":agents:agents-core",
    ":agents:agents-features:agents-features-debugger",
    ":agents:agents-features:agents-features-event-handler",
    ":agents:agents-features:agents-features-memory",
    ":agents:agents-features:agents-features-opentelemetry",
    ":agents:agents-features:agents-features-trace",
    ":agents:agents-features:agents-features-tokenizer",
    ":agents:agents-features:agents-features-snapshot",
    ":agents:agents-mcp",
    ":agents:agents-tools",
    ":agents:agents-utils",
    ":embeddings:embeddings-base",
    ":embeddings:embeddings-llm",
    ":prompt:prompt-cache:prompt-cache-files",
    ":prompt:prompt-cache:prompt-cache-model",
    ":prompt:prompt-cache:prompt-cache-redis",
    ":prompt:prompt-executor:prompt-executor-cached",
    ":prompt:prompt-executor:prompt-executor-clients",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-deepseek-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client-base",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client",
    ":prompt:prompt-executor:prompt-executor-llms",
    ":prompt:prompt-executor:prompt-executor-llms-all",
    ":prompt:prompt-executor:prompt-executor-model",
    ":prompt:prompt-llm",
    ":prompt:prompt-markdown",
    ":prompt:prompt-model",
    ":prompt:prompt-structure",
    ":prompt:prompt-tokenizer",
    ":prompt:prompt-xml",
    ":rag:rag-base",
    ":rag:vector-storage",
    ":utils",
)

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                val projects = rootProject.subprojects
                    .filterNot { it.path in excluded }
                    .filter { it.buildFile.exists() }

                val projectsPaths = projects.mapTo(sortedSetOf()) { it.path }

                val obsoleteIncluded = included - projectsPaths
                require(obsoleteIncluded.isEmpty()) {
                    "There are obsolete modules that are used for '${project.name}' main jar dependencies" +
                        "but no longer exist," +
                        "please remove them from 'included' in ${project.name}/build.gradle.kts:\n" +
                        obsoleteIncluded.joinToString(",\n") { "\"$it\"" }
                }

                val notIncluded = projectsPaths - included
                require(notIncluded.isEmpty()) {
                    "There are modules that are not listed for '${project.name}' main jar dependencies, " +
                        "please add them to 'included' or 'excluded' in ${project.name}/build.gradle.kts:\n" +
                        notIncluded.joinToString(",\n") { "\"$it\"" }
                }

                projects.forEach {
                    val text = it.buildFile.readText()

                    require("import ai.koog.gradle.publish.maven.Publishing.publishToMaven" in text) {
                        "Module ${it.path} is used as a dependency for '${project.name}' main jar. Hence, it should be published. If not, please mark it as excluded in ${project.name}/build.gradle.kts"
                    }

                    require("publishToMaven()" in text) {
                        "Module ${it.path} is used as a dependency for '${project.name}' main jar. Hence, it should be published. If not, please mark it as excluded in ${project.name}/build.gradle.kts"
                    }
                }

                projects.forEach {
                    api(project(it.path))
                }
            }
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}

publishToMaven()
