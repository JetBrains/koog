# --8<-- [start:heading]
|Parameter|Type| Description |
|---------|----|-------------|
# --8<-- [end:heading]

# --8<-- [start:topP]
| `topP` | Double | Also referred to as nucleus sampling. Creates a subset of next tokens by adding tokens with the highest probability values to the subset until the sum of their probabilities reaches the specified `topP` value. Takes a value greater than 0.0 and lower than or equal to 1.0. |
# --8<-- [end:topP]

# --8<-- [start:logprobs]
| `logprobs` | Boolean | If `true`, includes log-probabilities for output tokens. |
# --8<-- [end:logprobs]

# --8<-- [start:topLogprobs]
| `topLogprobs` | Integer | Number of top most likely tokens per position. Takes a value in the range of 0–20. Requires the `logprobs` parameter to be set to `true`. |
# --8<-- [end:topLogprobs]

# --8<-- [start:frequencyPenalty]
| `frequencyPenalty` | Double | Penalizes frequent tokens to reduce repetition. Higher `frequencyPenalty` values result in larger variations of phrasing and reduced repetition. Takes a value in the range of -2.0 to 2.0. |
# --8<-- [end:frequencyPenalty]

# --8<-- [start:presencePenalty]
| `presencePenalty` | Double | Prevents the model from reusing tokens that have already been included in the output. Higher values encourage the introduction of new tokens and topics. Takes a value in the range of -2.0 to 2.0. |
# --8<-- [end:presencePenalty]

# --8<-- [start:stop]
| `stop` | List&lt;String&gt; | Strings that signal to the model that it should stop generating content when it encounters any of them. For example, to make the model stop generating content when it produces two newlines, specify the stop sequence as `stop = listOf("/n/n")`. |
# --8<-- [end:stop]

# --8<-- [start:parallelToolCalls]
| `parallelToolCalls` | Boolean | If `true`, multiple tool calls can run in parallel. Particularly applicable to custom nodes or LLM interactions outside of agent strategies. |
# --8<-- [end:parallelToolCalls]

# --8<-- [start:promptCacheKey]
| `promptCacheKey` | String | Stable cache key for prompt caching. OpenAI uses it to cache responses for similar requests. |
# --8<-- [end:promptCacheKey]

# --8<-- [start:safetyIdentifier]
| `safetyIdentifier` | String | A stable and unique user identifier that may be used to detect users who violate OpenAI policies. |
# --8<-- [end:safetyIdentifier]

# --8<-- [start:serviceTier]
| `serviceTier` | ServiceTier | OpenAI processing tier selection that lets you prioritize performance over cost or vice versa. For more information, see the API documentation for [ServiceTier](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-service-tier/index.html). |
# --8<-- [end:serviceTier]

# --8<-- [start:store]
| `store` | Boolean | If `true`, the provider may store outputs for later retrieval. |
# --8<-- [end:store]

# --8<-- [start:audio]
| `audio` | OpenAIAudioConfig | Audio output configuration when using audio-capable models. For more information, see the API documentation for [OpenAIAudioConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-open-a-i-audio-config/index.html). |
# --8<-- [end:audio]

# --8<-- [start:reasoningEffort]
| `reasoningEffort` | ReasoningEffort | Specifies the level of reasoning effort that the model will use. For more information and available values, see the API documentation for [ReasoningEffort](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-reasoning-effort/index.html). |
# --8<-- [end:reasoningEffort]

# --8<-- [start:webSearchOptions]
| `webSearchOptions` | OpenAIWebSearchOptions | Configure web search tool usage (if supported). For more information, see the API documentation for [OpenAIWebSearchOptions](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client-base/ai.koog.prompt.executor.clients.openai.base.models/-open-a-i-web-search-options/index.html). |
# --8<-- [end:webSearchOptions]

# --8<-- [start:background]
| `background` | Boolean | Run the response in the background. |
# --8<-- [end:background]

