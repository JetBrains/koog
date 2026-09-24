# LLM parameters

This page provides details about LLM parameters in the Koog agentic framework. LLM parameters let you control and customize the behavior of language models.

## Overview

LLM parameters are configuration options that let you fine-tune how language models generate responses. These parameters control aspects like response randomness, length, format, and tool usage. By adjusting the parameters, you optimize model behavior for different use cases, from creative content generation to deterministic structured outputs.

In Koog, the `LLMParams` class incorporates LLM parameters and provides a consistent interface for configuring language model behavior. You can use LLM parameters in the following ways:

- When creating a prompt:

```
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

```
Prompt prompt = Prompt.builder("dev-assistant")
    .withParams(new LLMParams(
        0.7,         // temperature
        500,         // maxTokens
        1,           // numberOfChoices
        null,        // speculation
        null,        // schema
        LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
        null,        // user
        null         // additionalProperties
    ))
    .system("You are a helpful assistant.")
    .user("Tell me about Kotlin")
    .build();
```

For more information about prompt creation, see [Prompts](../prompts/prompt-creation/).

- When creating a subgraph:

```
val processQuery by subgraphWithTask<String, String>(
    tools = listOf(searchTool, calculatorTool, weatherTool),
    llmModel = OpenAIModels.Chat.GPT4o,
    llmParams = LLMParams(
        temperature = 0.7,
        maxTokens = 500
    ),
    parallelTools = false,
    assistantResponseRepeatMax = 3,
) { userQuery ->
    """
    You are a helpful assistant that can answer questions about various topics.
    Please help with the following query:
    $userQuery
    """
}
```

```

```

For more information about existing subgraph types in Koog, see [Predefined subgraphs](../nodes-and-components/#predefined-subgraphs). To learn how to create and implement your own subgraphs, see [Custom subgraphs](../custom-subgraphs/).

- When updating a prompt in an LLM write session:

```
llm.writeSession {
    changeLLMParams(
        LLMParams(
            temperature = 0.7,
            maxTokens = 500
        )
    )
}
```

```

```

For more information about sessions, see [LLM sessions and manual history management](../sessions/).

## LLM parameter reference

The following table provides a reference of LLM parameters included in the `LLMParams` class and supported by all LLM providers that are available in Koog out of the box. For a list of parameters that are specific to some providers, see [Provider-specific parameters](#provider-specific-parameters).

| Parameter              | Type                      | Description                                                                                                                                                                                     |
| ---------------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `temperature`          | Double                    | Controls randomness in the output. Higher values, such as 0.7–1.0, produce more diverse and creative responses, while lower values produce more deterministic and focused responses.            |
| `maxTokens`            | Integer                   | Maximum number of tokens to generate in the response. Useful for controlling response length.                                                                                                   |
| `numberOfChoices`      | Integer                   | Number of alternative responses to generate. Must be greater than 0.                                                                                                                            |
| `speculation`          | String                    | A speculative configuration string that influences model behavior, designed to enhance result speed and accuracy. Supported only by certain models, but may greatly improve speed and accuracy. |
| `schema`               | Schema                    | Defines the structure for the model's response format, enabling structured outputs like JSON. For more information, see [Schema](#schema).                                                      |
| `toolChoice`           | ToolChoice                | Controls tool calling behavior of the language model. For more information, see [Tool choice](#tool-choice).                                                                                    |
| `user`                 | String                    | Identifier for the user making the request, which can be used for tracking purposes.                                                                                                            |
| `additionalProperties` | Map\<String, JsonElement> | Additional properties that can be used to store custom parameters specific to certain model providers.                                                                                          |

For a list of default values for each parameter, see the corresponding LLM provider documentation:

- [OpenAI Chat](https://platform.openai.com/docs/api-reference/chat/create)
- [OpenAI Responses](https://platform.openai.com/docs/api-reference/responses/create)
- [Google](https://ai.google.dev/api/generate-content#generationconfig) β
- [Anthropic](https://platform.claude.com/docs/en/api/messages/create)
- [Mistral](https://docs.mistral.ai/api/#operation/chatCompletions) β
- [DeepSeek](https://api-docs.deepseek.com/api/create-chat-completion#request) β
- [OpenRouter](https://openrouter.ai/docs/api/reference/parameters)
- Alibaba β ([DashScope](https://www.alibabacloud.com/help/en/model-studio/qwen-api-reference))
- [Ollama](https://docs.ollama.com/api/openai-compatibility)

## Schema

The `Schema` interface defines the structure for the model's response format. Koog supports JSON schemas, as described in the sections below.

### JSON schemas

JSON schemas let you request structured JSON data from language models. Koog supports the following two types of JSON schemas:

1. **Basic JSON Schema** (`LLMParams.Schema.JSON.Basic`): Used for basic JSON processing capabilities. This format primarily focuses on nested data definitions without advanced JSON Schema functionalities.

```
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

