Koog provides a set of abstractions and implementations for working with Large Language Models (LLMs) from various
LLM providers in a provider-agnostic way. The set includes the following classes:

- **LLMCapability**: a class hierarchy that defines various capabilities that LLMs can support, such as:
    - Temperature adjustment for controlling response randomness
    - Tool integration for external system interaction
    - Vision processing for handling visual data
    - Embedding generation for vector representations
    - Completion for text generation tasks
    - Schema support for structured data (JSON with Simple and Full variants)
    - Speculation for exploratory responses

- **LLModel**: a data class that represents a specific LLM with its provider, unique identifier, and supported
  capabilities.

This serves as a foundation for interacting with different LLM providers in a unified way, allowing applications to work
with various models while abstracting away provider-specific details.

## LLM capabilities

LLM capabilities represent specific features or functionalities that a Large Language Model can support. In the Koog
framework, capabilities are used to define what a particular model can do and how it can be configured. Each capability
is represented as a subclass or data object of the `LLMCapability` class.

When configuring an LLM for use in your application, you specify which capabilities it supports by adding them to the
`capabilities` list when creating an `LLModel` instance. This allows the framework to properly interact with the model
and use its features appropriately.

### Core capabilities

The list below includes the core, LLM-specific capabilities that are available for models in the Koog framework:

- **Speculation** (`LLMCapability.Speculation`): lets the model generate speculative or exploratory responses with
  varying degrees of likelihood. Useful for creative or hypothetical scenarios where a broader range of potential
  outcomes
  is desired.

- **Temperature** (`LLMCapability.Temperature`): allows adjustment of the model's response randomness or creativity
  levels. Higher temperature values produce more diverse outputs, while lower values lead to more focused and
  deterministic responses.

- **Tools** (`LLMCapability.Tools`): indicates support for external tool usage or integration. This capability lets the
  model run specific tools or interact with external systems.

- **Tool choice** (`LLMCapability.ToolChoice`): configures how tool calling works with the LLM. Depending on the model,
  it can be configured to:
    - Automatically choose between generating text or tool calls
    - Generate only tool calls, never text
    - Generate only text, never tool calls
    - Force calling a specific tool among the defined tools

- **Multiple choices** (`LLMCapability.MultipleChoices`): lets the model generate multiple independent reply choices
  to a single prompt.

### Media processing capabilities

The following list represents a set of capabilities for processing media content such as images or audio:

- **Vision** (`LLMCapability.Vision`): a class for vision-based capabilities that process, analyze, and infer insights
  from visual data.
  Supports the following types of visual data:
    - **Image** (`LLMCapability.Vision.Image`): handles image-related vision tasks such as image analysis, recognition,
      and interpretation.
    - **Video** (`LLMCapability.Vision.Video`): processes video data, including analyzing and understanding video
      content.

- **Audio** (`LLMCapability.Audio`): provides audio-related functionalities such as transcription, audio generation, or
  audio-based interactions.

- **Document** (`LLMCapability.Document`): enables handling and processing of document-based inputs and outputs.

### Text processing capabilities

The following list of capabilities represents text generation and processing functionalities:

- **Embedding** (`LLMCapability.Embed`): lets models generate vector embeddings from an input text, enabling similarity
  comparisons, clustering, and other vector-based analyses.

- **Completion** (`LLMCapability.Completion`): includes the generation of text or content based on given input context,
  such as completing sentences, generating suggestions, or producing content that aligns with input data.

- **Prompt caching** (`LLMCapability.PromptCaching`): supports caching functionalities for prompts, potentially
  improving
  performance for repeated or similar queries.

- **Moderation** (`LLMCapability.Moderation`): lets the model analyze text for potentially harmful content and
  classify it according to various categories such as harassment, hate speech, self-harm, sexual content, violence, etc.

### Schema capabilities

The list below indicates the capabilities related to processing structured data:

- **Schema** (`LLMCapability.Schema`): a class for structured schema capabilities related to data interaction and
  encoding using specific formats.
  Includes support for the following format:
    - **JSON** (`LLMCapability.Schema.JSON`): JSON schema support with different levels:
        - **Basic** (`LLMCapability.Schema.JSON.Basic`): provides lightweight or basic JSON processing capabilities.
        - **Standard** (`LLMCapability.Schema.JSON.Standard`): offers comprehensive JSON schema support for complex data
          structures.

## Creating a model (LLModel) configuration

To define a model in a universal, provider-agnostic way, create a model configuration as an instance of the `LLModel`
class with the following parameters:

