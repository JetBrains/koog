# Prompt executors

Prompt executors provide a higher-level abstraction that lets you manage the lifecycle of one or multiple LLM clients.
You can work with multiple LLM providers through a unified interface, abstracting from provider-specific details,
with dynamic switching between them and fallbacks.

## Executor types

Koog provides two main types of prompt executors that implement the [`PromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-model/ai.koog.prompt.executor.model/-prompt-executor/index.html) interface:

| Type            | <div style="width:175px">Class</div>                                                                                                                               | Description                                                                                                                                                                                                                                                          |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Single-provider | [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html) | Wraps a single LLM client for one provider. Use this executor if your agent only requires switching between models within a single LLM provider.                                                                                                                     |
| Multi-provider  | [`MultiLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-multi-l-l-m-prompt-executor/index.html)   | Wraps multiple LLM clients and routes calls based on the LLM provider. It can optionally use a configured fallback provider and LLM when the requested client is unavailable. Use this executor if your agent needs to switch between LLMs from different providers. |

## Creating a single-provider executor

To create a prompt executor for a specific LLM provider, perform the following:

1. Configure an LLM client for a specific provider with the corresponding API key.
2. Create a prompt executor using [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html).

Here is an example:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val promptExecutor = MultiLLMPromptExecutor(openAIClient)
```
<!--- KNIT example-prompt-executors-01.kt -->

## Creating a multi-provider executor

To create a prompt executor that works with multiple LLM providers, do the following:

1. Configure clients for the required LLM providers with the corresponding API keys.
2. Pass the configured clients to the [`MultiLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-multi-l-l-m-prompt-executor/index.html) class constructor to create a prompt executor
   with multiple LLM providers.

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val ollamaClient = OllamaClient()

val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Ollama to ollamaClient
)
```
<!--- KNIT example-prompt-executors-02.kt -->

## Pre-defined prompt executors

For faster setup, Koog provides the ready-to-use executor implementations for common providers.

The following table includes the **pre-defined single-provider executors**
that return `SingleLLMPromptExecutor` configured with a specific LLM client.

| LLM provider   | Prompt executor                                                                                                                                                                             | Description                                                                      |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| OpenAI         | [simpleOpenAIExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-open-a-i-executor.html)                                  | Wraps `OpenAILLMClient` that runs prompts with OpenAI models.                    |
| OpenAI         | [simpleAzureOpenAIExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-azure-open-a-i-executor.html)                       | Wraps `OpenAILLMClient` configured for using Azure OpenAI Service.               |
| Anthropic      | [simpleAnthropicExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-anthropic-executor.html)                              | Wraps `AnthropicLLMClient` that runs prompts with Anthropic models.              |
| Google         | [simpleGoogleAIExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-google-a-i-executor.html)                              | Wraps `GoogleLLMClient` that runs prompts with Google models.                    |
| OpenRouter     | [simpleOpenRouterExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-open-router-executor.html)                           | Wraps `OpenRouterLLMClient` that runs prompts with OpenRouter.                   |
| Amazon Bedrock | [simpleBedrockExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-bedrock-executor.html)                                  | Wraps `BedrockLLMClient` that runs prompts with AWS Bedrock.                     |
| Amazon Bedrock | [simpleBedrockExecutorWithBearerToken](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-bedrock-executor-with-bearer-token.html) | Wraps `BedrockLLMClient` and uses the provided Bedrock API key to send requests. |
| Mistral        | [simpleMistralAIExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-mistral-a-i-executor.html)                            | Wraps `MistralAILLMClient` that runs prompts with Mistral models.                |
| Ollama         | [simpleOllamaAIExecutor](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms-all/ai.koog.prompt.executor.llms.all/simple-ollama-a-i-executor.html)                              | Wraps `OllamaClient` that runs prompts with Ollama.                              |

Here is an example of creating pre-defined single and multi-provider executors:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import kotlinx.coroutines.runBlocking
-->
```kotlin
// Create an OpenAI executor
val promptExecutor = simpleOpenAIExecutor("OPENAI_KEY")

