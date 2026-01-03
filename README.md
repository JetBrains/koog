# Koog

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Maven Central](https://img.shields.io/maven-central/v/ai.koog/koog-agents)](https://search.maven.org/artifact/ai.koog/koog-agents)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://github.com/JetBrains#jetbrains-on-github)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![CI status](https://img.shields.io/github/checks-status/JetBrains/koog/main)](https://github.com/JetBrains/koog/actions?query=branch%3Amain)
[![GitHub license](https://img.shields.io/github/license/JetBrains/koog)](LICENSE.txt)

Build status:

[![Checks](https://github.com/JetBrains/koog/actions/workflows/checks.yml/badge.svg?branch=develop)](https://github.com/JetBrains/koog/actions/workflows/checks.yml?query=branch%3Adevelop)
[![Heavy Tests](https://github.com/JetBrains/koog/actions/workflows/heavy-tests.yml/badge.svg?branch=develop)](https://github.com/JetBrains/koog/actions/workflows/heavy-tests.yml?query=branch%3Adevelop)
[![Ollama Tests](https://github.com/JetBrains/koog/actions/workflows/ollama-tests.yml/badge.svg?branch=develop)](https://github.com/JetBrains/koog/actions/workflows/ollama-tests.yml?query=branch%3Adevelop)

Useful links:

* [Documentation](https://docs.koog.ai/)
* [API reference](https://api.koog.ai/)
* [Slack channel](https://docs.koog.ai/koog-slack-channel/)
* [Issue tracker](https://youtrack.jetbrains.com/issues/KG)

## Overview

Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin. It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

### Key features

Key features of Koog include:

- **Multiplatform development**: Deploy agents across JVM, JS, WasmJS, Android, and iOS targets using Kotlin Multiplatform.
- **Reliability and fault-tolerance**: Handle failures with built-in retries and restore the agent state at specific points during execution with the agent persistence feature.
- **Intelligent history compression**: Optimize token usage while maintaining context in long-running conversations using advanced built-in history compression techniques.
- **Enterprise-ready integrations**: Utilize integration with popular JVM frameworks such as Spring Boot and Ktor to embed Koog into your applications.
- **Observability with OpenTelemetry exporters**: Monitor and debug applications with built-in support for popular observability providers (W&B Weave, Langfuse).
- **LLM switching and seamless history adaptation**: Switch to a different LLM at any point without losing the existing conversation history, or reroute between multiple LLM providers.
- **Integration with JVM and Kotlin applications**: Build AI agents with an idiomatic, type-safe Kotlin DSL designed specifically for JVM and Kotlin developers.
- **Model Context Protocol integration**: Use Model Context Protocol (MCP) tools in AI agents.
- **Agent Client Protocol integration**: Build ACP-compliant agents that can communicate with standardized client applications using the Agent Client Protocol (ACP).
- **Knowledge retrieval and memory**: Retain and retrieve knowledge across conversations using vector embeddings, ranked document storage, and shared agent memory.
- **Powerful Streaming API**: Process responses in real-time with streaming support and parallel tool calls.
- **Modular feature system**: Customize agent capabilities through a composable architecture.
- **Flexible graph workflows**: Design complex agent behaviors using intuitive graph-based workflows.
- **Custom tool creation**: Enhance your agents with tools that access external systems and APIs.
- **Comprehensive tracing**: Debug and monitor agent execution with detailed, configurable tracing.

### Available LLM providers and platforms

The LLM providers and platforms whose LLMs you can use to power your agent capabilities:

- Google
- OpenAI
- Anthropic
- DeepSeek
- OpenRouter
- Ollama
- Bedrock

### Quickstart example

To help you get started with AI agents, here is a quick example:

```kotlin
fun main() = runBlocking {
    // Before you run the example, assign a corresponding API key as an environment variable.
   val apiKey = System.getenv("OPENAI_API_KEY") // or Anthropic, Google, OpenRouter, etc.

   val agent = AIAgent(
      promptExecutor = simpleOpenAIExecutor(apiKey), // or Anthropic, Google, OpenRouter, etc.
      systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
      llmModel = OpenAIModels.Chat.GPT4o
   )

   val result = agent.run("Hello! How can you help me?")
   println(result)
}
```

## Using in your projects

### Supported targets

Currently, the framework supports the JVM, JS, WasmJS and iOS targets.

### Requirements

- JDK 17 or higher is required to use the framework on JVM.
- kotlinx-coroutines 1.10.2 and kotlinx-serialization 1.8.1 versions should be set explicitly in existing projects. Please check the [libs.versions.toml](gradle/libs.versions.toml) to know more about the Koog dependencies.

### Gradle (Kotlin DSL)

1. Add dependencies to the `build.gradle.kts` file:

    ```
    dependencies {
        implementation("ai.koog:koog-agents:0.6.0")
    }
    ```
2. Make sure that you have `mavenCentral()` in the list of repositories.
### Gradle (Groovy)

1. Add dependencies to the `build.gradle` file:

    ```
    dependencies {
        implementation 'ai.koog:koog-agents:0.6.0'
    }
    ```
2. Make sure that you have `mavenCentral()` in the list of repositories.
### Maven

1. Add dependencies to the `pom.xml` file:

    ```
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-jvm</artifactId>
        <version>0.6.0</version>
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

Please feel free to ask any questions in our [official Slack
channel](https://docs.koog.ai/koog-slack-channel/) and to
use [Koog official YouTrack project](https://youtrack.jetbrains.com/issues/KG)
for filing feature requests and bug reports.


