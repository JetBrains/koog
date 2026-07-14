# Building AI agents with DaoXE and Koog

[:material-github: Open source example](
https://github.com/JetBrains/koog/blob/develop/examples/simple-examples/src/main/kotlin/ai/koog/agents/example/client/DaoXEAgent.kt
){ .md-button .md-button--primary }

This guide shows how to run a Koog agent against [DaoXE](https://daoxe.com) using the **OpenAI-compatible** Chat Completions path.

DaoXE is a **multi-model, multi-protocol** API gateway. It is not limited to a single vendor or a single wire format:

- OpenAI-compatible Chat Completions and Responses (where available)
- Anthropic Messages (Claude protocol)
- Additional catalog endpoints (for example image-compatible APIs) depending on your account

Koog’s built-in `OpenAILLMClient` speaks the OpenAI-compatible surface. Point it at DaoXE by setting the base URL, API key, and a **model id from your DaoXE account**. Other DaoXE protocols (for example Anthropic Messages) are outside this sample; use them with a client that implements that protocol when needed.

!!! warning "Regional availability"
    DaoXE does **not** provide service in mainland China. Use the gateway only from regions where DaoXE is offered.

## What you'll learn

- How to reuse `OpenAILLMClient` with a custom base URL
- How to pass account-specific model ids as `LLModel`
- How to run the switch-tool agent against `https://daoxe.com/v1`

## Prerequisites

- A DaoXE account and API key from [daoxe.com](https://daoxe.com)
- At least one model id visible in your account catalog / pricing page
- Access from a supported region (not mainland China)
- Basic understanding of Kotlin coroutines

## Configuration

Set environment variables (or `examples/simple-examples/env.properties`):

```shell
export DAOXE_API_KEY=your-daoxe-api-key
export DAOXE_MODEL=model-id-from-your-account-catalog
```

OpenAI-compatible base URL used by this example:

```text
https://daoxe.com/v1
```

With Koog’s `OpenAIClientSettings`, that means:

- `baseUrl = "https://daoxe.com"`
- default path `v1/chat/completions` → `https://daoxe.com/v1/chat/completions`

## Example overview

The sample reuses the same switch tools as the Bedrock / SimpleAPI demos:

1. Define tools with `@Tool` / `@LLMDescription`
2. Build a `ToolRegistry`
3. Construct `OpenAILLMClient` with DaoXE settings
4. Create an `LLModel` whose `id` is your DaoXE model id (`DAOXE_MODEL`)
5. Run `AIAgent` with `MultiLLMPromptExecutor`

### Point OpenAI client at DaoXE

```kotlin
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

val daoxeSettings = OpenAIClientSettings(
    baseUrl = "https://daoxe.com",
)

val daoxeClient = OpenAILLMClient(
    apiKey = System.getenv("DAOXE_API_KEY")
        ?: error("DAOXE_API_KEY env is not set"),
    settings = daoxeSettings,
)

val modelId = System.getenv("DAOXE_MODEL")
    ?.takeIf { it.isNotBlank() }
    ?: error("Set DAOXE_MODEL to a model id from your DaoXE account catalog")

val daoxeModel = LLModel(
    provider = LLMProvider.OpenAI,
    id = modelId,
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Completion,
    ),
)

val executor = MultiLLMPromptExecutor(LLMProvider.OpenAI to daoxeClient)
```

### Run the agent

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry

val toolRegistry = ToolRegistry {
    tools(SwitchTools(switch).asTools())
}

val agent = AIAgent(
    promptExecutor = executor,
    strategy = singleRunStrategy(parallelTools = false),
    llmModel = daoxeModel,
    systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
    temperature = 0.0,
    toolRegistry = toolRegistry,
)

println(agent.run("Turn the switch on and confirm the state."))
```

## Run from the simple-examples project

```bash
cd examples/simple-examples
export DAOXE_API_KEY=your-daoxe-api-key
export DAOXE_MODEL=model-id-from-your-account-catalog
./gradlew runExampleDaoXEAgent
```

Source:

[`examples/simple-examples/src/main/kotlin/ai/koog/agents/example/client/DaoXEAgent.kt`](https://github.com/JetBrains/koog/blob/develop/examples/simple-examples/src/main/kotlin/ai/koog/agents/example/client/DaoXEAgent.kt)

## Notes

| Topic | Detail |
|-------|--------|
| Base URL | `https://daoxe.com/v1` (OpenAI-compatible Chat Completions) |
| Auth | `Authorization: Bearer <DAOXE_API_KEY>` (handled by `OpenAILLMClient`) |
| Model ids | Always take from the live DaoXE account catalog; ids and availability change |
| Multi-protocol | DaoXE also supports Anthropic Messages and other protocols; this sample covers OpenAI-compatible only |
| Region | Not available in mainland China |

## Related docs

- [LLM providers](../llm-providers.md)
- [LLM clients](../prompts/llm-clients.md)
- [Prompt executors](../prompts/prompt-executors.md)
- [Bedrock agent example](BedrockAgent.md) (same tool pattern, different backend)