```
// Create parameters with a basic JSON schema
LLMParams jsonParams = new LLMParams(
    0.2,         // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    new LLMParams.Schema.JSON.Basic(
        "PersonInfo",
        new JsonObject(Map.of(
            "type", new JsonPrimitive("object"),
            "properties", new JsonObject(Map.of(
                "name", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                "age", new JsonObject(Map.of("type", new JsonPrimitive("number"))),
                "skills", new JsonObject(Map.of(
                    "type", new JsonPrimitive("array"),
                    "items", new JsonObject(Map.of("type", new JsonPrimitive("string")))
                ))
            )),
            "additionalProperties", new JsonPrimitive(false),
            "required", new JsonArray(List.of(
                new JsonPrimitive("name"),
                new JsonPrimitive("age"),
                new JsonPrimitive("skills")
            ))
        ))
    ),
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,        // user
    null         // additionalProperties
);
```

2. **Standard JSON Schema** (`LLMParams.Schema.JSON.Standard`): Represents a standard JSON schema according to [json-schema.org](https://json-schema.org/). This format is a proper subset of the official JSON Schema specification. Note that the flavor across different LLM providers might vary, since not all of them support full JSON schemas.

```
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

```
// Create parameters with a standard JSON schema
LLMParams standardJsonParams = new LLMParams(
    0.2,         // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    new LLMParams.Schema.JSON.Standard(
        "ProductCatalog",
        new JsonObject(Map.of(
            "type", new JsonPrimitive("object"),
            "properties", new JsonObject(Map.of(
                "products", new JsonObject(Map.of(
                    "type", new JsonPrimitive("array"),
                    "items", new JsonObject(Map.of(
                        "type", new JsonPrimitive("object"),
                        "properties", new JsonObject(Map.of(
                            "id", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                            "name", new JsonObject(Map.of("type", new JsonPrimitive("string"))),
                            "price", new JsonObject(Map.of("type", new JsonPrimitive("number"))),
                            "description", new JsonObject(Map.of("type", new JsonPrimitive("string")))
                        )),
                        "additionalProperties", new JsonPrimitive(false),
                        "required", new JsonArray(List.of(
                            new JsonPrimitive("id"),
                            new JsonPrimitive("name"),
                            new JsonPrimitive("price"),
                            new JsonPrimitive("description")
                        ))
                    ))
                ))
            )),
            "additionalProperties", new JsonPrimitive(false),
            "required", new JsonArray(List.of(new JsonPrimitive("products")))
        ))
    ),
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,        // user
    null         // additionalProperties
);
```

## Tool choice

The `ToolChoice` class controls how the language model uses tools. It provides the following options:

- `LLMParams.ToolChoice.Named`: the language model calls the specified tool. Takes the `name` string argument that represents the name of the tool to call.
- `LLMParams.ToolChoice.All`: the language model calls all tools.
- `LLMParams.ToolChoice.None`: the language model does not call tools and only generates text.
- `LLMParams.ToolChoice.Auto`: the language model automatically decides whether to call tools and which tool to call.
- `LLMParams.ToolChoice.Required`: the language model calls at least one tool.

Here is an example of using the `LLMParams.ToolChoice.Named` class to call a specific tool:

```
val specificToolParams = LLMParams(
    toolChoice = LLMParams.ToolChoice.Named(name = "calculator")
)
```

```
LLMParams specificToolParams = new LLMParams(
    null,        // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    new LLMParams.ToolChoice.Named("calculator"), // toolChoice
    null,        // user
    null         // additionalProperties
);
```

## Provider-specific parameters

Koog supports provider-specific parameters for some LLM providers. These parameters extend the base `LLMParams` class and add provider-specific functionality. The following classes include parameters that are specific per provider:

- `OpenAIChatParams`: Parameters specific to the OpenAI Chat Completions API.
- `OpenAIResponsesParams`: Parameters specific to the OpenAI Responses API.
- `GoogleParams`: Parameters specific to Google models.
- `AnthropicParams`: Parameters specific to Anthropic models.
- `MistralAIParams`: Parameters specific to Mistral models.
- `DeepSeekParams`: Parameters specific to DeepSeek models.
- `OpenRouterParams`: Parameters specific to OpenRouter models.
- `DashscopeParams`: Parameters specific to Alibaba models.
- `OllamaParams`: Parameters specific to Ollama models.

Here is the complete reference of provider-specific parameters in Koog:

| Parameter           | Type                   | Description                                                                                                                                                                                                                                                                      |
| ------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `audio`             | OpenAIAudioConfig      | Audio output configuration when using audio-capable models. For more information, see the API documentation for [OpenAIAudioConfig](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioConfig).                               |
| `frequencyPenalty`  | Double                 | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                      |
| `logprobs`          | Boolean                | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                         |
| `parallelToolCalls` | Boolean                | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies.                                                                                                                                     |
| `presencePenalty`   | Double                 | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                              |
| `promptCacheKey`    | String                 | Stable cache key for prompt caching. OpenAI uses it to cache responses for similar requests.                                                                                                                                                                                     |
| `reasoningEffort`   | ReasoningEffort        | Specifies the level of reasoning effort that the model will use. For more information and available values, see the API documentation for [ReasoningEffort](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort).         |
| `safetyIdentifier`  | String                 | A stable and unique user identifier that may be used to detect users who violate OpenAI policies.                                                                                                                                                                                |
| `serviceTier`       | ServiceTier            | OpenAI processing tier selection that lets you prioritize performance over cost or vice versa. For more information, see the API documentation for [ServiceTier](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.ServiceTier).        |
| `stop`              | List<String>           | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                              |
| `store`             | Boolean                | If `true`, the provider may store outputs for later retrieval.                                                                                                                                                                                                                   |
| `topLogprobs`       | Integer                | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                        |
| `topP`              | Double                 | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0. |
| `webSearchOptions`  | OpenAIWebSearchOptions | Configure web search tool usage (if supported). For more information, see the API documentation for [OpenAIWebSearchOptions](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.OpenAIWebSearchOptions).                                 |

| Parameter           | Type                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `background`        | Boolean             | Run the response in the background.                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `include`           | List<OpenAIInclude> | Additional data to include in the model's response, such as sources of web search tool call or search results of a file search tool call. For detailed reference information, see [OpenAIInclude](api:prompt-executor-openai-client::ai.koog.prompt.executor.clients.openai.models.OpenAIInclude) in the Koog API reference. To learn more about the `include` parameter, see [OpenAI's documentation](https://platform.openai.com/docs/api-reference/responses/create#responses-create-include). |
| `logprobs`          | Boolean             | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `maxToolCalls`      | Integer             | Maximum total number of built-in tool calls allowed in this response. Takes a value equal to or greater than `0`.                                                                                                                                                                                                                                                                                                                                                                                 |
| `parallelToolCalls` | Boolean             | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies.                                                                                                                                                                                                                                                                                                                                                      |
| `promptCacheKey`    | String              | Stable cache key for prompt caching. OpenAI uses it to cache responses for similar requests.                                                                                                                                                                                                                                                                                                                                                                                                      |
| `reasoning`         | ReasoningConfig     | Reasoning configuration for reasoning-capable models. For more information, see the API documentation for [ReasoningConfig](api:prompt-executor-openai-client::ai.koog.prompt.executor.clients.openai.models.ReasoningConfig).                                                                                                                                                                                                                                                                    |
| `safetyIdentifier`  | String              | A stable and unique user identifier that may be used to detect users who violate OpenAI policies.                                                                                                                                                                                                                                                                                                                                                                                                 |
| `serviceTier`       | ServiceTier         | OpenAI processing tier selection that lets you prioritize performance over cost or vice versa. For more information, see the API documentation for [ServiceTier](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.ServiceTier).                                                                                                                                                                                                                         |
| `store`             | Boolean             | If `true`, the provider may store outputs for later retrieval.                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `topLogprobs`       | Integer             | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                                                                                                                                                                                                                                         |
| `topP`              | Double              | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.                                                                                                                                                                                                                  |
| `truncation`        | Truncation          | Truncation strategy when nearing the context window. For more information, see the API documentation for [Truncation](api:prompt-executor-openai-client::ai.koog.prompt.executor.clients.openai.models.Truncation).                                                                                                                                                                                                                                                                               |

| Parameter        | Type                 | Description                                                                                                                                                                                                                                                                          |
| ---------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `thinkingConfig` | GoogleThinkingConfig | Controls whether the model should expose its chain-of-thought and how many tokens it may spend on it. For more information, see the API reference for [GoogleThinkingConfig](api:prompt-executor-google-client::ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig). |
| `topK`           | Integer              | Number of top tokens to consider when generating the output. Takes a value greater than or equal to 0 (provider-specific minimums may apply).                                                                                                                                        |
| `topP`           | Double               | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.     |

| Parameter       | Type                                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| --------------- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `container`     | String                                | Container identifier for reuse across requests. Containers are used by Anthropic's code execution tool to provide a secure and containerized code execution environment. By providing the container identifier from a previous response, you can reuse containers across multiple requests, which preserves created files between requests. For more information, see [Containers](https://platform.claude.com/docs/en/agents-and-tools/tool-use/code-execution-tool#containers) in Anthropic's documentation. |
| `mcpServers`    | List<AnthropicMCPServerURLDefinition> | Definitions of MCP servers to be used in the request. Supports at most 20 servers. For more information, see the API reference for [AnthropicMCPServerURLDefinition](api:prompt-executor-anthropic-client::ai.koog.prompt.executor.clients.anthropic.models.AnthropicMCPServerURLDefinition).                                                                                                                                                                                                                  |
| `serviceTier`   | ServiceTier                           | OpenAI processing tier selection that lets you prioritize performance over cost or vice versa. For more information, see the API documentation for [ServiceTier](api:prompt-executor-openai-client-base::ai.koog.prompt.executor.clients.openai.base.models.ServiceTier).                                                                                                                                                                                                                                      |
| `stopSequences` | List<String>                          | Custom text sequences that cause the model to stop generating content. If matched, the value of `stop_reason` in the response is `stop_sequence`.                                                                                                                                                                                                                                                                                                                                                              |
| `thinking`      | AnthropicThinking                     | Configuration for activating Claude's extended thinking. When activated, responses also include thinking content blocks. For more information, see the API reference for [AnthropicThinking](api:prompt-executor-anthropic-client::ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking).                                                                                                                                                                                                        |
| `topK`          | Integer                               | Number of top tokens to consider when generating the output. Takes a value greater than or equal to 0 (provider-specific minimums may apply).                                                                                                                                                                                                                                                                                                                                                                  |
| `topP`          | Double                                | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.                                                                                                                                                                                                                               |

| Parameter           | Type         | Description                                                                                                                                                                                                                                                                                   |
| ------------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `frequencyPenalty`  | Double       | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                                   |
| `parallelToolCalls` | Boolean      | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies.                                                                                                                                                  |
| `presencePenalty`   | Double       | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                                           |
| `promptMode`        | String       | Lets you toggle between the reasoning mode and no system prompt. When set to `reasoning`, the default system prompt for reasoning models is used. For more information, see Mistral's [Reasoning](https://docs.mistral.ai/capabilities/reasoning) documentation.                              |
| `randomSeed`        | Integer      | The seed to use for random sampling. If set, different calls with the same parameters and the same seed value will generate deterministic results.                                                                                                                                            |
| `safePrompt`        | Boolean      | Specifies whether to inject a safety prompt before all conversations. The safety prompt is used to enforce guardrails and protect against harmful content. For more information, see Mistral's [Moderation & Guardarailing](https://docs.mistral.ai/capabilities/guardrailing) documentation. |
| `stop`              | List<String> | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                                           |
| `topP`              | Double       | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.              |

| Parameter          | Type         | Description                                                                                                                                                                                                                                                                      |
| ------------------ | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `frequencyPenalty` | Double       | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                      |
| `logprobs`         | Boolean      | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                         |
| `presencePenalty`  | Double       | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                              |
| `stop`             | List<String> | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                              |
| `topLogprobs`      | Integer      | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                        |
| `topP`             | Double       | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0. |

| Parameter           | Type                | Description                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `frequencyPenalty`  | Double              | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                                                                                                                                                                        |
| `logprobs`          | Boolean             | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                                                                                                                                                                           |
| `minP`              | Double              | Filters out tokens whose relative probability to the most likely token is below the defined `minP` value. Takes a value in the range of 0.0–0.1.                                                                                                                                                                                                                                                                                   |
| `models`            | List<String>        | List of allowed models for the request.                                                                                                                                                                                                                                                                                                                                                                                            |
| `presencePenalty`   | Double              | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                                                                                                                                                                                |
| `provider`          | ProviderPreferences | Includes a range of parameters that let you explicitly control how OpenRouter chooses which LLM provider to use. For more information, see the API documentation on [ProviderPreferences](api:prompt-executor-openrouter-client::ai.koog.prompt.executor.clients.openrouter.models.ProviderPreferences).                                                                                                                           |
| `repetitionPenalty` | Double              | Penalizes token repetition. Next-token probabilities for tokens that already appeared in the output are divided by the value of `repetitionPenalty`, which makes them less likely to appear again if `repetitionPenalty > 1`. Takes a value greater than 0.0 and lower than or equal to 2.0.                                                                                                                                       |
| `route`             | String              | Request routing strategy to use.                                                                                                                                                                                                                                                                                                                                                                                                   |
| `stop`              | List<String>        | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                                                                                                                                                                                |
| `topA`              | Double              | Dynamically adjusts the sampling window based on model confidence. If the model is confident (there are dominant high-probability next tokens), it keeps the sampling window limited to a few top tokens. If the confidence is low (there are many tokens with similar probabilities), keeps more tokens in the sampling window. Takes a value in the range of 0.0–0.1 (inclusive). Higher value means greater dynamic adaptation. |
| `topK`              | Integer             | Number of top tokens to consider when generating the output. Takes a value greater than or equal to 0 (provider-specific minimums may apply).                                                                                                                                                                                                                                                                                      |
| `topLogprobs`       | Integer             | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                                                                                                                                                                          |
| `topP`              | Double              | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0.                                                                                                                                                   |
| `transforms`        | List<String>        | List of context transforms. Defines how context is transformed when it exceeds the model's token limit. The default transformation is `middle-out` which truncates from the middle of the prompt. Use empty list for no transformations. For more information, see [Message Transforms](https://openrouter.ai/docs/guides/features/message-transforms) in OpenRouter documentation.                                                |

| Parameter           | Type         | Description                                                                                                                                                                                                                                                                      |
| ------------------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `enableSearch`      | Boolean      | Specifies whether to enable web search functionality. For more information, see Alibaba's [Web search](https://www.alibabacloud.com/help/en/model-studio/web-search?spm=a2c63.p38356.0.i14) documentation.                                                                       |
| `enableThinking`    | Boolean      | Specifies whether to enable thinking mode when using a hybrid thinking model. For more information, see Alibaba's documentation on [Deep thinking](https://www.alibabacloud.com/help/en/model-studio/deep-thinking?spm=a2c63.p38356.0.i11).                                      |
| `frequencyPenalty`  | Double       | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0.                                                                                      |
| `logprobs`          | Boolean      | If `true`, includes log-probabilities for output tokens.                                                                                                                                                                                                                         |
| `parallelToolCalls` | Boolean      | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies.                                                                                                                                     |
| `presencePenalty`   | Double       | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0.                                                                              |
| `stop`              | List<String> | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`.                              |
| `topLogprobs`       | Integer      | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`.                                                                                                                                        |
| `topP`              | Double       | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0. |

| Parameter | Type    | Description                                                                                                                                                                                                                                                           |
| --------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `think`   | Boolean | Configuration for activating Ollama extended thinking. When activated, responses also include thinking content blocks. For more information, see the API reference for [Ollama thinking](https://docs.ollama.com/capabilities/thinking#enable-thinking-in-api-calls). |

The following example shows defined OpenRouter LLM parameters using the provider-specific `OpenRouterParams` class:

```
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

