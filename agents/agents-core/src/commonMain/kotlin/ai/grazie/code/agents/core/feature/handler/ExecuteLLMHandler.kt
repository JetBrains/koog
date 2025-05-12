package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

class ExecuteLLMHandler {

    var beforeLLMCallHandler: BeforeLLMCallHandler =
        BeforeLLMCallHandler { _ -> }

    var beforeLLMCallWithToolsHandler: BeforeLLMCallWithToolsHandler =
        BeforeLLMCallWithToolsHandler { _, _ -> }

    var afterLLMCallHandler: AfterLLMCallHandler =
        AfterLLMCallHandler { _ -> }

    var afterLLMCallWithToolsHandler: AfterLLMCallWithToolsHandler =
        AfterLLMCallWithToolsHandler { _, _ -> }
}

fun interface BeforeLLMCallHandler {
    suspend fun handle(prompt: Prompt)
}

fun interface BeforeLLMCallWithToolsHandler {
    suspend fun handle(prompt: Prompt, tools: List<ToolDescriptor>)
}

fun interface AfterLLMCallHandler {
    suspend fun handle(response: String)
}

fun interface AfterLLMCallWithToolsHandler {
    suspend fun handle(response: List<Message.Response>, tools: List<ToolDescriptor>)
}
