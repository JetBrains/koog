package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import ai.jetbrains.code.prompt.text.TextContentBuilder

@PromptDSL
public class PromptBuilder internal constructor(private val id: String, private val params: LLMParams = LLMParams()) {
    private val messages = mutableListOf<Message>()

    internal companion object {
        internal fun from(prompt: Prompt): PromptBuilder = PromptBuilder(
            prompt.id,
            prompt.params
        ).apply {
            messages.addAll(prompt.messages)
        }
    }

    public fun system(content: String) {
        messages.add(Message.System(content))
    }

    public fun system(init: TextContentBuilder.() -> Unit) {
        system(TextContentBuilder().apply(init).build())
    }

    public fun user(content: String) {
        messages.add(Message.User(content))
    }

    public fun user(init: TextContentBuilder.() -> Unit) {
        user(TextContentBuilder().apply(init).build())
    }

    public fun assistant(content: String) {
        messages.add(Message.Assistant(content))
    }

    public fun assistant(init: TextContentBuilder.() -> Unit) {
        assistant(TextContentBuilder().apply(init).build())
    }

    public fun message(message: Message) {
        messages.add(message)
    }

    public fun messages(messages: List<Message>) {
        this.messages.addAll(messages)
    }

    public inner class ToolMessageBuilder() {
        public fun call(call: Message.Tool.Call) {
            messages.add(call)
        }

        public fun result(result: Message.Tool.Result) {
            messages.add(result)
        }
    }

    private val tool = ToolMessageBuilder()

    public fun tool(init: ToolMessageBuilder.() -> Unit) {
        tool.init()
    }

    internal fun build(): Prompt = Prompt(messages.toList(), id, params)
}
