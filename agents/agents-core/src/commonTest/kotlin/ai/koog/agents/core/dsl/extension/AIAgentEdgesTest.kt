package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class AIAgentEdgesTest {
    private val serializer = KotlinxSerializer()

    private val node1 by nodeDoNothing<Message.Assistant>()

    private val node2 by nodeDoNothing<Message.Assistant>()

    private val node3 by nodeDoNothing<Message.Assistant>()

    private suspend fun checkStrategy(strategy: AIAgentGraphStrategy<String, String>) {
        val results = mutableListOf<Any?>()

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor(serializer) {}

        AIAgent(
            promptExecutor = testExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY
        ) {
            install(EventHandler) {
                onAgentCompleted { eventContext -> results += eventContext.result }
            }
        }.use { agent ->
            agent.run("", null)
        }

        // After compression, we should have one result
        assertEquals(1, results.size)
        assertEquals("Done", results.first())
    }

    @Test
    fun testEdgeOnToolCall() = runTest {
        val agentStrategy = strategy<String, String>("test") {
            edge(
                nodeStart forwardTo node1 transformed {
                    Message.Assistant(
                        parts = listOf(
                            MessagePart.Text("some message"),
                            MessagePart.Tool.Call("id", "tool", buildJsonObject { put("arg", JsonPrimitive(0)) })
                        ),
                        ResponseMetaInfo.Empty
                    )
                }
            )
            edge(node1 forwardTo nodeFinish onToolCalls { true } transformed { "Done" })
        }

        checkStrategy(agentStrategy)
    }

    @Test
    fun testEdgeOnToolCallWithCondition() = runTest {
        val agentStrategy = strategy<String, String>("test") {
            edge(
                nodeStart forwardTo node1 transformed {
                    Message.Assistant(
                        parts = listOf(
                            MessagePart.Text("some message"),
                            MessagePart.Tool.Call("id", "tool", buildJsonObject { put("arg", JsonPrimitive(0)) })
                        ),
                        ResponseMetaInfo.Empty
                    )
                }
            )
            edge(node1 forwardTo nodeFinish onToolCalls { it.tool == "tool" } transformed { "Done" })
        }

        checkStrategy(agentStrategy)
    }
}
