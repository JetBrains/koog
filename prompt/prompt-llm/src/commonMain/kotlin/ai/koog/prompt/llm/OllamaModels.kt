package ai.koog.prompt.llm

/**
 * Represents a collection of predefined Large Language Models (LLM) categorized by makers.
 * Each maker contains specific models with configurations such as unique identifiers and capabilities.
 */
public object OllamaModels {
    /**
     * The `Meta` object represents the configuration for the Meta large language models (LLMs).
     * It contains the predefined model specifications for Meta's LLMs, including their identifiers
     * and supported capabilities.
     */
    public object Meta {
        /**
         * Represents the LLAMA version 3.2 model provided by Meta.
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
        public val LLAMA_3_2: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "llama3.2",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )

        /**
         * Represents the LLAMA version 4 model provided by Meta.
         *
         * The LLAMA 4 collection of models is natively multimodal AI models that enable text and multimodal experiences.
         * These two models leverage a mixture-of-experts (MoE) architecture and support native multimodality (image input).
         *
         */
        public val LLAMA_4: LLModel = LLModel(
            provider = LLMProvider.Meta,
            id = "llama4:latest",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
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
         */
        public val QWQ: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwq",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )

        /**
         * Represents the Alibaba Qwen-Coder model version 2.5 with 32 billion parameters.
         *
         * This predefined instance of `LLModel` is provided by Alibaba and supports the following capabilities:
         * - `Temperature`: Allows adjustment of the temperature setting for controlling the randomness in responses.
         * - `Schema.JSON.Simple`: Supports tasks requiring JSON schema validation and handling in a simplified manner.
         * - `Tools`: Enables interaction with external tools or functionalities within the model's ecosystem.
         *
         * The model is identified by the unique ID "qwen2.5-coder:32b" and categorized under the Ollama provider.
         */
        public val QWEN_CODER_2_5_32B: LLModel = LLModel(
            provider = LLMProvider.Ollama,
            id = "qwen2.5-coder:32b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
    }

}
