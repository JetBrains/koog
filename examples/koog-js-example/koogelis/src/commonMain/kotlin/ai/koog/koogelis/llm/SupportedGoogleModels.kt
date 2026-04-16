package ai.koog.koogelis.llm

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLModel

object SupportedGoogleModels {

    private val GOOGLE_MODELS: Set<LLModel> = setOf(
        GoogleModels.Gemini2_0Flash,
        GoogleModels.Gemini2_0Flash001,
        GoogleModels.Gemini2_0FlashLite,
        GoogleModels.Gemini2_0FlashLite001,
        GoogleModels.Gemini2_5Pro,
        GoogleModels.Gemini2_5Flash,
        GoogleModels.Gemini2_5FlashLite,
        GoogleModels.Gemini3_Pro_Preview,
        GoogleModels.Gemini3_Flash_Preview,
    )
    private val LLM_MODEL_BY_ID: Map<String, LLModel> = GOOGLE_MODELS.associateBy { it.id }

    fun findModel(modelId: String): LLModel? = LLM_MODEL_BY_ID[modelId]
}