```
OpenRouterParams openRouterParams = new OpenRouterParams(
    0.7,         // temperature
    500,         // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    null,        // toolChoice
    null,        // user
    null,        // additionalProperties
    0.5,         // frequencyPenalty
    null,        // logprobs
    null,        // minP
    Arrays.asList("anthropic/claude-3-opus", "anthropic/claude-3-sonnet"), // models
    0.5,         // presencePenalty
    null,        // provider
    1.1,         // repetitionPenalty
    null,        // route
    null,        // stop
    null,        // topA
    40,          // topK
    null,        // topLogprobs
    0.9,         // topP
    Arrays.asList("middle-out") // transforms
);
```

## Usage examples

### Basic usage

```
// A basic set of parameters with limited length
val basicParams = LLMParams(
    temperature = 0.7,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto
)
```

```
// A basic set of parameters with limited length
LLMParams basicParams = new LLMParams(
    0.7,         // temperature
    150,         // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,        // user
    null         // additionalProperties
);
```

### Reasoning control

You implement reasoning control through provider-specific parameters that control model reasoning. When using the OpenAI Chat API and models that support reasoning, use the `reasoningEffort` parameter to control how many reasoning tokens the model generates before providing a response:

```
val openAIReasoningEffortParams = OpenAIChatParams(
    reasoningEffort = ReasoningEffort.MEDIUM
)
```

