package ai.koog.prompt.executor.ollama.client.dto

import ai.koog.prompt.executor.ollama.client.OllamaModelCard
import ai.koog.prompt.llm.LLMCapability
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal fun OllamaShowModelResponseDTO.toOllamaModelCard(name: String, size: Long): OllamaModelCard {
    if (details == null) error("Invalid data")

    // cf. https://github.com/ollama/ollama/blob/f18e0cb5508450bd14db5ec8015709d2c4ab820f/cmd/cmd.go#L686
    val architecture = modelInfo?.get("general.architecture")?.jsonPrimitive?.content
    val parameterCount = modelInfo?.get("general.parameter_count")?.jsonPrimitive?.long
    val contextLength = architecture?.let { modelInfo["$it.context_length"]!!.jsonPrimitive.long }
    val embeddingLength = architecture?.let { modelInfo["$it.embedding_length"]!!.jsonPrimitive.long }

    return OllamaModelCard(
        name = name,
        family = details.family,
        families = details.families,
        size = size,
        parameterCount = parameterCount,
        contextLength = contextLength,
        embeddingLength = embeddingLength,
        quantizationLevel = details.quantizationLevel,
        capabilities = capabilities.toLLMCapabilities(),
    )
}

private fun List<OllamaShowModelResponseDTO.Capability>.toLLMCapabilities(): List<LLMCapability> {
    return flatMap { capability ->
        when (capability) {
            OllamaShowModelResponseDTO.Capability.COMPLETION -> listOf(LLMCapability.Completion)
            OllamaShowModelResponseDTO.Capability.EMBEDDING -> listOf(LLMCapability.Embed)
            OllamaShowModelResponseDTO.Capability.INSERT -> listOf()
            OllamaShowModelResponseDTO.Capability.VISION -> listOf(LLMCapability.Vision.Image)
            OllamaShowModelResponseDTO.Capability.THINKING -> listOf()
            OllamaShowModelResponseDTO.Capability.TOOLS -> listOf(LLMCapability.Tools)
        }
    } + listOf(
        LLMCapability.Temperature,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
    )
}
