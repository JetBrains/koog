@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class ExecuteLLMHandler {

    public var beforeLLMCallHandler: BeforeLLMCallHandler =
        BeforeLLMCallHandler { prompt, tools, model, sessionUuid -> }

    public var afterLLMCallHandler: AfterLLMCallHandler =
        AfterLLMCallHandler { prompt, tools, model, response, sessionUuid -> }
}

public fun interface BeforeLLMCallHandler {
    public suspend fun handle(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, sessionUuid: Uuid)
}

public fun interface AfterLLMCallHandler {
    public suspend fun handle(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>, sessionUuid: Uuid)
}