```
OpenAIChatParams openAIReasoningEffortParams = new OpenAIChatParams(
    null,        // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    null,        // toolChoice
    null,        // user
    null,        // additionalProperties
    null,        // audio
    null,        // frequencyPenalty
    null,        // logprobs
    null,        // parallelToolCalls
    null,        // presencePenalty
    null,        // promptCacheKey
    ReasoningEffort.MEDIUM, // reasoningEffort
    null,        // safetyIdentifier
    null,        // serviceTier
    null,        // stop
    null,        // store
    null,        // topLogprobs
    null,        // topP
    null         // webSearchOptions
);
```

In addition, when using the OpenAI Responses API in a stateless mode, you keep an encrypted history of reasoning items and send it to the model in every conversation turn. The encryption is done on the OpenAI side, and you need to request encrypted reasoning tokens by setting the `include` parameter in your requests to `reasoning.encrypted_content`. You can then pass the encrypted reasoning tokens back to the model in the next conversation turns.

```
val openAIStatelessReasoningParams = OpenAIResponsesParams(
    include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT)
)
```

```
OpenAIResponsesParams openAIStatelessReasoningParams = new OpenAIResponsesParams(
    null,        // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    null,        // toolChoice
    null,        // user
    null,        // additionalProperties
    null,        // background
    Arrays.asList(OpenAIInclude.REASONING_ENCRYPTED_CONTENT), // include
    null,        // logprobs
    null,        // maxToolCalls
    null,        // parallelToolCalls
    null,        // promptCacheKey
    null,        // reasoning
    null,        // safetyIdentifier
    null,        // serviceTier
    null,        // store
    null,        // topLogprobs
    null,        // topP
    null         // truncation
);
```

