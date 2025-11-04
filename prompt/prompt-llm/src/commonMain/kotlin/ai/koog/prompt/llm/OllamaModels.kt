package ai.koog.prompt.llm

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Represents a collection of predefined Large Language Models (LLM) categorized by makers.
 * Each maker contains specific models with configurations such as unique identifiers and capabilities.
 */
public object OllamaModels {
    /**
     *  The Groq object represents the configuration for the Groq large language model (LLM).
     *  It contains the predefined model specifications for Groq's LLMs, including their identifiers
     *  and supported capabilities.
     */
    public object Groq {
        /**
         * Represents the LLAMA version 3.0 model provided by Groq with 8B parameters.
         *
         * This variable defines an instance of the `LLModel` class with the Ollama provider, a unique identifier "llama3",
         * and a set of capabilities. The supported capabilities include:
         *  - Temperature adjustment.
         *  - JSON Schema-based tasks (Not very clear).
         *  - Tool utilization.
         *
         * LLAMA 3 is designed to support these specified features, enabling developers to utilize the model for tasks
         * that require dynamic behavior adjustments, schema adherence, and tool-based interactions.
         *
         * @see <a href="https://ollama.com/library/llama3-groq-tool-use">
         */
        @JvmField
        public val LLAMA_3_GROK_TOOL_USE_8B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3-groq-tool-use:8b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Tools
            ),
            contextLength = 8_192,
        )

        /**
         * Represents the LLAMA version 3.0 model provided by Groq with 70B parameters.
         *
         * This variable defines an instance of the `LLModel` class with the Ollama provider, a unique identifier "llama3",
         * and a set of capabilities. The supported capabilities include:
         *  - Temperature adjustment.
         *  - JSON Schema-based tasks (Not very clear).
         *  - Tool utilization.
         *
         * LLAMA 3 is designed to support these specified features, enabling developers to utilize the model for tasks
         * that require dynamic behavior adjustments, schema adherence, and tool-based interactions.
         *
         * @see <a href="https://ollama.com/library/llama3-groq-tool-use">
         */
        @JvmField
        public val LLAMA_3_GROK_TOOL_USE_70B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3-groq-tool-use:70b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Tools
            ),
            contextLength = 8_192,
        )
    }

    /**
     * The `Meta` object represents the configuration for the Meta large language models (LLMs).
     * It contains the predefined model specifications for Meta's LLMs, including their identifiers
     * and supported capabilities.
     */
    public object Meta {
        /**
         * Represents the LLAMA version 3.2.3b model provided by Meta.
         *
         * This variable defines an instance of the `LLModel` class with the Ollama provider, a unique identifier "llama3.2:3b",
         * and a set of capabilities. The supported capabilities include:
         *  - Temperature adjustment.
         *  - JSON Schema-based tasks (Simple Schema).
         *  - Tool utilization.
         *
         * LLAMA 3.2.3b is designed to support these specified features, enabling developers to utilize the model for tasks
         * that require dynamic behavior adjustments, schema adherence, and tool-based interactions.
         *
         * @see <a href="https://ollama.com/library/llama3.2">
         */
        @JvmField
        public val LLAMA_3_2_3B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3.2:3b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 131_072,
        )

        /**
         * Represents the latest LLAMA version 3.2 model provided by Meta.
         *
         * This variable defines an instance of the `LLModel` class with the Ollama provider, a unique identifier "llama3.2",
         * and a set of capabilities. The supported capabilities include:
         *  - Temperature adjustment.
         *  - JSON Schema-based tasks (Simple Schema).
         *  - Tool utilization.
         *
         * LLAMA 3.2 is designed to support these specified features, enabling developers to utilize the model for tasks
         * that require dynamic behavior adjustments, schema adherence, and tool-based interactions.
         */
        @JvmField
        public val LLAMA_3_2: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3.2:latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 131_072,
        )

        /**
         * Represents the LLAMA version 4 model provided by Meta.
         *
         * The LLAMA 4 collection of models is natively multimodal AI models that enable text and multimodal experiences.
         * These two models leverage a mixture-of-experts (MoE) architecture and support native multimodality (image input).
         *
         * @see <a href="https://ollama.com/library/llama4">
         */
        @JvmField
        public val LLAMA_4_SCOUT: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama4:latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 10_485_760,
        )

        /**
         * Represents the LLAMA version 4 model provided by Meta.
         *
         * The LLAMA 4 collection of models is natively multimodal AI models that enable text and multimodal experiences.
         * These two models leverage a mixture-of-experts (MoE) architecture and support native multimodality (image input).
         *
         * @see <a href="https://ollama.com/library/llama4">
         */
        @JvmField
        public val LLAMA_4: LLModel = LLAMA_4_SCOUT

        /**
         * Represents the llama-guard model version3 provided by Meta.
         *
         * The llama-guard collection of models is natively multimodal AI models that enable text and multimodal experiences.
         * These two models leverage a mixture-of-experts (MoE) architecture and support native multimodality (image input).
         *
         * @see <a href="https://ollama.com/library/llama-guard3">
         */
        @JvmField
        public val LLAMA_GUARD_3: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama-guard3:latest",
            capabilities = listOf(
                LLMCapability.Moderation
            ),
            contextLength = 131_072,
        )
    }

    /**
     * Represents an object that contains predefined Large Language Models (LLMs) made by Alibaba.
     *
     * The `Alibaba` object provides access to multiple LLM instances, each with specific identifiers and capabilities.
     * These models are configured with Ollama as the provider and are characterized by their unique capabilities.
     */
    public object Alibaba {
        /**
         * Represents the Qwen-2.5 model with 0.5 billion parameters.
         *
         * This predefined instance of `LLModel` is provided by Alibaba and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         *
         * The model is identified by the unique ID "qwen2.5:0.5b" and categorized under the Ollama provider.
         *
         * @see <a href="https://ollama.com/library/qwen2.5">
         */
        @JvmField
        public val QWEN_2_5_05B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwen2.5:0.5b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 32_768,
        )

        /**
         * Represents the Qwen-3.06b model with 0.6 billion parameters.
         *
         * This predefined instance of `LLModel` is provided by Alibaba and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         *
         * The model is identified by the unique ID "qwen3:0.6b" and categorized under the Ollama provider.
         *
         * @see <a href="https://ollama.com/library/qwen3">
         */
        @JvmField
        public val QWEN_3_06B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwen3:0.6b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 40_960,
        )

        /**
         * Represents the QWQ model with 32 billion parameters.
         *
         * This predefined instance of `LLModel` is provided by Alibaba and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         *
         * The model is identified by the unique ID "qwq:32b" and categorized under the Ollama provider.
         *
         * @see <a href="https://ollama.com/library/qwq">
         */
        @JvmField
        public val QWQ_32B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwq:32b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 40_960,
        )

        /**
         * Represents the `QWQ` language model instance provided by Alibaba with specific capabilities.
         *
         * The model is identified by its unique `id` "qwq". It belongs to the Ollama provider
         * and supports multiple advanced capabilities:
         * - Temperature Adjustment: Enables control over the randomness of the model's output.
         * - JSON Schema (Simple): Supports tasks structured through simple JSON schemas.
         * - Tools Usage: Allows the model to interact with external tools for extended functionality.
         *
         * Use this configuration to interact with the Alibaba `QWQ` model in applications that
         * require these capabilities for varied and advanced tasks.
         *
         * @see <a href="https://ollama.com/library/qwq">
         */
        @JvmField
        public val QWQ: LLModel = QWQ_32B

        /**
         * Represents the Alibaba Qwen-Coder model version 2.5 with 32 billion parameters.
         *
         * This predefined instance of `LLModel` is provided by Alibaba and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         *
         * The model is identified by the unique ID "qwen2.5-coder:32b" and categorized under the Ollama provider.
         *
         * <a href="https://ollama.com/library/qwen2.5-coder">
         */
        @JvmField
        public val QWEN_CODER_2_5_32B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwen2.5-coder:32b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 32_768,
        )
    }

    /**
     * The `Granite` object represents the configuration for Granite large language models (LLMs).
     * It contains the predefined model specifications for Granite LLMs, including their identifiers
     * and supported capabilities.
     */
    public object Granite {
        /**
         * Represents the Granite 3.2 Vision model.
         *
         * This predefined instance of `LLModel` is provided by Granite and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         * - `Vision.Image`: Enables processing and understanding of image inputs alongside text.
         *
         * The model is identified by the unique ID "granite3.2-vision" and categorized under the Ollama provider.
         * It is designed to handle both text and visual inputs for multimodal tasks.
         *
         * @see <a href="https://ollama.com/library/granite3.2-vision">
         */
        @JvmField
        public val GRANITE_3_2_VISION: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "granite3.2-vision",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools,
                LLMCapability.Vision.Image,
                LLMCapability.Document
            ),
            contextLength = 16_384,
        )
    }
}
