package ai.koog.agents.features.sql.providers

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.DockerAvailableCondition
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PostgresPersistenceAgentRunTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system("You are a test agent.")
        },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20
    )

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun provider(): PostgresPersistenceStorageProvider {
        val db: Database = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        return PostgresPersistenceStorageProvider(database = db)
    }

    private fun straightForwardGraphNoCheckpoint() = strategy("straight-forward") {
        val node1 by simpleNode(
            name = "Node1",
            output = "Node 1 output"
        )
        val node2 by simpleNode(
            name = "Node2",
            output = "Node 2 output"
        )
        val historyNode by collectHistoryNode("History Node")

        edge(nodeStart forwardTo node1)
        edge(node1 forwardTo node2)
        edge(node2 forwardTo historyNode)
        edge(historyNode forwardTo nodeFinish)
    }

    private fun AIAgentSubgraphBuilderBase<*, *>.simpleNode(
        name: String? = null,
        output: String,
    ): AIAgentNodeDelegate<String, String> = node(name) {
        llm.writeSession {
            appendPrompt { user { text(output) } }
        }
        return@node it + "\n" + output
    }

    private fun AIAgentSubgraphBuilderBase<*, *>.collectHistoryNode(
        name: String? = null,
    ): AIAgentNodeDelegate<String, String> = node(name) {
        return@node llm.readSession {
            val history = this.prompt.messages.joinToString("\n") { it.content }
            return@readSession "History: $history"
        }
    }

    @Test
    fun `postgres pre seeded valid checkpoint chain respected and agent starts fresh`() = runBlocking {
        val pgStorage = provider()
        pgStorage.migrate()
        preSeedValidCheckpointChainTest(pgStorage)
    }

    @Test
    fun `in memory pre seeded valid checkpoint chain respected and agent starts fresh`() =
        preSeedValidCheckpointChainTest(InMemoryPersistenceStorageProvider())

    @Test
    fun `preseeded chain finished with tombstone and extra checkpoint on top is used as latest`() = runBlocking {
        val pgStorage = provider()
        pgStorage.migrate()
        preSeedFinishedChainPlusUnfinishedTest(pgStorage)
    }

    @Test
    fun `in memory preseeded chain finished with tombstone and extra checkpoint on top is used as latest`() =
        preSeedFinishedChainPlusUnfinishedTest(InMemoryPersistenceStorageProvider())

    @Test
    fun `in memory pre seeded single checkpoint respected and agent starts fresh`() = runBlocking {
        preSeedSingleCheckpoint(InMemoryPersistenceStorageProvider())
    }

    @Test
    fun `postgres pre seeded single checkpoint respected and agent starts fresh`() = runBlocking {
        val pgStorage = provider()
        pgStorage.migrate()
        preSeedSingleCheckpoint(pgStorage)
    }

    fun preSeedValidCheckpointChainTest(provider: PersistenceStorageProvider<*>) = runBlocking<Unit> {
        val agentId = "pg-agent-preseed-1"
        val time = Clock.System.now()

        val cp1 = createTestCheckpoint("cp-1", time = time, version = 0)
        val cp2 = createTestCheckpoint("cp-2", version = cp1.version + 1, time = time)
        val tomb = tombstoneCheckpoint(time = Clock.System.now(), version = cp2.version + 1)

        // Save in order: cp1 -> cp2 -> tombstone
        provider.saveCheckpoint(agentId, cp1)
        provider.saveCheckpoint(agentId, cp2)
        provider.saveCheckpoint(agentId, tomb)

        // Pre-run assertions about the chain
        val seeded = provider.getLatestCheckpoint(agentId)
        seeded?.isTombstone() shouldBe true

        // Create agent with persistence but without automatic persistence to keep seeded chain intact
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            id = agentId
        ) {
            install(Persistence) {
                storage = provider
                enableAutomaticPersistence = false
            }
        }

        // Act: run
        val output = agent.run("Start the test")
        val latest = provider.getLatestCheckpoint(agentId)

        output shouldBe "History: You are a test agent.\n" +
            "Node 1 output\n" +
            "Node 2 output"

        latest?.isTombstone() shouldBe true
    }

    fun preSeedFinishedChainPlusUnfinishedTest(provider: PersistenceStorageProvider<*>) = runBlocking<Unit> {
        val agentId = "pg-agent-preseed-2"
        val time = Clock.System.now()

        val cp1 = createTestCheckpoint("cp-1", version = 0, time = time)
        val cp2 = createTestCheckpoint("cp-2", version = cp1.version + 1, time = time)
        val tomb = tombstoneCheckpoint(time = Clock.System.now(), version = cp2.version + 1)
        val cp3 = createTestCheckpoint("cp-3", version = tomb.version + 1, time = time, "Node1")

        // Save in order: cp1 -> cp2 -> tombstone -> cp3
        provider.saveCheckpoint(agentId, cp1)
        provider.saveCheckpoint(agentId, cp2)
        provider.saveCheckpoint(agentId, tomb)
        provider.saveCheckpoint(agentId, cp3)

        // Pre-run: latest checkpoint must be cp3
        val latestBefore = provider.getLatestCheckpoint(agentId)
        assertEquals("cp-3", latestBefore?.checkpointId, "Latest checkpoint must be the one on top of tombstone")

        // Create agent with persistence; keep auto persistence off to avoid mutating preseeded data
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            id = agentId
        ) {
            install(Persistence) {
                storage = provider
                enableAutomaticPersistence = false
            }
        }

        // Act: run
        val output = agent.run("Start the test")

        output shouldBeEqual "History: You are a test agent.\n" +
            "Node 1 output\n" +
            "Node 2 output\n" +
            "Node 1 output\n" +
            "Node 2 output"

        // Post-run: latest should still be cp3 since we did not persist new checkpoints
        val latestAfter = provider.getLatestCheckpoint(agentId)

        latestAfter?.checkpointId shouldBe "cp-3"
    }

    fun preSeedSingleCheckpoint(provider: PersistenceStorageProvider<*>) = runBlocking<Unit> {
        val agentId = "pg-agent-preseed-3"
        val time = Clock.System.now()

        val cp1 = createTestCheckpoint("cp-1", version = 0, time = time, nodeId = "Node1")

        // Save single checkpoint
        provider.saveCheckpoint(agentId, cp1)

        // Pre-run assertions about the chain
        val seeded = provider.getLatestCheckpoint(agentId)
        assertEquals("cp-1", seeded?.checkpointId, "Latest checkpoint must be the single pre-seeded one")

        // Create agent with persistence but without automatic persistence to keep seeded chain intact
        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = straightForwardGraphNoCheckpoint(),
            agentConfig = agentConfig,
            id = agentId
        ) {
            install(Persistence) {
                storage = provider
                enableAutomaticPersistence = false
            }
        }

        // Act: run
        val output = agent.run("Start the test")
        val latest = provider.getLatestCheckpoint(agentId)

        output shouldBe "History: You are a test agent.\n" +
            "Node 1 output\n" +
            "Node 2 output\n" +
            "Node 1 output\n" +
            "Node 2 output"

        latest?.checkpointId shouldBe "cp-1"
    }

    private fun createTestCheckpoint(
        id: String,
        version: Long,
        time: Instant,
        nodeId: String = "Node2"
    ): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = id,
            createdAt = time,
            nodeId = nodeId,
            lastInput = JsonPrimitive("Test input"),
            messageHistory = listOf(
                Message.System("You are a test agent.", RequestMetaInfo(time)),
                Message.User("Node 1 output", RequestMetaInfo(time)),
                Message.Assistant("Node 2 output", ResponseMetaInfo(time))
            ),
            version = version
        )
    }
}
