package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

public class ExecuteLLMHandler {

    public var beforeLLMCallHandler: BeforeLLMCallHandler =
        BeforeLLMCallHandler { _ -> }

    public var beforeLLMCallWithToolsHandler: BeforeLLMCallWithToolsHandler =
        BeforeLLMCallWithToolsHandler { _, _ -> }

    public var afterLLMCallHandler: AfterLLMCallHandler =
        AfterLLMCallHandler { _ -> }

    public var afterLLMCallWithToolsHandler: AfterLLMCallWithToolsHandler =
        AfterLLMCallWithToolsHandler { _, _ -> }
}

public fun interface BeforeLLMCallHandler {
    public suspend fun handle(prompt: Prompt)
}

public fun interface BeforeLLMCallWithToolsHandler {
    public suspend fun handle(prompt: Prompt, tools: List<ToolDescriptor>)
}

public fun interface AfterLLMCallHandler {
    public suspend fun handle(response: String)
}

public fun interface AfterLLMCallWithToolsHandler {
    public suspend fun handle(response: List<Message.Response>, tools: List<ToolDescriptor>)
}
