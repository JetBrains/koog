# Module stability

Koog modules are published at one of two stability levels: **stable** and **beta**.
The stability level determines the version suffix and the API stability guarantees.

## Stable modules (`1.0.0`)

Stable modules follow [Semantic Versioning](https://semver.org/).
Their public APIs are **guaranteed not to break** between minor releases.
You can safely depend on stable modules in production code.

Stable modules include the core agent infrastructure, tools, built-in features, and the standard LLM provider clients.

## Beta modules (`1.0.0-beta`)

Beta modules are **experimental features** under active development.
Their public APIs **may change** between releases without prior notice.
Use them to try out new capabilities, but be prepared for API updates when upgrading.

Beta modules use a `-beta` suffix in their version number (for example `1.0.0-beta`).

The following features are currently in beta:

| Feature                                                                                    | Module(s) |
|--------------------------------------------------------------------------------------------|-----------|
| [Planner agents](agents/planner-agents/index.md)                                           | `agents-planner` |
| [Model Context Protocol](model-context-protocol.md)                                        | `agents-mcp`, `agents-mcp-server` |
| [A2A Protocol](a2a-protocol-overview.md)                                                   | `a2a-core`, `a2a-client`, `a2a-server`, and transport modules |
| [Agent Client Protocol](agent-client-protocol.md)                                          | `agents-features-acp` |
| [Long-term memory](features/long-term-memory.md)                                           | `agents-features-longterm-memory`, `agents-features-longterm-memory-aws` |
| [RAG](retrieval-augmented-generation.md)                                                   | `rag-vector` |
| [Embeddings](embeddings.md)                                                                | `rag-vector` |
| [Spring Boot integration](spring-boot.md)                                                  | `koog-spring-boot-starter` |
| [Spring AI integration](spring-ai-integration.md)                                          | `koog-spring-ai-*` |
| [Ktor integration](ktor-plugin.md)                                                         | `koog-ktor` |
| [LLM response caching (Redis backend)](prompts/llm-response-caching.md)                    | `prompt-cache-redis` |
| [Subset of LLM clients (Google, DeepSeek, MistralAI, DashScope, LiteRT)](llm-providers.md) | `prompt-executor-*-client` |

## Adding a beta dependency

Beta modules are published as separate artifacts with the `-beta` version.
Add them explicitly to your project alongside any stable modules you use:

=== "Gradle (Kotlin)"

    ``` kotlin title="build.gradle.kts"
    dependencies {
        // Stable
        implementation("ai.koog:koog-agents:1.0.0")

        // Beta
        implementation("ai.koog:agents-mcp:1.0.0-beta")
    }
    ```

=== "Gradle (Groovy)"

    ``` groovy title="build.gradle"
    dependencies {
        // Stable
        implementation 'ai.koog:koog-agents:1.0.0'

        // Beta
        implementation 'ai.koog:agents-mcp:1.0.0-beta'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependencies>
        <!-- Stable -->
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Beta -->
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>agents-mcp-jvm</artifactId>
            <version>1.0.0-beta</version>
        </dependency>
    </dependencies>
    ```
