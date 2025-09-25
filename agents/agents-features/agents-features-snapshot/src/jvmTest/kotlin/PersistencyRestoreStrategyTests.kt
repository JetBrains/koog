import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistencyRestoreStrategyTests {
    @Test
    fun `rollback Default resumes from checkpoint node`() = runTest {
        val provider = InMemoryPersistencyStorageProvider("persistency-restore-default")

        val checkpoint = AgentCheckpointData(
            checkpointId = "chk-1",
            createdAt = Clock.System.now(),
            nodeId = "Node2",
            lastInput = JsonPrimitive("input-for-node2"),
            messageHistory = listOf(Message.Assistant("History Before", ResponseMetaInfo(Clock.System.now()))),
        )

        provider.saveCheckpoint(checkpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = restoreStrategyGraph(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("You are a test agent.") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            ),
        ) {
            install(Persistency) {
                storage = provider
                // We only need restore on start; automatic persistency doesn't matter here
                enableAutomaticPersistency = false
                rollbackStrategy = RollbackStrategy.Default
            }
        }

        val result = agent.run("start")

        assertEquals(
            "History: History Before\n" +
                "Node 2 output",
            result
        )
    }

    @Test
    fun `rollback MessageHistoryOnly starts from beginning`() = runTest {
        val provider = InMemoryPersistencyStorageProvider("persistency-restore-history-only")

        val checkpoint = AgentCheckpointData(
            checkpointId = "chk-1",
            createdAt = Clock.System.now(),
            nodeId = "Node2",
            lastInput = JsonPrimitive("input-for-node2"),
            messageHistory = listOf(Message.Assistant("History Before", ResponseMetaInfo(Clock.System.now()))),
        )

        provider.saveCheckpoint(checkpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = restoreStrategyGraph(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("You are a test agent.") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            ),
        ) {
            install(Persistency) {
                storage = provider
                enableAutomaticPersistency = false
                rollbackStrategy = RollbackStrategy.MessageHistoryOnly
            }
        }

        val result = agent.run("Agent Input")

        assertEquals(
            "History: History Before\n" +
                "Agent Input\n" +
                "Node 1 output\n" +
                "Node 2 output",
            result
        )
    }
}
