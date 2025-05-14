package ai.grazie.code.agents.core.api

import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.simpleStrategy
import ai.grazie.code.agents.core.dsl.extension.nodeExecuteTool
import ai.grazie.code.agents.core.dsl.extension.nodeLLMSendStageInput
import ai.grazie.code.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.grazie.code.agents.core.dsl.extension.onAssistantMessage
import ai.grazie.code.agents.core.dsl.extension.onToolCall
import ai.jetbrains.code.prompt.message.Message

/**
 * Creates and configures a [LocalAgentStrategy] for executing a chat interaction process.
 * The agent orchestrates interactions between different stages, nodes, and tools to
 * handle user input, execute tools, and provide responses.
 * Allows the agent to interact with the user in a chat-like manner.
 */
public fun chatAgentStrategy(): LocalAgentStrategy = simpleStrategy("chat") {
    val sendInput by nodeLLMSendStageInput("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    val giveFeedbackToCallTools by node<String, Message.Response> { input ->
        llm.writeSession {
            updatePrompt {
                user("Don't chat with plain text! Call one of the available tools, instead: ${tools.joinToString(", ") { it.name }}")
            }

            requestLLM()
        }
    }

    edge(nodeStart forwardTo sendInput)

    edge(sendInput forwardTo nodeExecuteTool onToolCall { true })
    edge(sendInput forwardTo giveFeedbackToCallTools onAssistantMessage { true })
    edge(giveFeedbackToCallTools forwardTo giveFeedbackToCallTools onAssistantMessage { true })
    edge(giveFeedbackToCallTools forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeExecuteTool forwardTo nodeFinish onToolCall { tc -> tc.tool == "__exit__" } transformed { "Chat finished" })
}

/**
 * Creates a KotlinAgent instance configured to execute a sequence of operations for a single run process
 * involving stages for sending an input, calling tools, and returning the final result.
 *
 * Sometimes, it also called "one-shot" strategy.
 * Useful if you need to run a straightforward process that doesn't require a lot of additional logic.
 */
public fun singleRunStrategy(): LocalAgentStrategy = simpleStrategy("single_run") {
    val sendInput by nodeLLMSendStageInput("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    edge(nodeStart forwardTo sendInput)
    edge(sendInput forwardTo nodeExecuteTool onToolCall { true })
    edge(sendInput forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}
