# Koog

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Maven Central](https://img.shields.io/maven-central/v/ai.koog/koog-agents)](https://search.maven.org/artifact/ai.koog/koog-agents)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://github.com/JetBrains#jetbrains-on-github)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![CI status](https://img.shields.io/github/checks-status/JetBrains/koog/main)](https://github.com/JetBrains/koog/actions?query=branch%3Amain)
[![GitHub license](https://img.shields.io/github/license/JetBrains/koog)](LICENSE.txt)
[![docs](https://img.shields.io/badge/documentation-blue)](https://docs.koog.ai)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/koog-agentic-framework/)

## Overview

Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin. It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

### Key features

Key features of Koog include:

- **Pure Kotlin implementation**: Build AI agents entirely in natural and idiomatic Kotlin.
- **MCP integration**: Connect to Model Context Protocol for enhanced model management.
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
- **Multiplatform**: Run agents on JVM, JS, WasmJS targets with Kotlin Multiplatform.

### Available LLM providers and platforms

The LLM providers and platforms whose LLMs you can use to power your agent capabilities:

- Google
- OpenAI
- Anthropic
- OpenRouter
- Ollama

### Quickstart example

To help you get started with AI agents, here is a quick example:

```kotlin
fun main() = runBlocking {
    // Before you run the example, assign a corresponding API key as an environment variable.
   val apiKey = System.getenv("OPENAI_API_KEY") // or Anthropic, Google, OpenRouter, etc.

   val agent = AIAgent(
      executor = simpleOpenAIExecutor(apiKey), // or Anthropic, Google, OpenRouter, etc.
      systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
      llmModel = OpenAIModels.Chat.GPT4o
   )

   val result = agent.run("Hello! How can you help me?")
   println(result)
}
```

## Using in your projects

### Supported targets

Currently, the framework supports the JVM, JS and WasmJS targets.

On JVM, JDK 17 or higher is required to use the framework.

Please check the [libs.versions.toml](gradle/libs.versions.toml) to know more about the Koog dependencies.

### Gradle (Kotlin DSL)

1. Add dependencies to the `build.gradle.kts` file:

    ```
    dependencies {
        implementation("ai.koog:koog-agents:0.3.0")
    }
    ```
2. Make sure that you have `mavenCentral()` in the list of repositories.
### Gradle (Groovy)

1. Add dependencies to the `build.gradle` file:

    ```
    dependencies {
        implementation 'ai.koog:koog-agents:0.3.0'
    }
    ```
2. Make sure that you have `mavenCentral()` in the list of repositories.
### Maven

1. Add dependencies to the `pom.xml` file:

    ```
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents</artifactId>
        <version>0.3.0</version>
    </dependency>
    ```
2. Make sure that you have `mavenCentral` in the list of repositories.
## Contributing
Read the [Contributing Guidelines](CONTRIBUTING.md).

## Code of Conduct
This project and the corresponding community are governed by the [JetBrains Open Source and Community Code of Conduct](https://github.com/jetbrains#code-of-conduct). Please make sure you read it.

## License
Koog is licensed under the [Apache 2.0 License](LICENSE.txt).

## Support

Please feel free to ask any questions in our official Slack channel ([link](https://kotlinlang.slack.com/messages/koog-agentic-framework/))

