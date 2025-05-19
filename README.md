# Koog

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://github.com/JetBrains#jetbrains-on-github)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![CI status](https://img.shields.io/github/checks-status/JetBrains/koog/main)](https://github.com/JetBrains/koog/actions?query=branch%3Amain)
[![GitHub license](https://img.shields.io/github/license/JetBrains/koog)](LICENSE)
[![docs](https://img.shields.io/badge/documentation-blue)](https://docs.koog.ai)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/koog-agentic-framework/)
<!-- TODO: maven central link -->


Koog is a Kotlin-based framework for creating and running AI agents locally without requiring external services. 
It provides a pure Kotlin implementation for building intelligent agents that can interact with tools, 
handle complex workflows, and communicate with users.

## Overview

Koog is a Kotlin-based framework designed to create and run AI agents locally without external
services. It provides a pure Kotlin implementation for building intelligent agents that can interact with
tools, handle complex workflows, and communicate with users.

### Key features

Key features of Koog include:

- A pure Kotlin implementation that lets you create and run AI agents entirely in Kotlin without relying on external service dependencies.
- A modular and composable feature system that lets you extend AI agent capabilities.
- The ability to create custom tools that give agents access to external systems and resources.
- Support for both conversational agents and single-query (one-shot) agents.
- The ability to intercept and modify agent behavior at different stages of operation.
- Optional persistent memory support for agents through a separate module.

### Available LLM providers and platforms

We support the following LLM providers and platforms whose LLMs you can use to power your agent capabilities:

- Google
- OpenAI
- Anthropic
- OpenRouter
- Ollama
- LightLLM

### Quickstart example

The `simpleChatAgent` (or `simpleSingleRunAgent`) rovides the easiest way to get started with AI agents:

```kotlin
fun main() = runBlocking {
    // Before you run the example, assign a corresponding API key as the `YOUR_API_TOKEN` environment variable. For details, see [Getting started](simple-api-getting-started.md).
    val apiKey = System.getenv("OPENAI_API_KEY") // or Anthropic, Google, OpenRouter, etc.

    val agent = simpleChatAgent(
        executor = simpleOpenAIExecutor(apiKey), // or Anthropic, Google, OpenRouter, etc.
        systemPrompt = "You are a helpful assistant. Answer user questions concisely."
    )
    
    val result = agent.runAndGetResult("Hello, how can you help me?")
    println(result)
}
```


## Using in your projects

### Supported targets

Currently, the framework supports two targets: JVM and JS.

On JVM, JDK 17 or higher is required to use the framework.
### How to use with Gradle (Kotlin DSL)
  To include all Koog dependencies together (useful for a quickstart), please add the following to your buildscript:
```kotlin
implementation("ai.koog:koog-agents:0.1.0-alpha.5+0.4.49")
```

### How to use with Gradle (Groovy)
  To include all Koog dependencies together (useful for a quickstart), please add the following to your buildscript:
```groovy
implementation 'ai.koog:koog-agents:0.1.0-alpha.5+0.4.49'
```

### How to use with Maven
  To include all Koog dependencies together (useful for a quickstart), please add the following to your pom.xml:
```xml
<dependency>
    <groupId>ai.koog</groupId>
    <artifactId>koog-agents</artifactId>
    <version>0.1.0-alpha.5+0.4.49</version>
</dependency>
```

## Contributing
Read the [Contributing Guidelines](CONTRIBUTING.md).

## Code of Conduct
This project and the corresponding community are governed by the [JetBrains Open Source and Community Code of Conduct](https://github.com/jetbrains#code-of-conduct). Please make sure you read it.

## License
Koog is licensed under the [Apache 2.0 License](LICENSE).

## Support

Please feel free to ask any questions in our official Slack channel ([link](https://kotlinlang.slack.com/archives/C08SLB97W23))
