import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class PersistenceRestoreStrategyTests {
    private val serializer = KotlinxSerializer()

    @Test
    fun `rollback Default resumes from checkpoint node`() = runTest {
        val provider = InMemoryPersistenceStorageProvider()

        val agentId = "persistency-restore-default"
        val sessionId = "persistency-restore-default"

        val checkpoint = AgentCheckpointData(
            checkpointId = "chk-1",
            createdAt = Clock.System.now(),
            nodePath = "$agentId/restore-strategy/Node2",
            lastInput = JSONPrimitive("input-for-node2"),
            messageHistory = listOf(Message.Assistant("History Before", ResponseMetaInfo(Clock.System.now()))),
            version = 0L
        )

        provider.saveCheckpoint(sessionId, checkpoint)

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = restoreStrategyGraph(),
            agentConfig = AIAgentConfig(
                prompt = prompt("test") { system("You are a test agent.") },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            ),
        ) {
            install(Persistence) {
                storage = provider
            }
        }

        val result = agent.run("start", sessionId = sessionId)

        assertEquals(
            "History: History Before\n" +
                "Node 2 output",
            result
        )
    }
}
