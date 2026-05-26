import ai.koog.gradle.publish.maven.Publishing.publishToMaven
import ai.koog.gradle.xcframework.XCFrameworkConfig.configureFrameworkExportsIfRequested


plugins {
    id("ai.kotlin.multiplatform")
}

val excluded = setOf(
    ":agents:agents-test",
    ":agents:agents-ext",
    ":agents:agents-features:agents-features-sql", // Optional SQL persistence provider
    ":agents:agents-features:agents-features-chat-memory-sql", // Optional SQL chat memory provider
    ":agents:agents-features:agents-features-chat-history-jdbc", // Optional JDBC chat history provider
    ":agents:agents-features:agents-features-chat-history-aws", // Optional AWS chat history provider
    ":agents:agents-features:agents-features-persistence-jdbc", // Optional JDBC persistence provider
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
    ":a2a:a2a-test",
    ":a2a:test-tck:a2a-test-server-tck",

    ":agents:agents-features:agents-features-a2a-core",
    ":agents:agents-features:agents-features-a2a-server",
    ":agents:agents-features:agents-features-a2a-client",

    ":agents:agents-features:agents-features-acp",

    ":http-client:http-client-test",
    ":http-client:http-client-okhttp",
    ":http-client:http-client-java",

    ":serialization:serialization-test",
    ":serialization:serialization-jackson",

    ":koog-spring-ai",
    ":koog-spring-ai:koog-spring-ai-common",
    ":koog-spring-ai:koog-spring-ai-starter-model-chat",
    ":koog-spring-ai:koog-spring-ai-starter-model-embedding",
    ":koog-spring-ai:koog-spring-ai-starter-chat-memory",
    ":koog-spring-ai:koog-spring-ai-starter-vector-store",

    ":agents:agents-features:agents-features-longterm-memory-aws", // Optional AWS LongTermMemory provider

    project.path, // the current project should not depend on itself
    ":koog-agents-additions"
)

val betaModules = setOf(
    ":agents:agents-cli",
    ":agents:agents-features:agents-features-longterm-memory",
    ":agents:agents-mcp",
    ":agents:agents-planner",
    ":prompt:prompt-cache:prompt-cache-redis",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-deepseek-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-mistralai-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-dashscope-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-litert-client",
    ":prompt:prompt-executor:prompt-executor-llms-all",
    ":rag:rag-vector"
)

// Non-beta modules ONLY:
val included = setOf(
    ":agents:agents-core",
    ":agents:agents-features:agents-features-event-handler",
    ":agents:agents-features:agents-features-memory",
    ":agents:agents-features:agents-features-opentelemetry",
    ":agents:agents-features:agents-features-trace",
    ":agents:agents-features:agents-features-tokenizer",
    ":agents:agents-features:agents-features-snapshot",
    ":agents:agents-mcp-metadata",
    ":agents:agents-tools",
    ":agents:agents-utils",
    ":embeddings:embeddings-base",
    ":embeddings:embeddings-llm",
    ":prompt:prompt-cache:prompt-cache-files",
    ":prompt:prompt-cache:prompt-cache-model",
    ":prompt:prompt-executor:prompt-executor-cached",
    ":prompt:prompt-executor:prompt-executor-clients",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client-base",
    ":prompt:prompt-executor:prompt-executor-model",
    ":prompt:prompt-llm",
    ":prompt:prompt-markdown",
    ":prompt:prompt-model",
    ":prompt:prompt-processor",
    ":prompt:prompt-structure",
    ":prompt:prompt-tokenizer",
    ":prompt:prompt-xml",
    ":http-client:http-client-core",
    ":http-client:http-client-ktor",
    ":serialization:serialization-core",
    ":rag:rag-base",
    ":utils",
)

// Modules that do not publish a wasmJs artifact. They are filtered out of the
// commonMain api auto-loop and declared on a local `nonWasmJsMain` intermediate
// source set that all non-wasmJs leaf targets inherit from, so that the
// koog-agents-wasm-js publication does not reference missing coordinates.
val wasmJsExcluded = setOf(
    ":agents:agents-features:agents-features-opentelemetry",
)

kotlin {
    val projects = rootProject.subprojects
        .filterNot { it.path in excluded }
        .filterNot { it.path in betaModules }
        .filter { it.buildFile.exists() }
    val projectsPaths = projects.mapTo(sortedSetOf()) { it.path }

    configureFrameworkExportsIfRequested(project, projectsPaths)

    sourceSets {
        commonMain {
            dependencies {

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

                projects.filterNot { it.path in wasmJsExcluded }.forEach {
                    api(project(it.path))
                }
            }
        }

        // Source set holding dependencies that are valid on every target except wasmJs.
        // 'jvmCommonMain', 'jsMain', and 'appleMain' pick these up.
        // 'wasmJsMain' keeps its existing parent (nonJvmCommonMain, commonMain) and never sees them.
        val nonWasmJsMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                wasmJsExcluded.forEach { api(project(it)) }
            }
        }

        jvmCommonMain {
            dependsOn(nonWasmJsMain)
        }

        jvmMain.dependencies {
            api(libs.ktor.client.apache5)
        }

        androidMain.dependencies {
            api(libs.ktor.client.okhttp)
        }

        appleMain {
            dependsOn(nonWasmJsMain)
            dependencies {
                api(libs.ktor.client.darwin)
            }
        }

        jsMain {
            dependsOn(nonWasmJsMain)
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        wasmJsMain.dependencies {
            api(libs.ktor.client.js)
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}

publishToMaven()
