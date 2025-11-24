# LLM providers

Koog works with major LLM providers and also supports local models using [Ollama](https://ollama.com/).

| <div style="width:115px">LLM provider</div> | Key capabilities                                                                                                                                                                                      | Choose for                                                              |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| OpenAI (including Azure OpenAI Service)     | Structured output (JSON Schema), tools with tool choice, multiple choices, image input, audio input, document input (PDF), content moderation.                                                        | Wide range of features, advanced models                                 |
| Anthropic                                   | Tools with tool choice, image input, document input (PDF).                                                                                                                                            | High-quality conversations and reasoning                                |
| Google                                      | Multimodal input (image, audio, video), document input (PDF), structured output (JSON Schema), tools, and multiple choices.                                                                           | Strong multimedia processing, large context windows                     |
| DeepSeek                                    | Structured output (JSON Schema), tools with tool choice.                                                                                                                                              | Cost-effective chat and reasoning                                       |
| OpenRouter                                  | One integration with an access to multiple models from multiple providers. Capabilities differ by model and include tools, vision, structured output (JSON Schema), and other depending on the model. | Flexibility, provider comparison, unified API                           |
| Bedrock                                     | AWS-native access to multiple models. Capabilities include tools with tool choice, image input, document input, embeddings, and content moderation, all model-specific.                               | AWS-native integrations, multi-provider access                          |
| Mistral                                     | Structured output (JSON Schema), tools with tool choice, multiple choices, vision (model-specific), embeddings, content moderation.                                                                   | European hosting                                                        |
| Alibaba (Dashscope)                         | Structured output (JSON Schema on specific models), tools with tool choice, multiple choices (model-specific), response streaming, multimodal input (model-specific).                                 | Large context window, cost-efficient Qwen models, OpenAI-compatible API |
| Ollama (local models)                       | Local execution, tools, structured output (JSON Schema), content moderation, vision (model-specific).                                                                                                 | Privacy, local development                                              |

!!! note
    The list above shows each provider's capabilities.
    For the exact capabilities of a specific model within a provider, refer to [Model capabilities](model-capabilities.md).

## Working with providers

Koog provides two ways to work with LLM providers:

- **LLM clients**: that provide direct control over specific providers.
  Each client implements the `LLMClient` interface, handling authentication, request formatting, and response parsing for the provider.
  For details, see [Running prompts with LLM clients](prompt-api.md#running-prompts-with-llm-clients).

- **Prompt executors** that manage the LLM client lifecycle and provide a unified interface across providers.
  They handle failures, retries, and switching between providers.
  You can either create your own executor or use a pre-defined prompt executor for a specific provider.
  For details, see [Running prompts with prompt executors](prompt-api.md#running-prompts-with-prompt-executors).

!!! tip
    Both LLM clients and prompt executors provide the functionality supported by the corresponding provider.
    If a provider supports response streaming, multiple choices generation, or content moderation, 
    both the LLM client and prompt executor for that provider will enable these features.
    The availability of specific functionalities depends on the LLM provider's capabilities.

## Next steps

- [Create and run an agent](getting-started.md) with a specific provider.
- Learn more about [prompts](prompt-api.md) and [how to choose between LLM clients and prompt executors](prompt-api.md#choosing-between-llm-clients-and-prompt-executors).



