# Overview

Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin.
It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

The framework supports the following types of agents:

* Single-run agents with minimal configuration that process a single input and provide a response.
  An agent of this type operates within a single cycle of tool-calling to complete its task and provide a response.
* Complex workflow agents with advanced capabilities that support custom strategies and configurations.

## Key features

Key features of Koog include:

- **Pure Kotlin implementation**: Build AI agents entirely in natural and idiomatic Kotlin.
- **MCP integration**: Connect to Model Control Protocol for enhanced model management.
- **Embedding capabilities**: Use vector embeddings for semantic search and knowledge retrieval.
- **Custom tool creation**: Extend your agents with tools that access external systems and APIs.
- **Ready-to-use components**: Speed up development with pre-built solutions for common AI engineering challenges.
- **Intelligent history compression**: Optimize token usage while maintaining conversation context using various pre-built strategies.
- **Powerful Streaming API**: Process responses in real-time with streaming support and parallel tool calls.
- **Persistent agent memory**: Enable knowledge retention across sessions and even different agents.
- **Comprehensive tracing**: Debug and monitor agent execution with detailed and configurable tracing.
- **Flexible graph workflows**: Design complex agent behaviors using intuitive graph-based workflows.
- **Modular feature system**: Customize agent capabilities through a composable architecture.
- **Scalable architecture**: Handle workloads from simple chatbots to enterprise applications.
- **Multiplatform**: Run agents on JVM, JS, WasmJS, and iOS targets with Kotlin Multiplatform.

## Available LLM providers and platforms

The LLM providers and platforms whose LLMs you can use to power your agent capabilities:

- Google
- OpenAI
- Anthropic
- DeepSeek
- OpenRouter
- Ollama
- Bedrock

For detailed guidance on using these providers with dedicated LLM clients, refer to [Runnning prompts with LLM clients](prompt-api.md#running-prompts-with-llm-clients).


## Installation

To use Koog, you need to include all necessary dependencies in your build configuration.

### Gradle

#### Gradle (Kotlin DSL)

1. Add dependencies to the `build.gradle.kts` file:

    ```
    dependencies {
        implementation("ai.koog:koog-agents:LATEST_VERSION")
    }
    ```

2. Make sure that you have `mavenCentral()` in the list of repositories.

#### Gradle (Groovy)

1. Add dependencies to the `build.gradle` file:

    ```
    dependencies {
        implementation 'ai.koog:koog-agents:LATEST_VERSION'
    }
    ```

2. Make sure that you have `mavenCentral()` in the list of repositories.

### Maven

1. Add dependencies to the `pom.xml` file:

    ```
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-jvm</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
    ```

2. Make sure that you have `mavenCentral` in the list of repositories.


## Quickstart example

To help you get started with AI agents, here is a quick example of a single-run agent:

!!! note
    Before you run the example, assign a corresponding API key as an environment variable. For details, see [Getting started](single-run-agents.md).

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() {
    runBlocking {
        val apiKey = System.getenv("OPENAI_API_KEY") // or Anthropic, Google, OpenRouter, etc.

        val agent = AIAgent(
            executor = simpleOpenAIExecutor(apiKey), // or Anthropic, Google, OpenRouter, etc.
            systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
            llmModel = OpenAIModels.Chat.GPT4o
        )

        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
}
```
<!--- KNIT example-index-01.kt -->
For more details, see [Getting started](single-run-agents.md).
