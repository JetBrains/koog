package ai.koog.agents.core.agent

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMModerateMessage
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MermaidDiagramGeneratorTest {

    @Test
    fun `Should generate a diagram for simple graph`() {
        val myStrategy = strategy<String, String>("my-strategy") {
            val nodeCallLLM by nodeLLMRequest()
            val executeToolCall by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo nodeCallLLM)
            edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeCallLLM forwardTo executeToolCall onToolCall { true })
            edge(executeToolCall forwardTo sendToolResult)
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(sendToolResult forwardTo executeToolCall onToolCall { true })
        }

        val diagram = myStrategy.asMermaidDiagram()

        diagram shouldBe
            """
            ---
            title: my-strategy
            ---
            stateDiagram
                state "nodeCallLLM" as nodeCallLLM
                state "executeToolCall" as executeToolCall
                state "sendToolResult" as sendToolResult

                [*] --> nodeCallLLM
                nodeCallLLM --> [*] : transformed
                nodeCallLLM --> executeToolCall : onCondition
                executeToolCall --> sendToolResult
                sendToolResult --> [*] : transformed
                sendToolResult --> executeToolCall : onCondition
            """.trimIndent()
    }

    @Test
    fun `Should create a diagram for advanced`() {
        val strategy = strategy<String, String>(
            name = "test-strategy",
        ) {
            val moderateInput by nodeLLMModerateMessage(
                name = "moderate-input",
                moderatingModel = OpenAIModels.Moderation.Omni,
            )
            val nodeCallLLM by nodeLLMRequest("CallLLM")

            val nodeExecuteTool by nodeExecuteTool("ExecuteTool")
            val nodeSendToolResult by nodeLLMSendToolResult("SendToolResult")

            edge(
                nodeStart forwardTo moderateInput transformed {
                    Message.User(it, metaInfo = RequestMetaInfo.Empty)
                },
            )

            edge(
                moderateInput forwardTo nodeCallLLM
                    onCondition { !it.moderationResult.isHarmful }
                    transformed { it.message.content },
            )

            edge(
                moderateInput forwardTo nodeFinish
                    onCondition { it.moderationResult.isHarmful }
                    transformed { "Moderation Error" },
            )

            edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        }

        val diagram = strategy.asMermaidDiagram()

        diagram shouldBe
            """
            ---
            title: test-strategy
            ---
            stateDiagram
                state "moderate-input" as moderate_input
                state "CallLLM" as CallLLM
                state "ExecuteTool" as ExecuteTool
                state "SendToolResult" as SendToolResult

                [*] --> moderate_input : transformed
                moderate_input --> CallLLM : transformed
                moderate_input --> [*] : transformed
                CallLLM --> [*] : transformed
                CallLLM --> ExecuteTool : onCondition
                ExecuteTool --> SendToolResult
                SendToolResult --> [*] : transformed
                SendToolResult --> ExecuteTool : onCondition
            """.trimIndent()
    }
}
