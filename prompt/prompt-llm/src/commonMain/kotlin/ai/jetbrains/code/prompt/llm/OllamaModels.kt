package ai.jetbrains.code.prompt.llm

public object OllamaModels {
    public object Meta {
        public val LLAMA_3_2: LLModel = LLModel(
            provider = LLMProvider.Meta,
            id = "meta-llama-3-2",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
    }

    public object Alibaba {
        public val QWQ: LLModel = LLModel(
            provider = LLMProvider.Alibaba,
            id = "alibaba-qwq",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
        public val QWEN_CODER_2_5_32B: LLModel = LLModel(
            provider = LLMProvider.Alibaba,
            id = "alibaba-qwen-coder-2-5-32b",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
    }

}
