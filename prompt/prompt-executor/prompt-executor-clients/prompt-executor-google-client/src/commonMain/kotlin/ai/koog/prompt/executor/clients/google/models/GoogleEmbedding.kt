package ai.koog.prompt.executor.clients.google.models

import ai.koog.prompt.executor.clients.google.GoogleEmbeddingParams
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.Serializable

@Serializable
internal data class GoogleEmbeddingRequest(
    val model: String,
    val content: GoogleContent,
    val outputDimensionality: Int? = null,
    val taskType: String? = null,
    val title: String? = null,
) {
    companion object {
        fun from(model: LLModel, text: String, params: GoogleEmbeddingParams): GoogleEmbeddingRequest =
            GoogleEmbeddingRequest(
                model = "models/${model.id}",
                content = GoogleContent(parts = listOf(GooglePart.Text(text))),
                outputDimensionality = params.dimensions,
                taskType = params.taskType?.name,
                title = params.title,
            )
    }
}

@Serializable
internal data class GoogleEmbeddingResponse(
    val embedding: GoogleEmbeddingData
)

@Serializable
internal data class GoogleEmbeddingData(
    val values: List<Double>
)