### Custom parameters

To add custom parameters that may be provider specific and not supported in Koog out of the box, use the `additionalProperties` property as shown in the example below.

```
// Add custom parameters for specific model providers
val customParams = LLMParams(
    additionalProperties = additionalPropertiesOf(
        "top_p" to 0.95,
        "frequency_penalty" to 0.5,
        "presence_penalty" to 0.5
    )
)
```

```
// Add custom parameters for specific model providers
LLMParams customParams = new LLMParams(
    null,        // temperature
    null,        // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    null,        // toolChoice
    null,        // user
    AdditionalPropertiesKt.additionalPropertiesOf(
        "top_p", 0.95,
        "frequency_penalty", 0.5,
        "presence_penalty", 0.5
    )
);
```

### Setting and overriding parameters

The code sample below shows how you can define a set of LLM parameters that you may want to use primarily, then create another set by partially overriding values from the original set and adding new values to it. This lets you define parameters that are common to most requests but also add more specific parameter combinations without having to repeat the common parameters.

```
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

```
// Define default parameters
LLMParams defaultParams = new LLMParams(
    0.7,         // temperature
    150,         // maxTokens
    1,           // numberOfChoices
    null,        // speculation
    null,        // schema
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,        // user
    null         // additionalProperties
);

// Create parameters with some overrides, using defaults for the rest
LLMParams overrideParams = new LLMParams(
    0.2,         // temperature
    null,        // maxTokens
    3,           // numberOfChoices
    null,        // speculation
    null,        // schema
    null,        // toolChoice
    null,        // user
    null         // additionalProperties
).applyDefaults(defaultParams);
```

The values in the resulting `overrideParams` set are equivalent to the following:

```
val overrideParams = LLMParams(
    temperature = 0.2,
    maxTokens = 150,
    toolChoice = LLMParams.ToolChoice.Auto,
    numberOfChoices = 3
)
```

```
LLMParams overrideParams = new LLMParams(
    0.2,         // temperature
    150,         // maxTokens
    3,           // numberOfChoices
    null,        // speculation
    null,        // schema
    LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
    null,        // user
    null         // additionalProperties
);
```
