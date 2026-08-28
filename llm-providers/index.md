# LLM providers

Koog works with major LLM providers and also supports local models using [Ollama](https://ollama.com/). The following providers are currently supported:

| LLM provider                                                                                                                                                | Choose for                                                                                                                       |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| [OpenAI](https://platform.openai.com/docs/overview) (including [Azure OpenAI Service](https://azure.microsoft.com/en-us/products/ai-foundry/models/openai)) | Advanced models with a wide range of capabilities.                                                                               |
| [Anthropic](https://www.anthropic.com/)                                                                                                                     | Long contexts and prompt caching.                                                                                                |
| [Google](https://ai.google.dev/) β                                                                                                                          | Multimodal processing (audio, video), large contexts.                                                                            |
| [DeepSeek](https://www.deepseek.com/) β                                                                                                                     | Cost-effective reasoning and coding.                                                                                             |
| [OpenRouter](https://openrouter.ai/)                                                                                                                        | One integration with an access to multiple models from multiple providers for flexibility, provider comparison, and unified API. |
| [Amazon Bedrock](https://aws.amazon.com/bedrock/)                                                                                                           | AWS-native environment, enterprise security and compliance, multi-provider access.                                               |
| [Mistral](https://mistral.ai/) β                                                                                                                            | European data hosting, GDPR compliance.                                                                                          |
| [Alibaba](https://www.alibabacloud.com/en?_p_lc=1) β ([DashScope](https://dashscope.aliyun.com/) OpenAI-compatible client)                                  | Large contexts and cost-efficient Qwen models.                                                                                   |
| [Ollama](https://ollama.com/)                                                                                                                               | Privacy, local development, offline operation, and no API costs.                                                                 |

The table below shows the LLM capabilities that Koog supports and which providers offer these capabilities in their models.

| LLM capability                  | OpenAI                       | Anthropic                       | Google β                                      | DeepSeek β | OpenRouter       | Amazon Bedrock   | Mistral β                       | Alibaba β (DashScope OpenAI-compatible client) | Ollama (local models) |
| ------------------------------- | ---------------------------- | ------------------------------- | --------------------------------------------- | ---------- | ---------------- | ---------------- | ------------------------------- | ---------------------------------------------- | --------------------- |
| Supported input                 | Text, image, audio, document | Text, image, document[1](#fn:1) | Text, image, audio, video, document[1](#fn:1) | Text       | Differs by model | Differs by model | Text, image, document[1](#fn:1) | Text, image, audio, video[1](#fn:1)            | Text, image[1](#fn:1) |
| Response streaming              | ✓                            | ✓                               | ✓                                             | ✓          | ✓                | ✓                | ✓                               | ✓                                              | ✓                     |
| Tools                           | ✓                            | ✓                               | ✓                                             | ✓          | ✓                | ✓[1](#fn:1)      | ✓                               | ✓                                              | ✓                     |
| Tool choice                     | ✓                            | ✓                               | ✓                                             | ✓          | ✓                | ✓[1](#fn:1)      | ✓                               | ✓                                              | –                     |
| Structured output (JSON Schema) | ✓                            | ✓[1](#fn:1)                     | ✓                                             | ✓          | ✓[1](#fn:1)      | –                | ✓                               | ✓[1](#fn:1)                                    | ✓                     |
| Multiple choices                | ✓                            | –                               | ✓                                             | –          | ✓[1](#fn:1)      | ✓[1](#fn:1)      | ✓                               | ✓[1](#fn:1)                                    | –                     |
| Temperature                     | ✓                            | ✓                               | ✓                                             | ✓          | ✓                | ✓                | ✓                               | ✓                                              | ✓                     |
| Speculation                     | ✓[1](#fn:1)                  | –                               | –                                             | –          | ✓[1](#fn:1)      | –                | ✓[1](#fn:1)                     | ✓[1](#fn:1)                                    | –                     |
| Content moderation              | ✓                            | –                               | –                                             | –          | –                | ✓                | ✓                               | –                                              | ✓                     |
| Embeddings                      | ✓                            | –                               | –                                             | –          | –                | ✓                | ✓                               | –                                              | ✓                     |
| Prompt caching                  | ✓[1](#fn:1)                  | ✓                               | –                                             | –          | –                | –                | –                               | –                                              | –                     |
| Completion                      | ✓                            | ✓                               | ✓                                             | ✓          | ✓                | ✓                | ✓                               | ✓                                              | ✓                     |
| Local execution                 | –                            | –                               | –                                             | –          | –                | –                | –                               | –                                              | ✓                     |

Note

Koog supports the most commonly used capabilities for creating AI agents. LLMs from each provider may have additional features that Koog does not currently support. To learn more, refer to [Model capabilities](../model-capabilities/).

## Working with providers

Koog lets you work with LLM providers on two levels:

- Using an **LLM client** for direct interaction with a specific provider. Each client implements the `LLMClient` interface, handling authentication, request formatting, and response parsing for the provider. For details, see [LLM clients](../prompts/llm-clients/).
- Using a **prompt executor** for a higher-level abstraction that wraps one or multiple LLM clients, manages their lifecycles, and unifies an interface across providers. It can switch between providers and optionally fall back to a configured provider and LLM using the corresponding client. You can either create your own executor or use a pre-defined prompt executor for a specific provider. For details, see [Prompt executors](../prompts/prompt-executors/).

Using a prompt executor offers a higher‑level layer over one or more LLMClients. It manages client lifecycles and exposes a unified interface across providers. In multi‑provider setups, it can route requests between providers and optionally fall back to a designated client when needed for core requests. You can create your own executor or use pre‑defined ones—both single‑provider and multi‑provider options are available.

## Next steps

- [Create and run an agent](../quickstart/) with a specific LLM provider.
- Learn more about [prompts](../prompts/).

______________________________________________________________________

1. Capability is supported only by some models of the provider. [↩](#fnref:1 "Jump back to footnote 1 in the text")[↩](#fnref2:1 "Jump back to footnote 1 in the text")[↩](#fnref3:1 "Jump back to footnote 1 in the text")[↩](#fnref4:1 "Jump back to footnote 1 in the text")[↩](#fnref5:1 "Jump back to footnote 1 in the text")[↩](#fnref6:1 "Jump back to footnote 1 in the text")[↩](#fnref7:1 "Jump back to footnote 1 in the text")[↩](#fnref8:1 "Jump back to footnote 1 in the text")[↩](#fnref9:1 "Jump back to footnote 1 in the text")[↩](#fnref10:1 "Jump back to footnote 1 in the text")[↩](#fnref11:1 "Jump back to footnote 1 in the text")[↩](#fnref12:1 "Jump back to footnote 1 in the text")[↩](#fnref13:1 "Jump back to footnote 1 in the text")[↩](#fnref14:1 "Jump back to footnote 1 in the text")[↩](#fnref15:1 "Jump back to footnote 1 in the text")[↩](#fnref16:1 "Jump back to footnote 1 in the text")[↩](#fnref17:1 "Jump back to footnote 1 in the text")[↩](#fnref18:1 "Jump back to footnote 1 in the text")
