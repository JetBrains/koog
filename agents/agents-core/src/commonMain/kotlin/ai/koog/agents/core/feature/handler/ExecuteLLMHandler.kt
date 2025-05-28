package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

public class ExecuteLLMHandler {

    public var beforeLLMCallHandler: BeforeLLMCallHandler =
        BeforeLLMCallHandler { prompt, tools -> }

    public var afterLLMCallHandler: AfterLLMCallHandler =
        AfterLLMCallHandler { response, tools -> }
}

public fun interface BeforeLLMCallHandler {
    public suspend fun handle(prompt: Prompt, tools: List<ToolDescriptor>)
}

public fun interface AfterLLMCallHandler {
    public suspend fun handle(responses: List<Message.Response>, tools: List<ToolDescriptor>)
}
