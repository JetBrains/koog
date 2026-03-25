package ai.koog.agents.core.agent

import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
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
            // language=mermaid
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
    fun `Should create a diagram for advanced strategy`() {
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
            // language=mermaid
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

    @Test
    fun `Should generate a diagram for strategy with subgraph`() {
        val myStrategy = strategy<String, String>("subgraph-strategy") {
            val node1 by node<String, String>("node1") { it }
            val node2 by node<String, String>("node2") { it }

            val sg by subgraph<String, String>("sg1") {
                val sgNode1 by node<String, String>("sgNode1") { it }
                val sgNode2 by node<String, String>("sgNode2") { it }

                nodeStart then sgNode1 then sgNode2 then nodeFinish
            }

            nodeStart then node1 then sg then node2 then nodeFinish
        }

        val diagram = myStrategy.asMermaidDiagram()

        diagram shouldBe
            // language=mermaid
            """
            ---
            title: subgraph-strategy
            ---
            stateDiagram
                state "node1" as node1
                state "node2" as node2
                state "sg1" as sg1 {
                    state "sgNode1" as sgNode1
                    state "sgNode2" as sgNode2

                    [*] --> sgNode1
                    sgNode1 --> sgNode2
                    sgNode2 --> [*]
                }

                [*] --> node1
                node1 --> sg1
                sg1 --> node2
                node2 --> [*]
            """.trimIndent()
    }

    @Test
    fun `Should generate a diagram for strategy with nested subgraphs`() {
        val myStrategy = strategy<String, String>("nested-strategy") {
            val node1 by node<String, String>("node1") { it }

            val sg by subgraph<String, String>("sg1") {
                val sgNode1 by node<String, String>("sgNode1") { it }
                val sgNode2 by node<String, String>("sgNode2") { it }

                val innerSg by subgraph<String, String>("sg2") {
                    val sg2Node1 by node<String, String>("sg2Node1") { it }
                    val sg2Node2 by node<String, String>("sg2Node2") { it }

                    nodeStart then sg2Node1 then sg2Node2 then nodeFinish
                }

                nodeStart then sgNode1 then innerSg then sgNode2 then nodeFinish
            }

            nodeStart then node1 then sg then nodeFinish
        }

        val diagram = myStrategy.asMermaidDiagram()

        diagram shouldBe
            // language=mermaid
            """
            ---
            title: nested-strategy
            ---
            stateDiagram
                state "node1" as node1
                state "sg1" as sg1 {
                    state "sgNode1" as sgNode1
                    state "sgNode2" as sgNode2
                    state "sg2" as sg2 {
                        state "sg2Node1" as sg2Node1
                        state "sg2Node2" as sg2Node2

                        [*] --> sg2Node1
                        sg2Node1 --> sg2Node2
                        sg2Node2 --> [*]
                    }

                    [*] --> sgNode1
                    sgNode1 --> sg2
                    sg2 --> sgNode2
                    sgNode2 --> [*]
                }

                [*] --> node1
                node1 --> sg1
                sg1 --> [*]
            """.trimIndent()
    }
}
