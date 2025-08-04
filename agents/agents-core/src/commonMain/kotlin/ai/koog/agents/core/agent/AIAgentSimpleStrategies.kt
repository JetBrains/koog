package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.dsl.extension.onToolCall

/**
 * Creates a single-run strategy for an AI agent.
 * This strategy defines a simple execution flow where the agent processes input,
 * calls tools, and sends results back to the agent.
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Call the LLM with the input.
 * 3. Execute a tool based on the LLM's response.
 * 4. Send the tool result back to the LLM.
 * 5. Repeat until LLM indicates no further tool calls are needed or the agent finishes.
 * @param runMode The mode in which the single-run strategy should operate. Defaults to SingleRunMode.SINGLE.
 *                - SingleRunMode.SINGLE: Executes without allowing multiple simultaneous tool calls.
 *                - SingleRunMode.SEQUENTIAL: Executes simultaneous tool calls sequentially.
 *                - SingleRunMode.PARALLEL: Executes multiple tool calls in parallel.
 * @return An instance of AIAgentStrategy configured according to the specified single-run mode.
 */
public fun singleRunStrategy(runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL): AIAgentStrategy<String, String> =
    when (runMode) {
        ToolCalls.SEQUENTIAL -> singleRunWithParallelAbility(false)
        ToolCalls.PARALLEL -> singleRunWithParallelAbility(true)
        ToolCalls.SINGLE_RUN_SEQUENTIAL -> singleRunModeStrategy()
    }

private fun singleRunWithParallelAbility(parallelTools: Boolean) = strategy("single_run_sequential") {
    val nodeCallLLM by nodeLLMRequestMultiple()
    val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = parallelTools)
    val nodeSendToolResult by nodeLLMSendMultipleToolResults()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(
        nodeCallLLM forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { it.joinToString("\n") { message -> message.content } }
    )

    edge(nodeExecuteTool forwardTo nodeSendToolResult)

    edge(
        nodeSendToolResult forwardTo nodeFinish
            onMultipleAssistantMessages { true }
            transformed { it.joinToString("\n") { message -> message.content } }
    )

    edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
}

private fun singleRunModeStrategy() = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Enum representing the modes in which a single-run strategy for an AI agent can be executed.
 *
 * These modes define how tasks or operations are processed during the agent's run:
 * - SEQUENTIAL: Multiple tool calls allowed but will be executed sequentially.
 * - PARALLEL: Tool calls executed in parallel.
 * - SINGLE: Multiple tool calls are not allowed.
 */
public enum class ToolCalls {
    SEQUENTIAL,
    PARALLEL,
    SINGLE_RUN_SEQUENTIAL
}
