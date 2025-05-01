package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.serialization.Serializable


@Serializable
data class Prompt(
    val messages: List<Message>,
    val id: String,
    val model: LLModel,
    val params: LLMParams = LLMParams()
) {

    companion object {
        val Empty = Prompt(emptyList(), "", JetBrainsAIModels.Google.Flash2_0)

        fun build(model: LLModel, id: String, params: LLMParams = LLMParams(), init: PromptBuilder.() -> Unit): Prompt {
            val builder = PromptBuilder(model, id, params)
            builder.init()
            return builder.build()
        }

        fun build(prompt: Prompt, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt).also(init).build()
        }
    }
}