// Create a MultiLLMPromptExecutor with OpenAI, Anthropic, and Google LLM clients
val openAIClient = OpenAILLMClient("OPENAI_KEY")
val anthropicClient = AnthropicLLMClient("ANTHROPIC_KEY")
val googleClient = GoogleLLMClient("GOOGLE_KEY")
val multiExecutor = MultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
<!--- KNIT example-prompt-executors-03.kt -->

## Running a prompt

To run a prompt using a prompt executor, do the following:

1. Create a prompt executor.
2. Run the prompt with the specific LLM using the `execute()` method.

Here is an example:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
-->
<!--- SUFFIX
    }
}
-->
```kotlin
// Create an OpenAI executor
val promptExecutor = simpleOpenAIExecutor("OPENAI_KEY")

// Execute a prompt
val response = promptExecutor.execute(
    prompt = prompt("demo") { user("Summarize this.") },
    model = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-prompt-executors-04.kt -->

This will run the prompt with the `GPT4o` model and return the response.

!!! note
    The prompt executors provide methods to run prompts using various capabilities, 
    such as streaming, multiple choice generation, and content moderation.
    Since prompt executors wrap LLM clients, each executor supports the capabilities of the corresponding client.
    For details, refer to [LLM clients](llm-clients.md).

## Switching between providers

When you work with multiple LLM providers using `MultiLLMPromptExecutor`, you can switch between them.
The process is as follows:

1. Create an LLM client instance for each provider you want to use.
2. Create a `MultiLLMPromptExecutor` that maps LLM providers to LLM clients.
3. Run a prompt with a model from the corresponding client passed as an argument to the `execute()` method.
   The prompt executor will use the corresponding client based on the model provider to run the prompt.

Here is an example of switching between providers:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
-->
<!--- SUFFIX
}
-->
```kotlin
// Create LLM clients for OpenAI, Anthropic, and Google providers
val openAIClient = OpenAILLMClient("OPENAI_API_KEY")
val anthropicClient = AnthropicLLMClient("ANTHROPIC_API_KEY")
val googleClient = GoogleLLMClient("GOOGLE_API_KEY")

// Create a MultiLLMPromptExecutor that maps LLM providers to LLM clients
val executor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient,
    LLMProvider.Google to googleClient
)

// Create a prompt
val p = prompt("demo") { user("Summarize this.") }

// Run the prompt with an OpenAI model; the prompt executor automatically switches to the OpenAI client
val openAIResult = executor.execute(p, OpenAIModels.Chat.GPT4o)

// Run the prompt with an Anthropic model; the prompt executor automatically switches to the Anthropic client
val anthropicResult = executor.execute(p, AnthropicModels.Sonnet_3_5)
```
<!--- KNIT example-prompt-executors-05.kt -->

You can optionally configure a fallback LLM provider and model to use when the requested client is unavailable.
For details, refer to [Configuring fallbacks](#configuring-fallbacks).

## Configuring fallbacks

Multi-provider prompt executors can be configured to use a fallback LLM provider and model when the requested LLM client is unavailable.
To configure the fallback mechanism, provide the `fallback` parameter to the `MultiLLMPromptExecutor` constructor:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.llm.LLMProvider
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val ollamaClient = OllamaClient()

val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Ollama to ollamaClient,
    fallback = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
        fallbackProvider = LLMProvider.Ollama,
        fallbackModel = OllamaModels.Meta.LLAMA_3_2
    )
)
```
<!--- KNIT example-prompt-executors-06.kt -->

If you pass a model from an LLM provider that is not included in the `MultiLLMPromptExecutor`,
the prompt executor will use the fallback model:

<!--- INCLUDE
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking

val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val ollamaClient = OllamaClient()

val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Ollama to ollamaClient,
    fallback = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
        fallbackProvider = LLMProvider.Ollama,
        fallbackModel = OllamaModels.Meta.LLAMA_3_2
    )
)

fun main() = runBlocking {
-->
<!--- SUFFIX
}
-->
```kotlin
// Create a prompt
val p = prompt("demo") { user("Summarize this") }
// If you pass a Google model, the prompt executor will use the fallback model, as the Google client is not included
val response = multiExecutor.execute(p, GoogleModels.Gemini2_5Pro)
```
<!--- KNIT example-prompt-executors-07.kt -->

!!! note
    Fallbacks are available for the `execute()` and `executeMultipleChoices()` methods only.






