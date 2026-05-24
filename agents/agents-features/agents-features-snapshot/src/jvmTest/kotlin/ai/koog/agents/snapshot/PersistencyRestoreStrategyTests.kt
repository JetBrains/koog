package ai.koog.agents.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistenceRestoreStrategyTests {
    private val serializer = KotlinxSerializer()

    @Test
    fun `rollback Default resumes from checkpoint node`() = runTest {
        val provider = InMemoryPersistenceStorageProvider()

        val agentId = "persistency-restore-default"
        val sessionId = "persistency-restore-default"

        val checkpoint = AgentCheckpointData(
            checkpointId = "chk-1",
            createdAt = KoogClock.System.now(),
            messageHistory = listOf(
                Message.Assistant("History Before", ResponseMetaInfo(KoogClock.System.now())),
                Message.User("Node 2 output", RequestMetaInfo(KoogClock.System.now()))
            ),
            version = 0L,
            graphProperties = GraphCheckpointProperties(
                nodePath = "$agentId/restore-strategy/Node2",
                lastOutput = JSONPrimitive("Node 2 output")
            ),
            plannerProperties = null,
            properties = null,
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
