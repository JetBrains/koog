import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class PersistenceRunsTwiceTest {

    @Test
    @Ignore
    fun `agent runs to end and on second run starts from beginning again`() = runTest {
        // Arrange
        val provider = InMemoryPersistenceStorageProvider()

        val testCollector = TestAgentLogsCollector()

        val agentService = AIAgentService(
            promptExecutor = getMockExecutor {
                // No LLM calls needed for this test; nodes write directly to the prompt/history
            },
            strategy = loggingGraphStrategy(testCollector),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("You are a test agent.") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            ),
        ) {
            install(Persistence) {
                storage = provider
                enableAutomaticPersistence = true
            }
        }

        val firstAgent = agentService.createAgent(id = "SAME_ID")
        val agentId1 = "SAME_ID"

        // Act: first run
        firstAgent.run("Start the test")

        // Assert
        testCollector.logs() shouldContainExactly listOf(
            "First Step",
            "Second Step"
        )

        // The latest checkpoint must be a tombstone after finishing

        await.until {
            runBlocking {
                provider.getLatestCheckpoint(agentId1)?.isTombstone() == true
            }
        }

        val firstCheckpoint = provider.getLatestCheckpoint(agentId1)

        val secondAgent = agentService.createAgent(id = "SAME_ID")

        // Act: second run with the same storage (should not resume mid-graph)
        secondAgent.run("Start the test2")

        // And still ends with a tombstone as the latest checkpoint
        await.until {
            runBlocking {
                val latest2 = provider.getLatestCheckpoint(agentId1)
                latest2?.isTombstone() == true
                latest2 != firstCheckpoint
            }
        }
    }

    @Test
    @Ignore
    fun `agent fails on the first run and second run running successfully`() = runTest {
        val provider = InMemoryPersistenceStorageProvider()

        val testCollector = TestAgentLogsCollector()

        val agentService = AIAgentService(
            promptExecutor = getMockExecutor {
                // No LLM calls needed for this test; nodes write directly to the prompt/history
            },
            strategy = loggingGraphForRunFromSecondTry(testCollector),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("You are a test agent.") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            ),
        ) {
            install(Persistence) {
                storage = provider
                enableAutomaticPersistence = true
                rollbackStrategy = RollbackStrategy.Default
            }
        }

        val agentId = "100500"

        // Act: first run
        val result = runCatching { agentService.createAgentAndRun("Start the test", id = agentId) }

        // Assert: first run fails
        assert(result.isFailure)

        testCollector.logs() shouldContainExactly listOf(
            "First Step",
            "Second Step"
        )

        await.until {
            runBlocking {
                provider.getCheckpoints(agentId).size == 2
            }
        }

        // Clear the collector to isolate the second run
        testCollector.clear()

        val secondAgent = agentService.createAgent(id = agentId)

        val secondRunResult = runCatching { secondAgent.run("Start the test") }

        // Assert: second run is successful
        assert(secondRunResult.isSuccess)
        testCollector.logs() shouldContainExactly listOf(
            "Second Step",
            "Second try successful",
        )

        await.until {
            runBlocking {
                provider.getCheckpoints(agentId).filter { !it.isTombstone() }.size == 4
            }
        }
    }
}
