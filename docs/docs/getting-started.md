# Getting started

This guide will help you install Koog and create your first AI agent.

## Prerequisites

Before you start, make sure you have the following:

- A working Kotlin/JVM project with Gradle or Maven.
- Java 17+ installed.
- A valid API key for your preferred [LLM provider](llm-providers.md) (not required for Ollama, which runs locally).

## Install Koog

To use Koog, you need to include all necessary dependencies in your build configuration.

!!! note
    Replace `LATEST_VERSION` with the latest version of Koog published on Maven Central.

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
        ```groovy
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

!!! note
    When integrating Koog with [Ktor servers](ktor-plugin.md), [Spring applications](spring-boot.md), or [MCP tools](model-context-protocol.md),
    you need to include the additional dependencies in your build configuration.
    For the exact dependencies, refer to the relevant pages in the Koog documentation.


## Set an API key

!!! tip
    Use environment variables or a secure configuration management system to store your API keys.
    Avoid hardcoding API keys directly in your source code.

=== "OpenAI"

    Get your [API key](https://platform.openai.com/api-keys) and assign it as an environment variable.
    
    === "Linux/macOS"

        ```bash
        export OPENAI_API_KEY=your-api-key
        ```

    === "Windows"

        ```shell
        setx OPENAI_API_KEY "your-api-key"
        ```
    
    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.   

=== "Anthropic"

    Get your [API key](https://console.anthropic.com/settings/keys) and assign it as an environment variable.

    === "Linux/macOS"

        ```bash
        export ANTHROPIC_API_KEY=your-api-key
        ```

    === "Windows"

        ```shell
        setx ANTHROPIC_API_KEY "your-api-key"
        ```
    
    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.

=== "Google"

    Get your [API key](https://aistudio.google.com/app/api-keys) and assign it as an environment variable.

    === "Linux/macOS"

        ```bash
        export GOOGLE_API_KEY=your-api-key
        ```

    === "Windows"

        ```shell
        setx GOOGLE_API_KEY "your-api-key"
        ```

    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.   

=== "DeepSeek"
    
    Get your [API key](https://platform.deepseek.com/api_keys) and assign it as an environment variable.

    === "Linux/macOS"

        ```bash
        export DEEPSEEK_API_KEY=your-api-key
        ```

    === "Windows"

        ```shell
        setx DEEPSEEK_API_KEY "your-api-key"
        ```

    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.   

=== "OpenRouter"

    Get your [API key](https://openrouter.ai/keys) and assign it as an environment variable.

    === "Linux/macOS"

        ```bash
        export OPENROUTER_API_KEY=your-api-key
        ```

    === "Windows"

        ```shell
        setx OPENROUTER_API_KEY "your-api-key"
        ```

    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.   

=== "Bedrock"

    Get valid [AWS credentials](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_bedrock.html) (an access key and a secret key) and assign them as environment variables.

    === "Linux/macOS"

        ```bash
        export AWS_BEDROCK_ACCESS_KEY=your-access-key
        export AWS_BEDROCK_SECRET_ACCESS_KEY=your-secret-access-key
        ``` 

    === "Windows"

        ```shell
        setx AWS_BEDROCK_ACCESS_KEY "your-access-key"
        setx AWS_BEDROCK_SECRET_ACCESS_KEY "your-secret-access-key"
        ```

    Restart your terminal to apply the changes. You can now retrieve and use the API key to create an agent.   

=== "Ollama"

    Install Ollama and run a model locally without an API key.

    For more information, see [Ollama documentation](https://docs.ollama.com/quickstart).

## Create and run an agent

=== "OpenAI"

    The example below creates and runs a simple AI agent using the [`GPT-4o`](https://platform.openai.com/docs/models/gpt-4o) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
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
    Hello! I'm here to help you with whatever you need. Here are just a few things I can do:

    - Answer questions.
    - Explain concepts or topics you're curious about.
    - Provide step-by-step instructions for tasks.
    - Offer advice, notes, or ideas.
    - Help with research or summarize complex material.
    - Write or edit text, emails, or other documents.
    - Brainstorm creative projects or solutions.
    - Solve problems or calculations.

    Let me know what you need help with—I’m here for you!
    ```

