package ai.grazie.code.agents.local.strategies.impls

import ai.grazie.code.agents.local.dsl.builders.LocalAgentStrategyBuilder
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.extensions.nodeExecuteTool
import ai.grazie.code.agents.local.dsl.extensions.nodeLLMSendToolResult
import ai.grazie.code.agents.local.dsl.extensions.onAssistantMessage
import ai.grazie.code.agents.local.dsl.extensions.onToolCall
import ai.jetbrains.code.prompt.message.Message

@Suppress("FunctionName")
internal fun LocalAgentStrategyBuilder.StageEssayWriting() = stage {
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    val nodeSendTopic by node<Unit, Message.Response> { input ->
        llm.writeSession {
            updatePrompt {
                user("Write an essay on the topic of \"${stageInput}\"")
            }

            requestLLM()
        }
    }

    edge(nodeStart forwardTo  nodeSendTopic)

    edge(
        (nodeSendTopic forwardTo nodeFinish)
            onAssistantMessage { true }
    )

    edge(
        (nodeSendTopic forwardTo nodeExecuteTool)
            onToolCall { true }
    )

    edge(
        (nodeExecuteTool forwardTo nodeSendToolResult)
    )

    edge(
        (nodeSendToolResult forwardTo nodeExecuteTool)
            onToolCall { true }

    )

    edge(
        (nodeSendToolResult forwardTo nodeFinish)
            onAssistantMessage { true }
    )
}
