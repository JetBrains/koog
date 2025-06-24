package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
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
 */
public fun singleRunStrategy(): AIAgentStrategy<String, String> = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}