=== "Anthropic"

    The example below creates and runs a simple AI agent using the [`Claude Opus 4.1`](https://www.anthropic.com/news/claude-opus-4-1) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get an API key from the ANTHROPIC_API_KEY environment variable
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleAnthropicExecutor(apiKey),
            llmModel = AnthropicModels.Opus_4_1
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-02.kt -->

    The example can produce the following output:

    ```
    Hello! I can help you with:

    - **Answering questions** and explaining topics
    - **Writing** - drafting, editing, proofreading
    - **Learning** - homework, math, study help
    - **Problem-solving** and brainstorming
    - **Research** and information finding
    - **General tasks** - instructions, planning, recommendations
    
    What do you need help with today?
    ```

=== "Google"

    The example below creates and runs a simple AI agent using the [`Gemini 2.5 Pro`](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
    import ai.koog.prompt.executor.clients.google.GoogleModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get an API key from the GOOGLE_API_KEY environment variable
        val apiKey = System.getenv("GOOGLE_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleGoogleAIExecutor(apiKey),
            llmModel = GoogleModels.Gemini2_5Pro
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-03.kt -->

    The example can produce the following output:

    ```
    I'm an AI that can help you with tasks involving language and information. You can ask me to:

    *   **Answer questions**
    *   **Write or edit text** (emails, stories, code, etc.)
    *   **Brainstorm ideas**
    *   **Summarize long documents**
    *   **Plan things** (like trips or projects)
    *   **Be a creative partner**

    Just tell me what you need
    ```

=== "DeepSeek"

    The example below creates and runs a simple AI agent using the `deepseek-chat` model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
    import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
    import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get an API key from the DEEPSEEK_API_KEY environment variable
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an LLM client
        val deepSeekClient = DeepSeekLLMClient(apiKey)
    
        // Create an agent
        val agent = AIAgent(
            // Create a prompt executor using the LLM client
            promptExecutor = SingleLLMPromptExecutor(deepSeekClient),
            // Provide a model
            llmModel = DeepSeekModels.DeepSeekChat
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-04.kt -->

    The example can produce the following output:

    ```
    Hello! I'm here to assist you with a wide range of tasks, including answering questions, providing information, helping with problem-solving, offering creative ideas, and even just chatting. Whether you need help with research, writing, learning something new, or simply want to discuss a topic, feel free to ask—I’m happy to help! 😊
    ```

=== "OpenRouter"

    The example below creates and runs a simple AI agent using the [`GPT-4o`](https://openrouter.ai/openai/gpt-4o) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
    import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get an API key from the OPENROUTER_API_KEY environment variable
        val apiKey = System.getenv("OPENROUTER_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            llmModel = OpenRouterModels.GPT4o
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-05.kt -->

    The example can produce the following output:

    ```
    I can answer questions, help with writing, solve problems, organize tasks, and more—just let me know what you need!
    ```

=== "Bedrock"

    The example below creates and runs a simple AI agent using the [`Claude Sonnet 4.5`](https://www.anthropic.com/news/claude-sonnet-4-5) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
    import ai.koog.prompt.executor.clients.bedrock.BedrockModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get access keys from the AWS_BEDROCK_ACCESS_KEY and AWS_BEDROCK_SECRET_ACCESS_KEY environment variables
        val awsAccessKeyId = System.getenv("AWS_BEDROCK_ACCESS_KEY")
            ?: error("The access key is not set.")
    
        val awsSecretAccessKey = System.getenv("AWS_BEDROCK_SECRET_ACCESS_KEY")
            ?: error("The secret access key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleBedrockExecutor(awsAccessKeyId, awsSecretAccessKey),
            llmModel = BedrockModels.AnthropicClaude4_5Sonnet
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-06.kt -->

    The example can produce the following output:

    ```
    Hello! I'm a helpful assistant and I can assist you in many ways, including:

    - **Answering questions** on a wide range of topics (science, history, technology, etc.)
    - **Writing help** - drafting emails, essays, creative content, or editing text
    - **Problem-solving** - working through math problems, logic puzzles, or troubleshooting issues
    - **Learning support** - explaining concepts, providing study notes, or tutoring
    - **Planning & organizing** - helping with projects, schedules, or breaking down tasks
    - **Coding assistance** - explaining programming concepts or helping debug code
    - **Creative brainstorming** - generating ideas for projects, stories, or solutions
    - **General conversation** - discussing topics or just chatting
    
     What would you like help with today?
    ```

=== "Ollama"

    The example below creates and runs a simple AI agent using the [`llama3.2`](https://ollama.com/library/llama3.2) model.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.llm.OllamaModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleOllamaAIExecutor(),
            llmModel = OllamaModels.Meta.LLAMA_3_2
        )

        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-07.kt -->

    The example can produce the following output:

    ```
    I can assist with various tasks such as answering questions, providing information, and even helping with language-related tasks like proofreading or writing suggestions. What's on your mind today?
    ```

## What's next

- Explore [key features](key-features.md) of Koog.
- Learn more about available [agent types](basic-agents.md).