| Name              | Data type                 | Required | Default | Description                                                                                                                                                                                    |
|-------------------|---------------------------|----------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `provider`        | LLMProvider               | Yes      |         | The provider of the LLM, such as Google or OpenAI. This identifies the company or organization that created or hosts the model.                                                                |
| `id`              | String                    | Yes      |         | A unique identifier for the LLM instance. This typically represents the specific model version or name. For example, `gpt-4-turbo`, `claude-3-opus`, `llama-3-2`.                              |
| `capabilities`    | List&lt;LLMCapability&gt; | Yes      |         | A list of capabilities supported by the LLM, such as temperature adjustment, tools usage, or schema-based tasks. These capabilities define what the model can do and how it can be configured. |
| `contextLength`   | Long                      | Yes      |         | The context length of the LLM. This is the maximum number of tokens the LLM can process.                                                                                                       |
| `maxOutputTokens` | Long                      | No       | `null`  | The maximum number of tokens that can be generated by the provider for the LLM.                                                                                                                |

### Examples

This section provides detailed examples of creating `LLModel` instances with different capabilities.

The code below represents a basic LLM configuration with core capabilities:

<!--- INCLUDE
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

-->

```kotlin
val basicModel = LLModel(
    provider = LLMProvider.OpenAI,
    id = "gpt-4-turbo",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.Schema.JSON.Standard
    ),
    contextLength = 128_000
)
```

<!--- KNIT example-model-capabilities-01.kt -->

The model configuration below is a multimodal LLM with vision capabilities:

<!--- INCLUDE
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

-->

```kotlin
val visionModel = LLModel(
    provider = LLMProvider.OpenAI,
    id = "gpt-4-vision",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Vision.Image,
        LLMCapability.MultipleChoices
    ),
    contextLength = 1_047_576,
    maxOutputTokens = 32_768
)
```

<!--- KNIT example-model-capabilities-02.kt -->

An LLM with audio processing capabilities:

<!--- INCLUDE
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

-->

```kotlin
val audioModel = LLModel(
    provider = LLMProvider.Anthropic,
    id = "claude-3-opus",
    capabilities = listOf(
        LLMCapability.Audio,
        LLMCapability.Temperature,
        LLMCapability.PromptCaching
    ),
    contextLength = 200_000
)
```

<!--- KNIT example-model-capabilities-03.kt -->


In addition to creating models as `LLModel` instances and having to specify all related parameters, Koog includes a
collection of predefined models and their configurations with supported capabilities.
To use a predefined Ollama model, specify it as follows:

<!--- INCLUDE
import ai.koog.prompt.llm.OllamaModels

-->

```kotlin
val metaModel = OllamaModels.Meta.LLAMA_3_2
```

<!--- KNIT example-model-capabilities-04.kt -->


To check whether a model supports a specific capability use the `contains` method to check for the presence of the
capability in the `capabilities` list:

<!--- INCLUDE
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.OllamaModels

val basicModel = OllamaModels.Meta.LLAMA_3_2
val visionModel = OllamaModels.Meta.LLAMA_3_2

-->

```kotlin
// Check if models support specific capabilities
val supportsTools = basicModel.capabilities.contains(LLMCapability.Tools) // true
val supportsVideo = visionModel.capabilities.contains(LLMCapability.Vision.Video) // false

// Check for schema capabilities
val jsonCapability = basicModel.capabilities.filterIsInstance<LLMCapability.Schema.JSON>().firstOrNull()
val hasFullJsonSupport = jsonCapability is LLMCapability.Schema.JSON.Standard // true
```

<!--- KNIT example-model-capabilities-05.kt -->

### LLM capabilities by model

This reference shows which LLM capabilities are supported by each model across different providers.

In the tables below:

- `✓` indicates that the model supports the capability
- `-` indicates that the model does not support the capability
- For JSON Schema, `Full` or `Simple` indicates which variant of the JSON Schema capability the model supports

??? "Google models"
    #### Google models

    | Model                  | Temperature | JSON Schema | Completion | Multiple Choices | Tools | Tool Choice | Vision (Image) | Vision (Video) | Audio |
    |------------------------|-------------|-------------|------------|------------------|-------|-------------|----------------|----------------|-------|
    | Gemini2_5Pro           | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_5Flash         | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_5FlashLite     | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_0Flash         | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_0Flash001      | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_0FlashLite     | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |
    | Gemini2_0FlashLite001  | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | ✓              | ✓     |

