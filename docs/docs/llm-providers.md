# LLM providers

Koog works with major LLM providers and also supports local models using [Ollama](https://ollama.com/).
The following providers are currently supported:

| <div style="width:115px">LLM provider</div>                                                                                                                 | Choose for                                                                                                                     |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| [OpenAI](https://platform.openai.com/docs/overview) (including [Azure OpenAI Service](https://azure.microsoft.com/en-us/products/ai-foundry/models/openai)) | Advanced models with a wide range of capabilities.                                                                             |
| [Anthropic](https://www.anthropic.com/)                                                                                                                     | Long contexts and prompt caching.                                                                                              |
| [Google](https://ai.google.dev/)                                                                                                                            | Multimodal processing (audio, video), large contexts.                                                                          |
| [DeepSeek](https://www.deepseek.com/)                                                                                                                       | Cost-effective reasoning and coding.                                                                                           |
| [OpenRouter](https://openrouter.ai/)                                                                                                                        | One integration with an access to multiple models from multiple providers for flexibility, provider comparison, and unified API. |
| [Amazon Bedrock](https://aws.amazon.com/bedrock/)                                                                                                           | AWS-native environment, enterprise security and compliance, multi-provider access.                                             |
| [Mistral](https://mistral.ai/)                                                                                                                              | European data hosting, GDPR compliance.                                                                                        |
| [Alibaba](https://www.alibabacloud.com/en?_p_lc=1) ([DashScope](https://dashscope.aliyun.com/))                                                             | Large contexts and cost-efficient Qwen models.                                                                                 |
| [Ollama](https://ollama.com/)                                                                                                                               | Privacy, local development, offline operation, and no API costs.                                                               |

The table below shows the LLM capabilities that Koog supports and which providers offer these capabilities in their models.
The `*` symbol means that the capability is supported by specific models of the provider.

| <div style="width:115px">LLM capability</div> | OpenAI                       | Anthropic              | Google                               | DeepSeek | OpenRouter       | Amazon Bedrock   | Mistral                | Alibaba (DashScope)        | Ollama (local models) |
|-----------------------------------------------|------------------------------|------------------------|--------------------------------------|----------|------------------|------------------|------------------------|----------------------------|-----------------------|
| Supported input                               | Text, image, audio, document | Text, image, document* | Text, image, audio, video, document* | Text     | Differs by model | Differs by model | Text, image, document* | Text, image, audio, video* | Text, image*          |
| Response streaming                            | ✓                            | ✓                      | ✓                                    | ✓        | ✓                | ✓                | ✓                      | ✓                          | ✓                     |
| Tools                                         | ✓                            | ✓                      | ✓                                    | ✓        | ✓                | ✓*               | ✓                      | ✓                          | ✓                     |
| Tool choice                                   | ✓                            | ✓                      | ✓                                    | ✓        | ✓                | ✓*               | ✓                      | ✓                          | –                     |
| Structured output (JSON Schema)               | ✓                            | –                      | ✓                                    | ✓        | ✓*               | –                | ✓                      | ✓*                         | ✓                     |
| Multiple choices                              | ✓                            | –                      | ✓                                    | –        | ✓*               | ✓*               | ✓                      | ✓*                         | –                     |
| Temperature                                   | ✓                            | ✓                      | ✓                                    | ✓        | ✓                | ✓                | ✓                      | ✓                          | ✓                     |
| Speculation                                   | ✓*                           | –                      | –                                    | –        | ✓*               | –                | ✓*                     | ✓*                         | –                     |
| Content moderation                            | ✓                            | –                      | –                                    | –        | –                | ✓                | ✓                      | –                          | ✓                     |
| Embeddings                                    | ✓                            | –                      | –                                    | –        | –                | ✓                | ✓                      | –                          | ✓                     |
| Prompt caching                                | ✓*                           | ✓                      | –                                    | –        | –                | –                | –                      | –                          | –                     |
| Completion                                    | ✓                            | ✓                      | ✓                                    | ✓        | ✓                | ✓                | ✓                      | ✓                          | ✓                     |
| Local execution                               | –                            | –                      | –                                    | –        | –                | –                | –                      | –                          | ✓                     |

!!! note
    Koog supports the most commonly used capabilities for creating AI agents.
    LLMs from each provider may have additional features that Koog does not currently support.
    To learn more, refer to [Model capabilities](model-capabilities.md).

## Working with providers

Koog lets you work with LLM providers on two levels:

* Using an **LLM client** for direct interaction with a specific provider.
  Each client implements the `LLMClient` interface, handling authentication, 
  request formatting, and response parsing for the provider.
  For details, see [Running prompts with LLM clients](prompt-api.md#running-prompts-with-llm-clients).

* Using a **prompt executor** for a higher-level abstraction that wraps one or multiple LLM clients,
  manages their lifecycles, and unifies an interface across providers.
  It can optionally fall back to a single LLM client if a specific provider is unavailable.
  Prompt executors also handle failures, retries, and switching between providers.
  You can either create your own executor or use a pre-defined prompt executor for a specific provider.
  For details, see [Running prompts with prompt executors](prompt-api.md#running-prompts-with-prompt-executors).

## Next steps

- [Create and run an agent](getting-started.md) with a specific LLM provider.
- Learn more about [prompts](prompt-api.md) and [how to choose between LLM clients and prompt executors](prompt-api.md#choosing-between-llm-clients-and-prompt-executors).



