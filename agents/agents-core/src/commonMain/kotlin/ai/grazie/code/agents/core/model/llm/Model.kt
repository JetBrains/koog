package ai.grazie.code.agents.core.model.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enum class representing various [OpenAI models](https://platform.openai.com/docs/models) available for use.
 */
@Serializable
enum class OpenAIModel {

    @SerialName("gpt-4-0613")
    GPT_4,

    @SerialName("gpt-4o-2024-08-06")
    GPT_4O,

    @SerialName("gpt-4o-mini-2024-07-18")
    GPT_4O_MINI,

    @SerialName("gpt-4-turbo-2024-04-09")
    GPT_4_TURBO,

    @SerialName("gpt-3.5-turbo-0125")
    GPT_3_5_TURBO,

    @SerialName("o1-2024-12-17")
    O1,

    @SerialName("o1-mini-2024-09-12")
    O1_MINI,

    @SerialName("o3-mini-2025-01-31")
    O3_MINI,
}
