# Running prompts with prompt executors

While LLM clients provide direct access to providers, prompt executors offer a higher-level abstraction that simplifies common use cases and handles client lifecycle management.
They are ideal when you need to:

- Quickly prototype without managing client configuration.
- Work with multiple providers through a unified interface.
- Simplify dependency injection in larger applications.
- Abstract away provider-specific details.

### Executor types

Koog provides two main prompt executors:

| <div style="width:175px">Name</div> | Description                                                                                                                                                                                                                             |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html)       | Wraps a single LLM client for one provider. Use this executor if your agent only requires the ability to switch between models within a single LLM provider.                                                                            |
| [`MultiLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-multi-l-l-m-prompt-executor/index.html)        | Routes to multiple LLM clients by a provider, with optional fallbacks for each provider to be used when a requested provider is not available. Use this executor if your agent needs to switch between models from different providers. |

These are implementations of the [`PromtExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-model/ai.koog.prompt.executor.model/-prompt-executor/index.html) interface for executing prompts with LLMs.

### Creating a single provider executor

To create a prompt executor for a specific LLM provider, perform the following:

1) Configure an LLM client for a specific provider with the corresponding API key:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
```
<!--- KNIT example-prompt-api-09.kt -->
2) Create a prompt executor using [`SingleLLMPromptExecutor`](https://api.koog.ai/prompt/prompt-executor/prompt-executor-llms/ai.koog.prompt.executor.llms/-single-l-l-m-prompt-executor/index.html):
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi09.openAIClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
-->
```kotlin
val promptExecutor = SingleLLMPromptExecutor(openAIClient)
```
<!--- KNIT example-prompt-api-10.kt -->

### Creating a multi-provider executor

To create a prompt executor that works with multiple LLM providers, do the following:

1) Configure clients for the required LLM providers with the corresponding API keys:
<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
-->
```kotlin
val openAIClient = OpenAILLMClient(System.getenv("OPENAI_KEY"))
val ollamaClient = OllamaClient()
```
<!--- KNIT example-prompt-api-11.kt -->

2) Pass the configured clients to the `MultiLLMPromptExecutor` class constructor to create a prompt executor with multiple LLM providers:
<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi11.openAIClient
import ai.koog.agents.example.examplePromptApi11.ollamaClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
-->
```kotlin
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Ollama to ollamaClient
)
```
<!--- KNIT example-prompt-api-12.kt -->

### Pre-defined prompt executors

For faster setup, Koog provides the following ready-to-use executor implementations for common providers:

- Single provider executors that return `SingleLLMPromptExecutor` configured with a certain LLM client:
    - `simpleOpenAIExecutor` for executing prompts with OpenAI models.
    - `simpleAzureOpenAIExecutor` for executing prompts using Azure OpenAI Service.
    - `simpleAnthropicExecutor` for executing prompts with Anthropic models.
    - `simpleGoogleAIExecutor` for executing prompts with Google models.
    - `simpleOpenRouterExecutor` for executing prompts with OpenRouter.
    - `simpleOllamaAIExecutor` for executing prompts with Ollama.

- Multi-provider executor:
    - `DefaultMultiLLMPromptExecutor` which is an implementation of `MultiLLMPromptExecutor` that supports OpenAI, Anthropic, and Google providers.

Here is an example of creating pre-defined single and multi-provider executors:

<!--- INCLUDE
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.DefaultMultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
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

// Create a DefaultMultiLLMPromptExecutor with OpenAI, Anthropic, and Google LLM clients
val openAIClient = OpenAILLMClient("OPENAI_KEY")
val anthropicClient = AnthropicLLMClient("ANTHROPIC_KEY")
val googleClient = GoogleLLMClient("GOOGLE_KEY")
val multiExecutor = DefaultMultiLLMPromptExecutor(openAIClient, anthropicClient, googleClient)
```
<!--- KNIT example-prompt-api-13.kt -->

### Executing a prompt

The prompt executors provide methods to run prompts using various capabilities, such as streaming, multiple choices generation, and content moderation.

Here is an example of how to run a prompt with a specific LLM using the `execute` method:

<!--- INCLUDE
import ai.koog.agents.example.examplePromptApi04.prompt
import ai.koog.agents.example.examplePromptApi10.promptExecutor
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
// Execute a prompt
val response = promptExecutor.execute(
    prompt = prompt,
    model = OpenAIModels.Chat.GPT4o
)
```
<!--- KNIT example-prompt-api-14.kt -->

This will run the prompt with the `GPT4o` model and return the response.

!!!note
The prompt executors let you stream responses, generate multiple choices, and run content moderation.
For more information, refer to the API Reference for the specific executor.
To learn more about content moderation, see [Content moderation](../content-moderation.md).
