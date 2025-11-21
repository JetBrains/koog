# LLM parameters

This page provides details about LLM parameters in the Koog agentic framework. LLM parameters let you control and customize the behavior of language models.

## Overview

LLM parameters are configuration options that let you fine-tune how language models generate responses. These parameters control aspects like response randomness, length, format, and tool usage. By adjusting the parameters, you optimize model behavior for different use cases, from creative content generation to deterministic structured outputs.

In Koog, the `LLMParams` class incorporates LLM parameters and provides a consistent interface for configuring language model behavior. You can use LLM parameters in the following ways:

- When creating a prompt:

    <!--- INCLUDE
    import ai.koog.prompt.prompt
    import ai.koog.prompt.params.LLMParams
    -->
    ```kotlin
    val prompt = prompt(
        id = "dev-assistant",
        params = LLMParams(
            temperature = 0.7,
            maxTokens = 500
        )
    ) {
        // Add a system message to set the context
        system("You are a helpful assistant.")

        // Add a user message
        user("Tell me about Kotlin")
    }
    ```
    <!--- KNIT example-llm-parameters-01.kt -->

    For more information about prompt creation, see [Prompts](prompt-api.md).

- When creating a subgraph:

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    import ai.koog.agents.ext.tool.SayToUser
    import ai.koog.prompt.executor.clients.openai.OpenAIModels
    import ai.koog.agents.ext.agent.subgraphWithTask
    val searchTool = SayToUser
    val calculatorTool = SayToUser
    val weatherTool = SayToUser
    val strategy = strategy<String, String>("strategy_name") {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val processQuery by subgraphWithTask<String, String>(
        tools = listOf(searchTool, calculatorTool, weatherTool),
        llmModel = OpenAIModels.Chat.GPT4o,
        llmparams = LLMParams(
            temperature = 0.7,
            maxTokens = 500,
        )
    ) { userQuery ->
        """
        You are a helpful assistant that can answer questions about various topics.
        Please help with the following query:
        $userQuery
        """
    }
    ```
    <!--- KNIT example-llm-parameters-02.kt -->

    For more information on how to create and implement your own subgraphs, see [Custom subgraphs](custom-subgraphs.md).

- When updating a prompt in an LLM write session:

    <!--- INCLUDE
    import ai.koog.agents.core.dsl.builder.strategy
    val strategy = strategy<Unit, Unit>("strategy-name") {
    val node by node<Unit, Unit> {
    -->
    <!--- SUFFIX
       }
    }
    -->
    ```kotlin
    llm.writeSession {
        changeLLMParams(
            LLMParams(
                temperature = 0.7,
                maxTokens = 500
            )
        )
    }
    ```
    <!--- KNIT example-llm-parameters-03.kt -->

    For more information about sessions, see [LLM sessions and manual history management](sessions.md).

## LLM parameter reference

The following table provides a reference of LLM parameters included in the `LLMParams` class and supported by all LLM providers that are available in Koog out of the box. 
For a list of parameters that are specific to some providers, see [Provider-specific parameters](#provider-specific-parameters).

| Parameter              | Type                           | Description                                                                                                                                                                                     |
|------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `temperature`          | Double                         | Controls randomness in the output. Higher values, such as 0.7–1.0, produce more diverse and creative responses, while lower values produce more deterministic and focused responses.            |
| `maxTokens`            | Integer                        | Maximum number of tokens to generate in the response. Useful for controlling response length.                                                                                                   |
| `numberOfChoices`      | Integer                        | Number of alternative responses to generate. Must be greater than 0.                                                                                                                            |
| `speculation`          | String                         | A speculative configuration string that influences model behavior, designed to enhance result speed and accuracy. Supported only by certain models, but may greatly improve speed and accuracy. |
| `schema`               | Schema                         | Defines the structure for the model's response format, enabling structured outputs like JSON. For more information, see [Schema](#schema).                                                      |
| `toolChoice`           | ToolChoice                     | Controls tool calling behavior of the language model. For more information, see [Tool choice](#tool-choice).                                                                                    |
| `user`                 | String                         | Identifier for the user making the request, which can be used for tracking purposes.                                                                                                            |
| `additionalProperties` | Map&lt;String, JsonElement&gt; | Additional properties that can be used to store custom parameters specific to certain model providers.                                                                                          |

For a list of default values for each parameter, see the corresponding LLM provider documentation:

- [OpenAI Chat](https://platform.openai.com/docs/api-reference/chat/create)
- [OpenAI Responses](https://platform.openai.com/docs/api-reference/responses/create)
- [Google](https://ai.google.dev/api/generate-content#generationconfig)
- [Anthropic](https://platform.claude.com/docs/en/api/messages/create)
- [Mistral](https://docs.mistral.ai/api/#operation/chatCompletions)
- [DeepSeek](https://api-docs.deepseek.com/api/create-chat-completion#request)
- [OpenRouter](https://openrouter.ai/docs/api/reference/parameters)
- Alibaba ([DashScope](https://www.alibabacloud.com/help/en/model-studio/qwen-api-reference?spm=a2c63.p38356.help-menu-2400256.d_2_1_0.f57d482eBkrliN))

## Schema

The `Schema` interface defines the structure for the model's response format. 
Koog supports JSON schemas, as described in the sections below.

### JSON schemas

JSON schemas let you request structured JSON data from language models. Koog supports the following two types of JSON schemas:

1. **Basic JSON Schema** (`LLMParams.Schema.JSON.Basic`): Used for basic JSON processing capabilities. This format primarily focuses on nested data definitions without advanced JSON Schema functionalities.

    <!--- INCLUDE
    import ai.koog.prompt.params.LLMParams
    import kotlinx.serialization.json.JsonObject
    import kotlinx.serialization.json.JsonArray
    import kotlinx.serialization.json.JsonPrimitive
    -->
    ```kotlin
    // Create parameters with a basic JSON schema
    val jsonParams = LLMParams(
        temperature = 0.2,
        schema = LLMParams.Schema.JSON.Basic(
            name = "PersonInfo",
            schema = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                        "age" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                        "skills" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("array"),
                                "items" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                            )
                        )
                    )
                ),
                "additionalProperties" to JsonPrimitive(false),
                "required" to JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("age"), JsonPrimitive("skills")))
            ))
        )
    )
    ```
    <!--- KNIT example-llm-parameters-04.kt -->

2. **Standard JSON Schema** (`LLMParams.Schema.JSON.Standard`): Represents a standard JSON schema according to [json-schema.org](https://json-schema.org/). This format is a proper subset of the official JSON Schema specification. Note that the flavor across different LLM providers might vary, since not all of them support full JSON schemas.

    <!--- INCLUDE
    import ai.koog.prompt.params.LLMParams
    import kotlinx.serialization.json.JsonObject
    import kotlinx.serialization.json.JsonPrimitive
    import kotlinx.serialization.json.JsonArray
    -->
    ```kotlin
    // Create parameters with a standard JSON schema
    val standardJsonParams = LLMParams(
        temperature = 0.2,
        schema = LLMParams.Schema.JSON.Standard(
            name = "ProductCatalog",
            schema = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "products" to JsonObject(mapOf(
                        "type" to JsonPrimitive("array"),
                        "items" to JsonObject(mapOf(
                            "type" to JsonPrimitive("object"),
                            "properties" to JsonObject(mapOf(
                                "id" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                "price" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                                "description" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                            )),
                            "additionalProperties" to JsonPrimitive(false),
                            "required" to JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("name"), JsonPrimitive("price"), JsonPrimitive("description")))
                        ))
                    ))
                )),
                "additionalProperties" to JsonPrimitive(false),
                "required" to JsonArray(listOf(JsonPrimitive("products")))
            ))
        )
    )
    ```
    <!--- KNIT example-llm-parameters-05.kt -->

## Tool choice

The `ToolChoice` class controls how the language model uses tools. It provides the following options:

* `LLMParams.ToolChoice.Named`: the language model calls the specified tool. Takes the `name` string argument that
represents the name of the tool to call.
* `LLMParams.ToolChoice.All`: the language model calls all tools.
* `LLMParams.ToolChoice.None`: the language model does not call tools and only generates text.
* `LLMParams.ToolChoice.Auto`: the language model automatically decides whether to call tools and which tool to call.
* `LLMParams.ToolChoice.Required`: the language model calls at least one tool.

Here is an example of using the `LLMParams.ToolChoice.Named` class to call a specific tool:



<!--- INCLUDE
import ai.koog.prompt.params.LLMParams
-->
```kotlin
val specificToolParams = LLMParams(
    toolChoice = LLMParams.ToolChoice.Named(name = "calculator")
)
```
<!--- KNIT example-llm-parameters-01.kt -->

## Provider-specific parameters

Koog supports provider-specific parameters for some LLM providers. These parameters extend the base `LLMParams` class
and add provider-specific functionality. The following classes include parameters that are specific per provider:

- `OpenAIChatParams`: Parameters specific to the OpenAI Chat Completions API.
- `OpenAIResponsesParams`: Parameters specific to the OpenAI Responses API.
- `GoogleParams`: Parameters specific to Google models.
- `AnthropicParams`: Parameters specific to Anthropic models.
- `MistralAIParams`: Parameters specific to Mistral models.
- `DeepSeekParams`: Parameters specific to DeepSeek models.
- `OpenRouterParams`: Parameters specific to OpenRouter models.
- `DashscopeParams`: Parameters specific to Alibaba models.

Here is the complete reference of provider-specific parameters in Koog:

| Parameter           | Providers                                                                                            | Type                                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|---------------------|------------------------------------------------------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `topP`              | OpenAI Chat, OpenAI Responses, DeepSeek, OpenRouter, Anthropic, Google, Alibaba (DashScope), Mistral | Double                                      | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.                                                                                                                                                                                                                                                                                               |
| `logprobs`          | OpenAI Chat, OpenAI Responses, DeepSeek, OpenRouter, Alibaba (DashScope)                             | Boolean                                     | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `topLogprobs`       | OpenAI Chat, OpenAI Responses, DeepSeek, OpenRouter, Alibaba (DashScope)                             | Integer                                     | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `topK`              | OpenRouter, Anthropic, Google                                                                        | Integer                                     | Number of top tokens to consider when generating the output. Takes a value greater than or equal to 0 (provider-specific minimums may apply).                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `frequencyPenalty`  | OpenAI Chat, DeepSeek, OpenRouter, Alibaba (DashScope), Mistral                                      | Double                                      | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                                                                                                                                                                                                                                                                                                                    |
| `presencePenalty`   | OpenAI Chat, DeepSeek, OpenRouter, Alibaba (DashScope), Mistral                                      | Double                                      | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                                                                                                                                                                                                                                                                                                                            |
| `stop`              | OpenAI Chat, DeepSeek, OpenRouter, Alibaba (DashScope), Mistral                                      | List&lt;String&gt;                          | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                                                                                                                                                                                                                                                                                                                            |
| `parallelToolCalls` | OpenAI Chat, OpenAI Responses, DashScope, Mistral                                                    | Boolean                                     | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies.                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `promptCacheKey`    | OpenAI Chat, OpenAI Responses                                                                        | String                                      | Stable cache key for prompt caching. OpenAI uses it to cache responses for similar requests.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `safetyIdentifier`  | OpenAI Chat, OpenAI Responses                                                                        | String                                      | A stable and unique user identifier that may be used to detect users who violate OpenAI policies.                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `serviceTier`       | OpenAI Chat, OpenAI Responses                                                                        | ServiceTier                                 | OpenAI processing tier selection that lets you prioritize performance over cost or vice versa. For more information, see the API documentation for [ServiceTier](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-service-tier/index.html).                                                                                                                                                                                                                           |
| `store`             | OpenAI Chat, OpenAI Responses                                                                        | Boolean                                     | If `true`, the provider may store outputs for later retrieval.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `audio`             | OpenAI Chat                                                                                          | OpenAIAudioConfig                           | Audio output configuration when using audio-capable models. For more information, see the API documentation for [OpenAIAudioConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-open-a-i-audio-config/index.html).                                                                                                                                                                                                                                               |
| `reasoningEffort`   | OpenAI Chat                                                                                          | ReasoningEffort                             | Specifies the level of reasoning effort that the model will use. For more information and available values, see the API documentation for [ReasoningEffort](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-reasoning-effort/index.html).                                                                                                                                                                                                                            |
| `webSearchOptions`  | OpenAI Chat                                                                                          | OpenAIWebSearchOptions                      | Configure web search tool usage (if supported). For more information, see the API documentation for [OpenAIWebSearchOptions](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-open-a-i-web-search-options/index.html).                                                                                                                                                                                                                                                |
| `background`        | OpenAI Responses                                                                                     | Boolean                                     | Run the response in the background.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `include`           | OpenAI Responses                                                                                     | List&lt;OpenAIInclude&gt;                   | Additional data to include in the model's response, such as sources of web search tool call or search results of a file search tool call. For detailed reference information, see [OpenAIInclude](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-open-a-i-include/index.html) in the Koog API reference. To learn more about the `include` parameter, see [OpenAI's documentation](https://platform.openai.com/docs/api-reference/responses/create#responses-create-include). |
| `maxToolCalls`      | OpenAI Responses                                                                                     | Integer                                     | Maximum total number of built-in tool calls allowed in this response. Takes a value equal to or greater than `0`.                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `reasoning`         | OpenAI Responses                                                                                     | ReasoningConfig                             | Reasoning configuration for reasoning-capable models. For more information, see the API documentation for [ReasoningConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-reasoning-config/index.html).                                                                                                                                                                                                                                                                      |
| `truncation`        | OpenAI Responses                                                                                     | Truncation                                  | Truncation strategy when nearing the context window. For more information, see the API documentation for [Truncation](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-truncation/index.html).                                                                                                                                                                                                                                                                                  |
| `thinkingConfig`    | Google                                                                                               | GoogleThinkingConfig                        | Controls whether the model should expose its chain-of-thought and how many tokens it may spend on it. For more information, see the API reference for [GoogleThinkingConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google.models/-google-thinking-config/index.html).                                                                                                                                                                                                               |
| `stopSequences`     | Anthropic                                                                                            | List&lt;String&gt;                          | Custom text sequences that cause the model to stop generating content. If matched, the value of `stop_reason` in the response is `stop_sequence`.                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `container`         | Anthropic                                                                                            | String                                      | Container identifier for reuse across requests. Containers are used by Anthropic's code execution tool to provide a secure and containerized code execution environment. By providing the container identifier from a previous response, you can reuse containers across multiple requests, which preserves created files between requests. For more information, see [Containers](https://platform.claude.com/docs/en/agents-and-tools/tool-use/code-execution-tool#containers) in Anthropic's documentation.                                                                 |
| `mcpServers`        | Anthropic                                                                                            | List&lt;AnthropicMCPServerURLDefinition&gt; | Definitions of MCP servers to be used in the request. Supports at most 20 servers. For more information, see the API reference for [AnthropicMCPServerURLDefinition](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-m-c-p-server-u-r-l-definition/index.html).                                                                                                                                                                                                |
| `serviceTier`       | Anthropic                                                                                            | AnthropicServiceTier                        | Determines whether to use priority capacity (if available) or standard capacity for the request. For more information, see the API reference for [AnthropicServiceTier](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-service-tier/index.html) and Anthropic's [Service tiers](https://platform.claude.com/docs/en/api/service-tiers) documentation.                                                                                                         |
| `thinking`          | Anthropic                                                                                            | AnthropicThinking                           | Configuration for activating Claude's extended thinking. When activated, responses also include thinking content blocks. For more information, see the API reference for [AnthropicThinking](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-thinking/index.html).                                                                                                                                                                                             |
| `randomSeed`        | Mistral                                                                                              | Integer                                     | The seed to use for random sampling. If set, different calls with the same parameters and the same seed value will generate deterministic results.                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `promptMode`        | Mistral                                                                                              | String                                      | Lets you toggle between the reasoning mode and no system prompt. When set to `reasoning`, the default system prompt for reasoning models is used. For more information, see Mistral's [Reasoning](https://docs.mistral.ai/capabilities/reasoning) documentation.                                                                                                                                                                                                                                                                                                               |
| `safePrompt`        | Mistral                                                                                              | Boolean                                     | Specifies whether to inject a safety prompt before all conversations. The safety prompt is used to enforce guardrails and protect against harmful content. For more information, see Mistral's [Moderation & Guardarailing](https://docs.mistral.ai/capabilities/guardrailing) documentation.                                                                                                                                                                                                                                                                                  |
| `enableSearch`      | Alibaba (DashScope)                                                                                  | Boolean                                     | Specifies whether to enable web search functionality. For more information, see Alibaba's [Web search](https://www.alibabacloud.com/help/en/model-studio/web-search?spm=a2c63.p38356.0.i14) documentation.                                                                                                                                                                                                                                                                                                                                                                     |
| `enableThinking`    | Alibaba (DashScope)                                                                                  | Boolean                                     | Specifies whether to enable thinking mode when using a hybrid thinking model. For more information, see Alibaba's documentation on [Deep thinking](https://www.alibabacloud.com/help/en/model-studio/deep-thinking?spm=a2c63.p38356.0.i11).                                                                                                                                                                                                                                                                                                                                    |
| `repetitionPenalty` | OpenRouter                                                                                           | Double                                      | Penalizes token repetition. Next-token probabilities for tokens that already appeared in the output are divided by the value of `repetitionPenalty`, which makes them less likely to appear again if `repetitionPenalty > 1`. Takes a value greater than 0.0 and lower than or equal to 2.0.                                                                                                                                                                                                                                                                                   |
| `minP`              | OpenRouter                                                                                           | Double                                      | Filters out tokens whose relative probability to the most likely token is below the defined `minP` value. Takes a value in the range of 0.0–0.1.                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `topA`              | OpenRouter                                                                                           | Double                                      | Dynamically adjusts the sampling window based on model confidence. If the model is confident (there are dominant high-probability next tokens), it keeps the sampling window limited to a few top tokens. If the confidence is low (there are many tokens with similar probabilities), keeps more tokens in the sampling window. Takes a value in the range of 0.0–0.1 (inclusive). Higher value means greater dynamic adaptation.                                                                                                                                             |
| `transforms`        | OpenRouter                                                                                           | List&lt;String&gt;                          | List of context transforms. Defines how context is transformed when it exceeds the model's token limit. The default transformation is `middle-out` which truncates from the middle of the prompt. Use empty list for no transformations. For more information, see [Message Transforms](https://openrouter.ai/docs/guides/features/message-transforms) in OpenRouter documentation.                                                                                                                                                                                            |
| `models`            | OpenRouter                                                                                           | List&lt;String&gt;                          | List of allowed models for the request.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `route`             | OpenRouter                                                                                           | String                                      | Request routing strategy to use.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `provider`          | OpenRouter                                                                                           | ProviderPreferences                         | Includes a range of parameters that let you explicitly control how OpenRouter chooses which LLM provider to use. For more information, see the API documentation on [ProviderPreferences](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter.models/-provider-preferences/index.html).                                                                                                                                                                                            |

The following example shows defined OpenRouter LLM parameters using the provider-specific `OpenRouterParams` class:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams
-->
```kotlin
val openRouterParams = OpenRouterParams(
    temperature = 0.7,
    maxTokens = 500,
    frequencyPenalty = 0.5,
    presencePenalty = 0.5,
    topP = 0.9,
    topK = 40,
    repetitionPenalty = 1.1,
    models = listOf("anthropic/claude-3-opus", "anthropic/claude-3-sonnet"),
    transforms = listOf("middle-out")
)
```
<!--- KNIT example-llm-parameters-02.kt -->

## Usage examples

### Basic usage

<!--- INCLUDE
import ai.koog.prompt.params.LLMParams
-->
```kotlin
// A basic set of parameters with limited length 
val basicParams = LLMParams(
    temperature = 0.7,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto
)
```
<!--- KNIT example-llm-parameters-03.kt -->

### Reasoning control

You implement reasoning control through provider-specific parameters that control model reasoning. 
When using the OpenAI Chat API and models that support reasoning, use the `reasoningEffort` parameter 
to control how many reasoning tokens the model generates before providing a response:

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
-->
```kotlin
val openAIReasoningEffortParams = OpenAIChatParams(
    reasoningEffort = ReasoningEffort.MEDIUM
)
```
<!--- KNIT example-llm-parameters-04.kt -->

In addition, when using the OpenAI Responses API in a stateless mode, you keep an encrypted history of reasoning items and send it to the model in every conversation turn. The encryption is done on the OpenAI side, and you need to request encrypted reasoning tokens by setting the `include` parameter in your requests to `reasoning.encrypted_content`. 
You can then pass the encrypted reasoning tokens back to the model in the next conversation turns.

<!--- INCLUDE
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
-->
```kotlin
val openAIStatelessReasoningParams = OpenAIResponsesParams(
    include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT)
)
```
<!--- KNIT example-llm-parameters-05.kt -->

### Custom parameters

To add custom parameters that may be provider specific and not supported in Koog out of the box, use the `additionalProperties` property as shown in the example below. 

<!--- INCLUDE
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.additionalPropertiesOf
-->
```kotlin
// Add custom parameters for specific model providers
val customParams = LLMParams(
    additionalProperties = additionalPropertiesOf(
        "top_p" to 0.95,
        "frequency_penalty" to 0.5,
        "presence_penalty" to 0.5
    )
)
```
<!--- KNIT example-llm-parameters-06.kt -->

### Setting and overriding parameters

The code sample below shows how you can define a set of LLM parameters that you may want to use primarily,
then create another set by partially overriding values from the original set and adding new values to it. 
This lets you define parameters that are common to most requests but also add more specific parameter combinations without having to repeat the common parameters. 

<!--- INCLUDE
import ai.koog.prompt.params.LLMParams
-->
```kotlin
// Define default parameters
val defaultParams = LLMParams(
    temperature = 0.7,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto
)

// Create parameters with some overrides, using defaults for the rest
val overrideParams = LLMParams(
    temperature = 0.2,
    numberOfChoices = 3
).default(defaultParams)
```
<!--- KNIT example-llm-parameters-07.kt -->

The values in the resulting `overrideParams` set are equivalent to the following:

<!--- INCLUDE
import ai.koog.prompt.params.LLMParams
-->
```kotlin
val overrideParams = LLMParams(
    temperature = 0.2,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto,
    numberOfChoices = 3
)
```
<!--- KNIT example-llm-parameters-08.kt -->
