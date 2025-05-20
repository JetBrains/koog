# Koog

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://github.com/JetBrains#jetbrains-on-github)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![CI status](https://img.shields.io/github/checks-status/JetBrains/koog/main)](https://github.com/JetBrains/koog/actions?query=branch%3Amain)
[![GitHub license](https://img.shields.io/github/license/JetBrains/koog)](LICENSE)
[![docs](https://img.shields.io/badge/documentation-blue)](https://docs.koog.ai)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/koog-agentic-framework/)
<!-- TODO: maven central link -->

## Overview

Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin. It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

### Key features

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
- **Multiplatform**: Run agents on both JVM and JS targets with Kotlin Multiplatform.

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
fun main() {
    // Before you run the example, assign a corresponding API key as an environment variable.
    val apiKey = System.getenv("OPENAI_API_KEY") // or Anthropic, Google, OpenRouter, etc.

    val agent = simpleChatAgent(
        executor = simpleOpenAIExecutor(apiKey), // or Anthropic, Google, OpenRouter, etc.
        systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
        llmModel = OpenAIModels.Chat.GPT4o
    )

    val result = agent.runAndGetResult("Hello, how can you help me?")
    println(result)
}
```

## Using in your projects

### Supported targets

Currently, the framework supports the JVM and JS targets.

On JVM, JDK 17 or higher is required to use the framework.

### Gradle (Kotlin DSL)

1. Add dependencies to the `build.gradle.kts` file:

    ```
    dependencies {
        implementation("ai.koog:koog-agents:VERSION")
    }
    ```

2. Add the address of the Maven repository where the package takes from:

    ```
    repositories {
        maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/")
    }
    ```

### Gradle (Groovy)

1. Add dependencies to the `build.gradle` file:

    ```
    dependencies {
        implementation 'ai.koog:koog-agents:VERSION'
    }
    ```

2. Add the address of the Maven repository where the package takes from:

    ```
    repositories {
        maven {
            url 'https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/'
        }
    }
    ```

### Maven

1. Add dependencies to the `pom.xml` file:

    ```
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents</artifactId>
        <version>VERSION</version>
    </dependency>
    ```

2. Add the address of the Maven repository where the package takes from:

    ```
    <repository>
        <id>mavenCustom</id>
        <url>https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/</url>
    </repository>
    ```

## Contributing
Read the [Contributing Guidelines](CONTRIBUTING.md).

## Code of Conduct
This project and the corresponding community are governed by the [JetBrains Open Source and Community Code of Conduct](https://github.com/jetbrains#code-of-conduct). Please make sure you read it.

## License
Koog is licensed under the [Apache 2.0 License](LICENSE).

## Support

Please feel free to ask any questions in our official Slack channel ([link](https://kotlinlang.slack.com/archives/C08SLB97W23))
