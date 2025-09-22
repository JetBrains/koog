import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PersistencyRunsTwiceTest {

    @Test
    fun `agent runs to end and on second run starts from beginning again`() = runTest {
        // Arrange
        val provider = InMemoryPersistencyStorageProvider("persistency-test-agent")

        val testCollector = TestAgentLogsCollector()

        val agent = AIAgent(
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
            install(Persistency) {
                storage = provider
                enableAutomaticPersistency = true
            }
        }

        // Act: first run
        agent.run("Start the test")

        // Assert
        assertEquals(
            listOf(
                "First Step",
                "Second Step"
            ),
            testCollector.logs
        )

        // The latest checkpoint must be a tombstone after finishing
        val latest1 = provider.getLatestCheckpoint()
        assertNotNull(latest1)
        assertEquals(true, latest1!!.isTombstone())

        // Act: second run with the same storage (should not resume mid-graph)
        agent.run("Start the test2")

        // And still ends with a tombstone as the latest checkpoint
        val latest2 = provider.getLatestCheckpoint()
        assertNotNull(latest2)
        assertEquals(true, latest2!!.isTombstone())
    }

    @Test
    fun `agent fails on the first run and second run running successfully`() = runTest {
        val provider = InMemoryPersistencyStorageProvider("persistency-test-agent")

        val testCollector = TestAgentLogsCollector()

        val agent = AIAgent(
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
            install(Persistency) {
                storage = provider
                enableAutomaticPersistency = true
            }
        }

        // Act: first run
        val result = runCatching { agent.run("Start the test") }

        // Assert: first run fails
        assert(result.isFailure)

        assertEquals(
            listOf(
                "First Step",
                "Second Step"
            ),
            testCollector.logs
        )

        assertEquals(2, provider.getCheckpoints().size)
        testCollector.logs.clear()
        val secondRunResult = runCatching { agent.run("Start the test") }

        // Assert: second run is successful
        assert(secondRunResult.isSuccess)
        assertEquals(
            listOf(
                "Second Step",
                "Second try successful",
            ),
            testCollector.logs
        )
        assertEquals(4, provider.getCheckpoints().filter { !it.isTombstone() }.size)
    }
}
