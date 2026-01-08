package ai.koog.prompt.llm

/**
 * Represents a collection of predefined Large Language Models (LLM) available for LiteRT-LM.
 * LiteRT-LM is Google's on-device inference engine for running LLMs locally.
 *
 * Each model is configured with specific capabilities and context lengths suitable for on-device inference.
 */
public object LiteRTLMModels {
    /**
     * The Google object represents the configuration for Google's large language models (LLMs)
     * that can be run on-device via LiteRT-LM.
     * It contains the predefined model specifications for Google's LLMs, including their identifiers
     * and supported capabilities.
     */
    public object Google {
        /**
         * Represents the Gemma 3n E4B model optimized for on-device inference.
         *
         * Gemma 3n is a lightweight, multimodal model designed for efficient on-device execution.
         * The E4B variant uses 4-bit quantization for reduced memory footprint while maintaining
         * quality suitable for mobile and edge deployment.
         *
         * This model supports:
         * - Temperature adjustment for controlling response randomness
         * - Vision capabilities for image understanding
         * - Audio capabilities for audio processing
         *
         * Note: Tool support is available through LiteRT-LM's function calling mechanism.
         *
         * @see <a href="https://github.com/google-ai-edge/LiteRT-LM">LiteRT-LM GitHub</a>
         */
        public val GEMMA_3N_E4B: LLModel = LLModel(
            provider = LLMProvider.LiteRTLM,
            id = "gemma-3n-e4b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Vision.Image,
                LLMCapability.Audio,
                LLMCapability.Tools
            ),
            contextLength = 32_768,
        )
    }
}
