package ai.grazie.code.agents.testing.tools

import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.tools.DirectToolCallsEnabler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.annotations.InternalAgentToolsApi
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message

@OptIn(InternalAgentToolsApi::class)
private object MockToolsEnabler : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
class MockEnvironment(
    val toolRegistry: ToolRegistry,
    val promptExecutor: PromptExecutor,
    val baseEnvironment: AgentEnvironment? = null
) : AgentEnvironment {
    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        return toolCalls.map {
            executeTool(it)
        }
    }

    private suspend fun executeTool(functionCall: Message.Tool.Call): ReceivedToolResult {
        if (promptExecutor is MockLLMExecutor) {
            promptExecutor.toolActions
                .find { it.satisfies(functionCall) }
                ?.invokeAndSerialize(functionCall)
                ?.let { (result, content) ->
                    return ReceivedToolResult(
                        id = functionCall.id,
                        tool = functionCall.tool,
                        content = content,
                        result = result
                    )
                }
        }
        val tool = toolRegistry
            .getStageByTool(functionCall.tool)
            .getTool(functionCall.tool)

        val args = tool.decodeArgsFromString(functionCall.content)
        val result = tool.executeUnsafe(args, MockToolsEnabler)


        return ReceivedToolResult(
            id = functionCall.id,
            tool = functionCall.tool,
            content = tool.encodeResultToStringUnsafe(result),
            result = result
        )
    }

    override suspend fun reportProblem(exception: Throwable) {
        throw exception
    }

    override suspend fun sendTermination(result: String?) {
        baseEnvironment?.sendTermination(result)
    }
}