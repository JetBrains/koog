# Prompt API

The Prompt API lets you create well-structured prompts with Kotlin DSL, execute them against different LLM providers, and process responses in different formats.

## Create a prompt

The Prompt API uses Kotlin DSL to create prompts. It supports the following types of messages:

- `system`: sets the context and instructions for the LLM.
- `user`: represents user input.
- `assistant`: represents LLM responses.

Here's an example of a simple prompt:

```kotlin
val prompt = prompt("prompt_name", LLMParams()) {
    // Add a system message to set the context
    system("You are a helpful assistant.")

    // Add a user message
    user("Tell me about Kotlin")

    // You can also add assistant messages for few-shot examples
    assistant("Kotlin is a modern programming language...")

    // Add another user message
    user("What are its key features?")
}
```

## Execute a prompt

To execute a prompt with a specific LLM, you need to the following:

1. Create a corresponding LLM client that handles the connection between your application and LLM providers. For example:
```kotlin
// Create an OpenAI client
val client = OpenAILLMClient(apiKey)
```
2. Call the `execute` method with the prompt and LLM as arguments.
```kotlin
// Execute the prompt
val response = client.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o  // You can choose different models
)
```

The following LLM clients are available:

* [OpenAILLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai/-open-a-i-l-l-m-client/index.html)
* [AnthropicLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic/-anthropic-l-l-m-client/index.html)
* [GoogleLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google/-google-l-l-m-client/index.html)
* [OpenRouterLLMClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter/-open-router-l-l-m-client/index.html)
* [OllamaClient](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-ollama-client/ai.koog.prompt.executor.ollama.client/-ollama-client/index.html)


Here's a simple example of using the Prompt API:

```kotlin
fun main() {
    // Set up the OpenAI client with your API key
    val token = System.getenv("OPENAI_API_KEY")
    val client = OpenAILLMClient(token)

    // Create a prompt
    val prompt = prompt("prompt_name", LLMParams()) {
        // Add a system message to set the context
        system("You are a helpful assistant.")

        // Add a user message
        user("Tell me about Kotlin")

        // You can also add assistant messages for few-shot examples
        assistant("Kotlin is a modern programming language...")

        // Add another user message
        user("What are its key features?")
    }

    // Execute the prompt and get the response
    val response = client.execute(prompt = prompt, model = OpenAIModels.Chat.GPT4o)
    println(response)
}
```

## Prompt executors

Prompt executors provide a higher-level way to work with LLMs, handling the details of client creation and management.

You can use a prompt executor to manage and run prompts.
You can choose a prompt executor based on the LLM provider you plan to use or create a custom prompt executor using one of the available LLM clients.

The Koog framework provides several prompt executors:

- **Single provider executors**:
    - `simpleOpenAIExecutor`: for executing prompts with OpenAI models.
    - `simpleAnthropicExecutor`: for executing prompts with Anthropic models.
    - `simpleGoogleExecutor`: for executing prompts with Google models.
    - `simpleOpenRouterExecutor`: for executing prompts with OpenRouter.
    - `simpleOllamaExecutor`: for executing prompts with Ollama.

- **Multi-provider executor**:
    - `DefaultMultiLLMPromptExecutor`: For working with multiple LLM providers

### Create a single provider executor

To create a prompt executor for a specific LLM provider, use the corresponding function.
For example, to create the OpenAI prompt executor, you need to call the `simpleOpenAIExecutor` function and provide it with the API key required for authentication with the OpenAI service:

1. Create a prompt executor:
```kotlin
// Create an OpenAI executor
val promptExecutor = simpleOpenAIExecutor(apiToken)
```
2. Execute the prompt with a specific LLM:
```kotlin
// Execute a prompt
val response = promptExecutor.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o
)
```

### Create a multi-provider executor

To create a prompt executor that works with multiple LLM providers, do the following:

1. Configure clients for the required LLM providers with the corresponding API keys. For example:
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val anthropicClient = AnthropicLLMClient(System.getenv("ANTHROPIC_KEY"))
val googleClient = GoogleLLMClient(System.getenv("GOOGLE_KEY"))
```
2. Pass the configured clients to the `DefaultMultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
```kotlin
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
3. Execute the prompt with a specific LLM:
```kotlin
val response = multiExecutor.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o
)
```
