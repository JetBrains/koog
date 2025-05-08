package ai.grazie.code.agents.example.tone

import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.tools.ToolRegistry

/**
 * Creates a strategy for the tone analysis agent.
 */
fun toneStrategy(name: String, toolRegistry: ToolRegistry, toneStageName: String): LocalAgentStrategy {
    return strategy(name) {
        stage(
            name = toneStageName,
            requiredTools = toolRegistry.stagesToolDescriptors.getValue(toneStageName)
        ) {
            val nodeSendInput by nodeLLMSendStageInput()
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()
            val nodeCompressHistory by nodeLLMCompressHistory<ReceivedToolResult>()

            // Define the flow of the agent
            edge(nodeStart forwardTo nodeSendInput)

            // If the LLM responds with a message, finish
            edge(
                (nodeSendInput forwardTo nodeFinish)
                    onAssistantMessage { true }
            )

            // If the LLM calls a tool, execute it
            edge(
                (nodeSendInput forwardTo nodeExecuteTool)
                    onToolCall { true }
            )

            // If the history gets too large, compress it
            edge(
                (nodeExecuteTool forwardTo nodeCompressHistory)
                    onCondition { _ -> llm.readSession { prompt.messages.size > 100 } }
            )

            edge(nodeCompressHistory forwardTo nodeSendToolResult)

            // Otherwise, send the tool result directly
            edge(
                (nodeExecuteTool forwardTo nodeSendToolResult)
                    onCondition { _ -> llm.readSession { prompt.messages.size <= 100 } }
            )

            // If the LLM calls another tool, execute it
            edge(
                (nodeSendToolResult forwardTo nodeExecuteTool)
                    onToolCall { true }
            )

            // If the LLM responds with a message, finish
            edge(
                (nodeSendToolResult forwardTo nodeFinish)
                    onAssistantMessage { true }
            )
        }
    }
}