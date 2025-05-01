package ai.jetbrains.code.prompt.llm

object OllamaModels {
    object Meta {
        val LLAMA_3_2 = LLModel(
            provider = LLMProvider.Meta,
            id = "meta-llama-3-2",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
    }

    object Alibaba {
        val QWQ = LLModel(
            provider = LLMProvider.Alibaba,
            id = "alibaba-qwq",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Simple,
                LLMCapability.Tools
            )
        )
        val QWEN_CODER_2_5_32B = LLModel(
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
