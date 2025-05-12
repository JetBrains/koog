package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import ai.jetbrains.code.prompt.params.LLMParams.Schema
import ai.jetbrains.code.prompt.params.LLMParams.ToolChoice
import kotlinx.serialization.Serializable


@Serializable
public data class Prompt(
    val messages: List<Message>,
    val id: String,
    val params: LLMParams = LLMParams()
) {

    public companion object {
        public val Empty: Prompt = Prompt(emptyList(), "")

        public fun build(id: String, params: LLMParams = LLMParams(), init: PromptBuilder.() -> Unit): Prompt {
            val builder = PromptBuilder(id, params)
            builder.init()
            return builder.build()
        }

        public fun build(prompt: Prompt, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt).also(init).build()
        }
    }

    public fun withMessages(newMessages: List<Message>): Prompt = copy(messages = newMessages)

    public fun withUpdatedMessages(update: MutableList<Message>.() -> Unit): Prompt =
        this.copy(messages = messages.toMutableList().apply { update() })

    public fun withParams(newParams: LLMParams): Prompt = copy(params = newParams)

    public class LLMParamsUpdateContext internal constructor(
        public var temperature: Double?,
        public var speculation: String?,
        public var schema: Schema?,
        public var toolChoice: ToolChoice?,
    ) {
        internal constructor(params: LLMParams) : this(
            params.temperature,
            params.speculation,
            params.schema,
            params.toolChoice
        )

        public fun toParams(): LLMParams = LLMParams(
            temperature = temperature,
            speculation = speculation,
            schema = schema,
            toolChoice = toolChoice
        )
    }

    public fun withUpdatedParams(update: LLMParamsUpdateContext.() -> Unit): Prompt =
        copy(params = LLMParamsUpdateContext(params).apply { update() }.toParams())
}
