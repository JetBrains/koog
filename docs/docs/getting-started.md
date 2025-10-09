# Getting started

This guide will help you install Koog and create your first AI agent.

## Prerequisites

Before you start, make sure that you have the following:

- A working Kotlin/JVM project with Gradle or Maven.
- Java 17+ installed.
- A valid [OpenAI](https://platform.openai.com/api-keys) API key.

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

## Install Koog

To use Koog, you need to include all necessary dependencies in your build configuration.

!!! tip
    Check the latest version in the Maven Central Repository.

=== "Gradle (Kotlin DSL)"

    1. Add the dependency to the `build.gradle.kts` file.
    
        ```kotlin
        dependencies {
            implementation("ai.koog:koog-agents:LATEST_VERSION")
        }
        ```
    2. Make sure that you have `mavenCentral()` in the list of repositories.
    
        ```kotlin
        repositories {
            mavenCentral()
        }
        ```

=== "Gradle (Groovy)"

    1. Add the dependency to the `build.gradle` file.
    
        ```groovy
        dependencies {
            implementation 'ai.koog:koog-agents:LATEST_VERSION'
        }
        ```
    2. Make sure that you have `mavenCentral()` in the list of repositories.
        ```kotlin
                repositories {
            mavenCentral()
        }
        ```

=== "Maven"

    1. Add the dependency to the `pom.xml` file.
    
        ```xml
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>LATEST_VERSION</version>
        </dependency>
        ```
    2. Make sure that you have `mavenCentral()` in the list of repositories.

        ```xml
         <repositories>
            <repository>
                <id>mavenCentral</id>
                <url>https://repo1.maven.org/maven2/</url>
            </repository>
        </repositories>
        ```

## Create and run an agent

The example below creates and runs a simple AI agent using the [`GPT-4o`](https://platform.openai.com/docs/models/gpt-4o) model.

<!--- INCLUDE
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
    // Get an API key from the OPENAI_API_KEY environment variable
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("The API key is not set.")
    
    // Create an agent
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o
    )

    // Run the agent
    val result = agent.run("Hello! How can you help me?")
    println(result)
}
```
<!--- KNIT example-getting-started-01.kt -->

The example can produce the following output:

```
I can assist with various tasks such as answering questions, providing information, and even helping with language-related tasks like proofreading or writing suggestions. What's on your mind today?
```

## What's next

- Check supported [LLM providers](llm-providers.md) to learn how to use other models.
- Explore [key features](key-features.md) of Koog.
- Learn more about available [agent types](single-run-agents.md).

