package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import ai.jetbrains.code.prompt.params.LLMParams.Schema
import ai.jetbrains.code.prompt.params.LLMParams.ToolChoice
import kotlinx.serialization.Serializable


@Serializable
data class Prompt(
    val messages: List<Message>,
    val id: String,
    val params: LLMParams = LLMParams()
) {

    companion object {
        val Empty = Prompt(emptyList(), "")

        fun build(id: String, params: LLMParams = LLMParams(), init: PromptBuilder.() -> Unit): Prompt {
            val builder = PromptBuilder(id, params)
            builder.init()
            return builder.build()
        }

        fun build(prompt: Prompt, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt).also(init).build()
        }
    }

    fun withMessages(newMessages: List<Message>): Prompt = copy(messages = newMessages)

    fun withUpdatedMessages(update: MutableList<Message>.() -> Unit): Prompt =
        this.copy(messages = messages.toMutableList().apply { update() })

    fun withParams(newParams: LLMParams): Prompt = copy(params = newParams)

    class LLMParamsUpdateContext internal constructor(
        var temperature: Double?,
        var speculation: String?,
        var schema: Schema?,
        var toolChoice: ToolChoice?,
    ) {
        internal constructor(params: LLMParams) : this(
            params.temperature,
            params.speculation,
            params.schema,
            params.toolChoice
        )

        fun toParams(): LLMParams = LLMParams(
            temperature = temperature,
            speculation = speculation,
            schema = schema,
            toolChoice = toolChoice
        )
    }

    fun withUpdatedParams(update: LLMParamsUpdateContext.() -> Unit): Prompt =
        copy(params = LLMParamsUpdateContext(params).apply { update() }.toParams())
}
