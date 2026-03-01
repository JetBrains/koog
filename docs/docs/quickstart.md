# Quickstart

This guide will help you start using Koog in your project.

## Prerequisites

--8<-- "quickstart-snippets.md:prerequisites"

## Install Koog

--8<-- "quickstart-snippets.md:dependencies"

??? tip "Nightly builds"

    Nightly builds from the develop branch are published to the [JetBrains Grazie Maven](https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public) repository.
    
    To use a nightly build, add the following repository to your build configuration:
    `https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public`.
    
    Then update your Koog dependency to the desired nightly version. Nightly versions follow the pattern
    `[next-major-version]-develop-[date]-[time]`.
    
    You can browse the available nightly builds [here](https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/ai/koog/koog-agents/).

## Set up an API key

Koog requires either an API key from a [supported LLM provider](llm-providers.md) or a locally running LLM.

!!! warning
    Avoid hardcoding API keys in the source code.
    Use environment variables to store API keys.

=== "OpenAI"

    Get your [OpenAI API key](https://platform.openai.com/api-keys) and assign it to the `OPENAI_API_KEY` environment variable.
    
    === "Linux/macOS"

        ```shell
        export OPENAI_API_KEY=your-api-key
        ```

    === "Windows"

        ```cmd
        setx OPENAI_API_KEY "your-api-key"
        ```

=== "Anthropic"

    Get your [Anthropic API key](https://console.anthropic.com/settings/keys) and assign it to the `ANTHROPIC_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export ANTHROPIC_API_KEY=your-api-key
        ```

    === "Windows"

        ```cmd
        setx ANTHROPIC_API_KEY "your-api-key"
        ```

=== "Google"

    Get your [Gemini API key](https://aistudio.google.com/app/api-keys) and assign it to the `GOOGLE_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export GOOGLE_API_KEY=your-api-key
        ```

    === "Windows"

        ```cmd
        setx GOOGLE_API_KEY "your-api-key"
        ```  

=== "DeepSeek"

    Get your [DeepSeek API key](https://platform.deepseek.com/api_keys) and assign it to the `DEEPSEEK_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export DEEPSEEK_API_KEY=your-api-key
        ```

    === "Windows"

        ```cmd
        setx DEEPSEEK_API_KEY "your-api-key"
        ``` 

=== "OpenRouter"

    Get your [OpenRouter API key](https://openrouter.ai/keys) and assign it to the `OPENROUTER_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export OPENROUTER_API_KEY=your-api-key
        ```

    === "Windows"

        ```cmd
        setx OPENROUTER_API_KEY "your-api-key"
        ```  

=== "Bedrock"

    [Generate an Amazon Bedrock API key](https://docs.aws.amazon.com/bedrock/latest/userguide/api-keys.html) and assign it to the `BEDROCK_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export BEDROCK_API_KEY=your-api-key
        ``` 

    === "Windows"

        ```cmd
        setx BEDROCK_API_KEY "your-api-key"
        ```  

=== "Mistral"

    Get your [Mistral API key](https://console.mistral.ai/api-keys) and assign it to the `MISTRAL_API_KEY` environment variable.

    === "Linux/macOS"

        ```shell
        export MISTRAL_API_KEY=your-api-key
        ``` 

    === "Windows"

        ```cmd
        setx MISTRAL_API_KEY "your-api-key"
        ``` 

=== "Ollama"

    Run a local LLM in Ollama as described in the [Ollama documentation](https://docs.ollama.com/quickstart).

## Create your first Koog agent

=== "OpenAI"

    The following example creates and runs a simple Koog agent using the [`GPT-4o`](https://platform.openai.com/docs/models/gpt-4o) model via the OpenAI API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the OpenAI API key from the OPENAI_API_KEY environment variable
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

    The following example creates and runs a simple Koog agent using the [`Claude Opus 4.1`](https://www.anthropic.com/news/claude-opus-4-1) model via the Anthropic API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
    import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the Anthropic API key from the ANTHROPIC_API_KEY environment variable
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

    The following example creates and runs a simple Koog agent using the [`Gemini 2.5 Pro`](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro) model via the Gemini API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
    import ai.koog.prompt.executor.clients.google.GoogleModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the Gemini API key from the GOOGLE_API_KEY environment variable
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

    The following example creates and runs a simple Koog agent using the `deepseek-chat` model via the DeepSeek API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
    import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
    import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the DeepSeek API key from the DEEPSEEK_API_KEY environment variable
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an LLM client
        val deepSeekClient = DeepSeekLLMClient(apiKey)
    
        // Create an agent
        val agent = AIAgent(
            // Create a prompt executor using the LLM client
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
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

    The following example creates and runs a simple Koog agent using the [`GPT-4o`](https://openrouter.ai/openai/gpt-4o) model via the OpenRouter API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
    import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the OpenRouter API key from the OPENROUTER_API_KEY environment variable
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

    The following example creates and runs a simple Koog agent using the [`Claude Sonnet 4.5`](https://www.anthropic.com/news/claude-sonnet-4-5) model via the Bedrock API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleBedrockExecutorWithBearerToken
    import ai.koog.prompt.executor.clients.bedrock.BedrockModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the Bedrock API key from the BEDROCK_API_KEY environment variable
        val apiKey = System.getenv("BEDROCK_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleBedrockExecutorWithBearerToken(apiKey),
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

=== "Mistral"

    The following example creates and runs a simple Koog agent using the [`Mistral Medium 3.1`](https://docs.mistral.ai/models/mistral-medium-3-1-25-08) model via the Mistral AI API.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleMistralAIExecutor
    import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
    import kotlinx.coroutines.runBlocking
    -->
    ```kotlin
    fun main() = runBlocking {
        // Get the Mistral AI API key from the MISTRAL_API_KEY environment variable
        val apiKey = System.getenv("MISTRAL_API_KEY")
            ?: error("The API key is not set.")
        
        // Create an agent
        val agent = AIAgent(
            promptExecutor = simpleMistralAIExecutor(apiKey),
            llmModel = MistralAIModels.Chat.MistralMedium31
        )
    
        // Run the agent
        val result = agent.run("Hello! How can you help me?")
        println(result)
    }
    ```
    <!--- KNIT example-getting-started-07.kt -->

    The example can produce the following output:

    ```
    I can assist you with a wide range of topics and tasks. Here are some examples:

    1. **Answering questions**: I can provide information on various subjects, including history, science, technology, literature, and more.
    2. **Providing definitions**: If you're unsure about the meaning of a word or phrase, I can help define it for you.
    3. **Generating text**: Whether it's writing an email, creating content for social media, or composing a story, I can help with text generation.
    4. **Translation**: I can translate text from one language to another.
    5. **Conversation**: We can have a chat about any topic that interests you, and I'll respond accordingly.
    6. **Language practice**: If you're learning a new language, I can help with pronunciation, grammar, and vocabulary practice.
    7. **Brainstorming**: If you're stuck on a problem or need ideas for a project, I can help brainstorm solutions.
    8. **Summarization**: If you have a long piece of text and want a summary, I can condense it for you.
    
    What's on your mind? Is there something specific you'd like help with?
    ```

=== "Ollama"

    The following example creates and runs a simple Koog agent using the [`llama3.2`](https://ollama.com/library/llama3.2) model running locally via Ollama.

    <!--- INCLUDE
    import ai.koog.agents.core.agent.AIAgent
    import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
    import ai.koog.prompt.executor.ollama.client.OllamaModels
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
    <!--- KNIT example-getting-started-08.kt -->

    The example can produce the following output:

    ```
    I can assist with various tasks such as answering questions, providing information, and even helping with language-related tasks like proofreading or writing suggestions. What's on your mind today?
    ```

## Next steps

- Learn more about [building agents](agents/index.md)