# --8<-- [start:include]
| `include` | List&lt;OpenAIInclude&gt; | Additional data to include in the model's response, such as sources of web search tool call or search results of a file search tool call. For detailed reference information, see [OpenAIInclude](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-open-a-i-include/index.html) in the Koog API reference. To learn more about the `include` parameter, see [OpenAI's documentation](https://platform.openai.com/docs/api-reference/responses/create#responses-create-include). |
# --8<-- [end:include]

# --8<-- [start:maxToolCalls]
| `maxToolCalls` | Integer | Maximum total number of built-in tool calls allowed in this response. Takes a value equal to or greater than `0`. |
# --8<-- [end:maxToolCalls]

# --8<-- [start:reasoning]
| `reasoning` | ReasoningConfig | Reasoning configuration for reasoning-capable models. For more information, see the API documentation for [ReasoningConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-reasoning-config/index.html). |
# --8<-- [end:reasoning]

# --8<-- [start:truncation]
| `truncation` | Truncation | Truncation strategy when nearing the context window. For more information, see the API documentation for [Truncation](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openai-client/ai.koog.prompt.executor.clients.openai.models/-truncation/index.html). |
# --8<-- [end:truncation]

# --8<-- [start:topK]
| `topK` | Integer | Number of top tokens to consider when generating the output. Takes a value greater than or equal to 0 (provider-specific minimums may apply). |
# --8<-- [end:topK]

# --8<-- [start:repetitionPenalty]
| `repetitionPenalty` | Double | Penalizes token repetition. Next-token probabilities for tokens that already appeared in the output are divided by the value of `repetitionPenalty`, which makes them less likely to appear again if `repetitionPenalty > 1`. Takes a value greater than 0.0 and lower than or equal to 2.0. |
# --8<-- [end:repetitionPenalty]

# --8<-- [start:minP]
| `minP` | Double | Filters out tokens whose relative probability to the most likely token is below the defined `minP` value. Takes a value in the range of 0.0–0.1. |
# --8<-- [end:minP]

# --8<-- [start:topA]
| `topA` | Double | Dynamically adjusts the sampling window based on model confidence. If the model is confident (there are dominant high-probability next tokens), it keeps the sampling window limited to a few top tokens. If the confidence is low (there are many tokens with similar probabilities), keeps more tokens in the sampling window. Takes a value in the range of 0.0–0.1 (inclusive). Higher value means greater dynamic adaptation. |
# --8<-- [end:topA]

# --8<-- [start:transforms]
| `transforms` | List&lt;String&gt; | List of context transforms. Defines how context is transformed when it exceeds the model's token limit. The default transformation is `middle-out` which truncates from the middle of the prompt. Use empty list for no transformations. For more information, see [Message Transforms](https://openrouter.ai/docs/guides/features/message-transforms) in OpenRouter documentation. |
# --8<-- [end:transforms]

# --8<-- [start:models]
| `models` | List&lt;String&gt; | List of allowed models for the request. |
# --8<-- [end:models]

# --8<-- [start:route]
| `route` | String | Request routing strategy to use. |
# --8<-- [end:route]

# --8<-- [start:provider]
| `provider` | ProviderPreferences | Includes a range of parameters that let you explicitly control how OpenRouter chooses which LLM provider to use. For more information, see the API documentation on [ProviderPreferences](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-openrouter-client/ai.koog.prompt.executor.clients.openrouter.models/-provider-preferences/index.html). |
# --8<-- [end:provider]

# --8<-- [start:stopSequences]
| `stopSequences` | List&lt;String&gt; | Custom text sequences that cause the model to stop generating content. If matched, the value of `stop_reason` in the response is `stop_sequence`. |
# --8<-- [end:stopSequences]

# --8<-- [start:container]
| `container`  | String | Container identifier for reuse across requests. Containers are used by Anthropic's code execution tool to provide a secure and containerized code execution environment. By providing the container identifier from a previous response, you can reuse containers across multiple requests, which preserves created files between requests. For more information, see [Containers](https://platform.claude.com/docs/en/agents-and-tools/tool-use/code-execution-tool#containers) in Anthropic's documentation. |
# --8<-- [end:container]

# --8<-- [start:mcpServers]
| `mcpServers` | List&lt;AnthropicMCPServerURLDefinition&gt; | Definitions of MCP servers to be used in the request. Supports at most 20 servers. For more information, see the API reference for [AnthropicMCPServerURLDefinition](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-m-c-p-server-u-r-l-definition/index.html). |
# --8<-- [end:mcpServers]

# --8<-- [start:serviceTier]
| `serviceTier` | AnthropicServiceTier | Determines whether to use priority capacity (if available) or standard capacity for the request. For more information, see the API reference for [AnthropicServiceTier](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-service-tier/index.html) and Anthropic's [Service tiers](https://platform.claude.com/docs/en/api/service-tiers) documentation. |
# --8<-- [end:serviceTier]

# --8<-- [start:thinking]
| `thinking` | AnthropicThinking | Configuration for activating Claude's extended thinking. When activated, responses also include thinking content blocks. For more information, see the API reference for [AnthropicThinking](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-anthropic-client/ai.koog.prompt.executor.clients.anthropic.models/-anthropic-thinking/index.html). |
# --8<-- [end:thinking]

# --8<-- [start:thinkingConfig]
| `thinkingConfig` | GoogleThinkingConfig | Controls whether the model should expose its chain-of-thought and how many tokens it may spend on it. For more information, see the API reference for [GoogleThinkingConfig](https://api.koog.ai/prompt/prompt-executor/prompt-executor-clients/prompt-executor-google-client/ai.koog.prompt.executor.clients.google.models/-google-thinking-config/index.html). |
# --8<-- [end:thinkingConfig]

# --8<-- [start:enableSearch]
| `enableSearch` | Boolean | Specifies whether to enable web search functionality. For more information, see Alibaba's [Web search](https://www.alibabacloud.com/help/en/model-studio/web-search?spm=a2c63.p38356.0.i14) documentation. |
# --8<-- [end:enableSearch]

# --8<-- [start:enableThinking]
| `enableThinking` | Boolean | Specifies whether to enable thinking mode when using a hybrid thinking model. For more information, see Alibaba's documentation on [Deep thinking](https://www.alibabacloud.com/help/en/model-studio/deep-thinking?spm=a2c63.p38356.0.i11). |
# --8<-- [end:enableThinking]

# --8<-- [start:randomSeed]
| `randomSeed` | Integer | The seed to use for random sampling. If set, different calls with the same parameters and the same seed value will generate deterministic results. |
# --8<-- [end:randomSeed]

# --8<-- [start:promptMode]
| `promptMode` | String | Lets you toggle between the reasoning mode and no system prompt. When set to `reasoning`, the default system prompt for reasoning models is used. For more information, see Mistral's [Reasoning](https://docs.mistral.ai/capabilities/reasoning) documentation. |
# --8<-- [end:promptMode]

# --8<-- [start:safePrompt]
| `safePrompt` | Boolean | Specifies whether to inject a safety prompt before all conversations. The safety prompt is used to enforce guardrails and protect against harmful content. For more information, see Mistral's [Moderation & Guardarailing](https://docs.mistral.ai/capabilities/guardrailing) documentation. |
# --8<-- [end:safePrompt]
