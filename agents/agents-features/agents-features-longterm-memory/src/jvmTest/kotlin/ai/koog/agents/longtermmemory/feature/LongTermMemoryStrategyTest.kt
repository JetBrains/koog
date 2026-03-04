package ai.koog.agents.longtermmemory.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest
import ai.koog.agents.longtermmemory.retrieval.SearchResult
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic test for LongTermMemory feature installation with InMemoryRecordStorage.
 * Verifies that users can install the feature, provide their own storage implementations,
 * and call `search` and `add` methods from those storages within strategy nodes.
 */
@OptIn(ExperimentalAgentsApi::class)
class LongTermMemoryStrategyTest {
    private val myNamespace = "ns"
    private val serializer = KotlinxSerializer()

    @Test
    @Timeout(30)
    fun `install LongTermMemory with custom storages and use search and add in strategy`() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Done").asDefaultResponse
        }

        val memoryStorage = InMemoryRecordStorage(myNamespace)

        val searchResults = mutableListOf<SearchResult>()

        val strategy = strategy<String, String>(
            "ltm-basic-test",
            toolSelectionStrategy = ToolSelectionStrategy.NONE
        ) {
            val addRecords by node<String, Unit> {
                withLongTermMemory {
                    this.ingestionStorage?.add(
                        listOf(
                            MemoryRecord(content = "Kotlin is a modern programming language"),
                            MemoryRecord(content = "Java is a widely used language"),
                            MemoryRecord(content = "Kotlin runs on the JVM")
                        ),
                        myNamespace
                    )
                }
            }

            val searchRecords by node<Unit, Unit> {
                searchResults += withLongTermMemory {
                    this.retrievalStorage?.search(
                        KeywordSearchRequest(query = "Kotlin", limit = 10),
                        myNamespace
                    ) ?: emptyList()
                }
            }

            edge(nodeStart forwardTo addRecords)
            edge(addRecords forwardTo searchRecords)
            edge(searchRecords forwardTo nodeFinish transformed { "Done" })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") { system("You are a helpful assistant") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig
        ) {
            install(LongTermMemory) {
                retrieval {
                    storage = memoryStorage
                }
                ingestion {
                    storage = memoryStorage
                }
            }
        }

        agent.run("test input", null)

        assertEquals(3, memoryStorage.size())
        assertEquals(2, searchResults.size)
        assertTrue(searchResults.all { it.record.content.contains("Kotlin") })
    }
}
