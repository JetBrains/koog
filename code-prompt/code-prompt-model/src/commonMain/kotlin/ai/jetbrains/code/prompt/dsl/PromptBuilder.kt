package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import ai.jetbrains.code.prompt.text.TextContentBuilder

@PromptDSL
class PromptBuilder internal constructor(private val model: LLModel, private val id: String, val params: LLMParams = LLMParams()) {
    private val messages = mutableListOf<Message>()

    companion object {
        fun from(prompt: Prompt) = PromptBuilder(
            prompt.model,
            prompt.id,
            prompt.params
        ).apply {
            messages.addAll(prompt.messages)
        }
    }


    fun system(content: String) {
        messages.add(Message.System(content))
    }

    fun system(init: TextContentBuilder.() -> Unit) {
        system(TextContentBuilder().apply(init).build())
    }

    fun user(content: String) {
        messages.add(Message.User(content))
    }

    fun user(init: TextContentBuilder.() -> Unit) {
        user(TextContentBuilder().apply(init).build())
    }

    fun assistant(content: String) {
        messages.add(Message.Assistant(content))
    }

    fun assistant(init: TextContentBuilder.() -> Unit) {
        assistant(TextContentBuilder().apply(init).build())
    }

    fun message(message: Message) {
        messages.add(message)
    }

    fun messages(messages: List<Message>) {
        this.messages.addAll(messages)
    }

    inner class ToolMessageBuilder() {
        fun call(call: Message.Tool.Call) {
            messages.add(call)
        }

        fun result(result: Message.Tool.Result) {
            messages.add(result)
        }
    }

    val tool = ToolMessageBuilder()

    fun tool(init: ToolMessageBuilder.() -> Unit) {
        tool.init()
    }

    internal fun build(): Prompt = Prompt(messages.toList(), id, model, params)
}