??? "OpenAI models"
    #### OpenAI models

    | Model                    | Temperature | JSON Schema | Completion | Multiple Choices | Tools | Tool Choice | Vision (Image) | Vision (Video) | Audio | Speculation | Moderation |
    |--------------------------|-------------|-------------|------------|------------------|-------|-------------|----------------|----------------|-------|-------------|------------|
    | Reasoning.O4Mini         | -           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Reasoning.O3Mini         | -           | Full        | ✓          | ✓                | ✓     | ✓           | -              | -              | -     | ✓           | -          |
    | Reasoning.O3             | -           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Reasoning.O1             | -           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Chat.GPT4o               | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Chat.GPT4_1              | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Chat.GPT5                | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Chat.GPT5Mini            | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Chat.GPT5Nano            | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Audio.GptAudio           | ✓           | -           | ✓          | -                | ✓     | ✓           | -              | -              | ✓     | -           | -          |
    | Audio.GPT4oMiniAudio     | ✓           | -           | ✓          | -                | ✓     | ✓           | -              | -              | ✓     | -           | -          |
    | Audio.GPT4oAudio         | ✓           | -           | ✓          | -                | ✓     | ✓           | -              | -              | ✓     | -           | -          |
    | CostOptimized.GPT4_1Nano | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | CostOptimized.GPT4_1Mini | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | CostOptimized.GPT4oMini  | ✓           | Full        | ✓          | ✓                | ✓     | ✓           | ✓              | -              | -     | ✓           | -          |
    | Moderation.Omni          | -           | -           | -          | -                | -     | -           | ✓              | -              | -     | -           | ✓          |

??? "Anthropic models"
    #### Anthropic models

    | Model      | Temperature | JSON Schema | Completion | Tools | Tool Choice | Vision (Image) |
    |------------|-------------|-------------|------------|-------|-------------|----------------|
    | Opus_4_1   | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Opus_4     | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Sonnet_4   | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Sonnet_3_7 | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Haiku_3_5  | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Sonnet_3_5 | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Haiku_3    | ✓           | -           | ✓          | ✓     | ✓           | ✓              |
    | Opus_3     | ✓           | -           | ✓          | ✓     | ✓           | ✓              |

??? "Ollama models"
    #### Ollama models

    ##### Meta models

    | Model         | Temperature | JSON Schema | Tools | Moderation |
    |---------------|-------------|-------------|-------|------------|
    | LLAMA_3_2_3B  | ✓           | Simple      | ✓     | -          |
    | LLAMA_3_2     | ✓           | Simple      | ✓     | -          |
    | LLAMA_4       | ✓           | Simple      | ✓     | -          |
    | LLAMA_GUARD_3 | -           | -           | -     | ✓          |

    ##### Alibaba models

    | Model              | Temperature | JSON Schema | Tools |
    |--------------------|-------------|-------------|-------|
    | QWEN_2_5_05B       | ✓           | Simple      | ✓     |
    | QWEN_3_06B         | ✓           | Simple      | ✓     |
    | QWQ                | ✓           | Simple      | ✓     |
    | QWEN_CODER_2_5_32B | ✓           | Simple      | ✓     |

    ##### Groq models

    | Model                     | Temperature | JSON Schema | Tools |
    |---------------------------|-------------|-------------|-------|
    | LLAMA_3_GROK_TOOL_USE_8B  | ✓           | Full        | ✓     |
    | LLAMA_3_GROK_TOOL_USE_70B | ✓           | Full        | ✓     |

    ##### Granite models

    | Model              | Temperature | JSON Schema | Tools | Vision (Image) |
    |--------------------|-------------|-------------|-------|----------------|
    | GRANITE_3_2_VISION | ✓           | Simple      | ✓     | ✓              |

??? "DeepSeek models"
    #### DeepSeek models

    | Model            | Temperature | JSON Schema | Completion | Speculation | Tools | Tool Choice | Vision (Image) |
    |------------------|-------------|-------------|------------|-------------|-------|-------------|----------------|
    | DeepSeekChat     | ✓           | Full        | ✓          | -           | ✓     | ✓           | -              |
    | DeepSeekReasoner | ✓           | Full        | ✓          | -           | ✓     | ✓           | -              |

??? "OpenRouter models"
    #### OpenRouter models

    | Model               | Temperature | JSON Schema | Completion | Speculation | Tools | Tool Choice | Vision (Image) |
    |---------------------|-------------|-------------|------------|-------------|-------|-------------|----------------|
    | Phi4Reasoning       | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Claude3Opus         | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3Sonnet       | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3Haiku        | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3_5Sonnet     | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3_7Sonnet     | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude4Sonnet       | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude4_1Opus       | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | GPT4oMini           | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | GPT5                | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | GPT5Mini            | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | GPT5Nano            | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | GPT_OSS_120b        | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | GPT4                | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | GPT4o               | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | GPT4Turbo           | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | GPT35Turbo          | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Llama3              | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Llama3Instruct      | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Mistral7B           | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Mixtral8x7B         | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Claude3VisionSonnet | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3VisionOpus   | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Claude3VisionHaiku  | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | DeepSeekV30324      | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | -              |
    | Gemini2_5FlashLite  | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Gemini2_5Flash      | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
    | Gemini2_5Pro        | ✓           | Full        | ✓          | ✓           | ✓     | ✓           | ✓              |
