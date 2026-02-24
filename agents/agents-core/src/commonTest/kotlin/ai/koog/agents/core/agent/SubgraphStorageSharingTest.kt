package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SubgraphStorageSharingTest {

    @Test
    fun test_storage_is_shared_between_two_sequential_subgraphs() = runTest {
        val storageKey: AIAgentStorageKey<String> = createStorageKey("shared_value")

        val testStrategy = strategy<String, String>("two_subgraphs") {
            val subgraph1 by subgraph<String, String>("subgraph1") {
                val writeToStorage by node<String, String>("writeToStorage") { input ->
                    storage.set(storageKey, "written_by_subgraph1")
                    input
                }
                val llmCall by nodeLLMRequest()

                edge(nodeStart forwardTo writeToStorage)
                edge(writeToStorage forwardTo llmCall)
                edge(llmCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val subgraph2 by subgraph<String, String>("subgraph2") {
                val readFromStorage by node<String, String>("readFromStorage") { _ ->
                    val value = storage.get(storageKey)
                    assertNotNull(value, "Storage value from subgraph1 should be accessible in subgraph2")
                    assertEquals("written_by_subgraph1", value)
                    value
                }

                edge(nodeStart forwardTo readFromStorage)
                edge(readFromStorage forwardTo nodeFinish)
            }

            edge(nodeStart forwardTo subgraph1)
            edge(subgraph1 forwardTo subgraph2)
            edge(subgraph2 forwardTo nodeFinish)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("subgraph1 done").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = testStrategy,
            toolRegistry = ToolRegistry { }
        )

        val result = agent.run("test input", null)
        assertEquals("written_by_subgraph1", result)
    }

    @Test
    fun test_storage_value_set_before_subgraphs_is_accessible_in_both() = runTest {
        val presetKey: AIAgentStorageKey<String> = createStorageKey("preset_value")

        var valueInSubgraph1: String? = null
        var valueInSubgraph2: String? = null

        val testStrategy = strategy<String, String>("preset_storage") {
            val setupNode by node<String, String>("setup") { input ->
                storage.set(presetKey, "preset")
                input
            }

            val subgraph1 by subgraph<String, String>("subgraph1") {
                val readNode by node<String, String>("read1") { input ->
                    valueInSubgraph1 = storage.get(presetKey)
                    input
                }
                val llmCall by nodeLLMRequest()

                edge(nodeStart forwardTo readNode)
                edge(readNode forwardTo llmCall)
                edge(llmCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val subgraph2 by subgraph<String, String>("subgraph2") {
                val readNode by node<String, String>("read2") { input ->
                    valueInSubgraph2 = storage.get(presetKey)
                    input
                }

                edge(nodeStart forwardTo readNode)
                edge(readNode forwardTo nodeFinish)
            }

            edge(nodeStart forwardTo setupNode)
            edge(setupNode forwardTo subgraph1)
            edge(subgraph1 forwardTo subgraph2)
            edge(subgraph2 forwardTo nodeFinish)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("done").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = testStrategy,
            toolRegistry = ToolRegistry { }
        )

        agent.run("test input", null)

        assertEquals("preset", valueInSubgraph1, "Subgraph1 should see the preset storage value")
        assertEquals("preset", valueInSubgraph2, "Subgraph2 should see the preset storage value")
    }

    @Test
    fun test_storage_accessible_via_event_handler_context() = runTest {
        val storageKey: AIAgentStorageKey<String> = createStorageKey("event_key")
        val collectedValues = mutableListOf<String?>()

        val testStrategy = strategy<String, String>("event_handler_storage") {
            val subgraph1 by subgraph<String, String>("subgraph1") {
                val writeNode by node<String, String>("write") { input ->
                    storage.set(storageKey, "from_subgraph1")
                    input
                }
                val llmCall by nodeLLMRequest()

                edge(nodeStart forwardTo writeNode)
                edge(writeNode forwardTo llmCall)
                edge(llmCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val subgraph2 by subgraph<String, String>("subgraph2") {
                val writeNode by node<String, String>("write2") { input ->
                    storage.set(storageKey, "from_subgraph2")
                    input
                }

                edge(nodeStart forwardTo writeNode)
                edge(writeNode forwardTo nodeFinish)
            }

            edge(nodeStart forwardTo subgraph1)
            edge(subgraph1 forwardTo subgraph2)
            edge(subgraph2 forwardTo nodeFinish)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("done").asDefaultResponse
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            strategy = testStrategy,
            toolRegistry = ToolRegistry { }
        ) {
            install(EventHandler) {
                onSubgraphExecutionCompleted { eventContext ->
                    collectedValues.add(eventContext.context.storage.get(storageKey))
                }
            }
        }

        agent.run("test input", null)

        assertEquals(2, collectedValues.size, "Should have collected values from 2 subgraph completions")
        assertEquals("from_subgraph1", collectedValues[0], "First subgraph completion should see its own value")
        assertEquals("from_subgraph2", collectedValues[1], "Second subgraph completion should see updated value")
    }
}
