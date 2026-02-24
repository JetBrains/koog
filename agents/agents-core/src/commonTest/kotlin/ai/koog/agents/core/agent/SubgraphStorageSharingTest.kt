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
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
    fun test_storage_modification_in_first_subgraph_visible_in_second() = runTest {
        val counterKey: AIAgentStorageKey<Int> = createStorageKey("counter")

        var counterInSubgraph2: Int? = null

        val testStrategy = strategy<String, String>("modify_storage") {
            val subgraph1 by subgraph<String, String>("subgraph1") {
                val incrementNode by node<String, String>("increment") { input ->
                    val current = storage.get(counterKey) ?: 0
                    storage.set(counterKey, current + 10)
                    input
                }
                val llmCall by nodeLLMRequest()

                edge(nodeStart forwardTo incrementNode)
                edge(incrementNode forwardTo llmCall)
                edge(llmCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val subgraph2 by subgraph<String, String>("subgraph2") {
                val readNode by node<String, String>("readCounter") { input ->
                    counterInSubgraph2 = storage.get(counterKey)
                    input
                }

                edge(nodeStart forwardTo readNode)
                edge(readNode forwardTo nodeFinish)
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
        )

        agent.run("test input", null)

        assertEquals(10, counterInSubgraph2, "Subgraph2 should see the counter value set by subgraph1")
    }

    @Test
    fun test_storage_keys_registry_returns_same_instance() = runTest {
        val key1: AIAgentStorageKey<String> = AIAgentStorageKeys.get("my_key")
        val key2: AIAgentStorageKey<String> = AIAgentStorageKeys.get("my_key")

        assertSame(key1, key2, "Registry should return the same key instance for the same name")
    }

    @Test
    fun test_storage_keys_registry_rejects_same_name_with_different_type() = runTest {
        // Clear registry state to avoid interference from other tests
        AIAgentStorageKeys.clear()

        AIAgentStorageKeys.get<String>("typed_key")

        assertFailsWith<IllegalArgumentException> {
            AIAgentStorageKeys.get<Int>("typed_key")
        }

        AIAgentStorageKeys.clear()
    }

    @Test
    fun test_storage_accessible_with_registry_keys_across_subgraphs() = runTest {
        // Simulates the user scenario: keys obtained from the registry in different subgraphs
        val testStrategy = strategy<String, String>("registry_keys") {
            val subgraph1 by subgraph<String, String>("subgraph1") {
                val writeNode by node<String, String>("write") { input ->
                    val key: AIAgentStorageKey<String> = AIAgentStorageKeys.get("registry_key")
                    storage.set(key, "registry_value")
                    input
                }
                val llmCall by nodeLLMRequest()

                edge(nodeStart forwardTo writeNode)
                edge(writeNode forwardTo llmCall)
                edge(llmCall forwardTo nodeFinish onAssistantMessage { true })
            }

            val subgraph2 by subgraph<String, String>("subgraph2") {
                val readNode by node<String, String>("read") { _ ->
                    // Obtain the key from the registry — same instance as in subgraph1
                    val key: AIAgentStorageKey<String> = AIAgentStorageKeys.get("registry_key")
                    val value = storage.get(key)
                    assertNotNull(value, "Should find value using a registry key obtained in a different subgraph")
                    assertEquals("registry_value", value)
                    value
                }

                edge(nodeStart forwardTo readNode)
                edge(readNode forwardTo nodeFinish)
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
        )

        val result = agent.run("test input", null)
        assertEquals("registry_value", result)
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


object AIAgentStorageKeys {
    private val registry = mutableMapOf<String, Pair<AIAgentStorageKey<*>, KType>>()

    fun clear() {
        registry.clear()
    }

    internal inline fun <reified T : Any> get(name: String): AIAgentStorageKey<T> {
        val requestedType = typeOf<T>()
        val (key, existingType) = registry.getOrPut(name) {
            AIAgentStorageKey<T>(name) to requestedType
        }
        require(existingType == requestedType) {
            "Key '$name' was registered as $existingType but requested as $requestedType"
        }
        @Suppress("UNCHECKED_CAST")
        return key as AIAgentStorageKey<T>
    }
}
