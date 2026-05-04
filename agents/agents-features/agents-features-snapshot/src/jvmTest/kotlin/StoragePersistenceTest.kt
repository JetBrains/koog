import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.rootContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.execution.path
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.persisted
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@Serializable
private data class RetryStats(val attempts: Int, val lastReason: String)

private val RetryCounterKey: AIAgentStorageKey<Int> = createStorageKey("retry_counter")
private val RetryStatsKey: AIAgentStorageKey<RetryStats> = createStorageKey("retry_stats")
private val SecretKey: AIAgentStorageKey<String> = createStorageKey("secret_unregistered")

class StoragePersistenceTest {
    private val systemPrompt = "You are a test agent."
    private val agentConfig = AIAgentConfig(
        prompt = prompt("test") { system(systemPrompt) },
        model = OllamaModels.Meta.LLAMA_3_2,
        maxAgentIterations = 20,
    )
    private val toolRegistry = ToolRegistry { tool(SayToUser) }
    private val serializer = KotlinxSerializer()

    @Test
    fun checkpointSerializationRoundTripsStorageField() {
        val now = Clock.System.now()
        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-storage",
            createdAt = now,
            nodePath = "NodeA",
            lastOutput = JSONPrimitive("done"),
            messageHistory = listOf(
                Message.User("hi", metaInfo = RequestMetaInfo(now)),
                Message.Assistant("hello", metaInfo = ResponseMetaInfo(now)),
            ),
            version = 0L,
            storage = JSONObject(
                mapOf(
                    "retry_counter" to JSONPrimitive(3),
                    "retry_stats" to JSONObject(
                        mapOf(
                            "attempts" to JSONPrimitive(2),
                            "lastReason" to JSONPrimitive("rate-limit"),
                        )
                    ),
                )
            ),
        )

        val json = PersistenceUtils.defaultCheckpointJson
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), checkpoint)
        val restored = json.decodeFromString(AgentCheckpointData.serializer(), serialized)

        assertEquals(checkpoint, restored)
        assertEquals(checkpoint.storage, restored.storage)
    }

    @Test
    fun storageFieldOmittedWhenNull() {
        val now = Clock.System.now()
        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-no-storage",
            createdAt = now,
            nodePath = "NodeA",
            lastOutput = JSONPrimitive("done"),
            messageHistory = emptyList(),
            version = 0L,
        )

        val serialized = PersistenceUtils.defaultCheckpointJson
            .encodeToString(AgentCheckpointData.serializer(), checkpoint)

        // Older checkpoints (before the storage field) must still encode without an empty `storage` member.
        assertTrue("\"storage\"" !in serialized, "Serialized JSON should omit storage when null")
        assertNull(checkpoint.storage)
    }

    @Test
    fun runFromCheckpointRestoresRegisteredStorageEntries() = runTest {
        val sessionId = "test-session-storage"
        val now = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-with-storage",
            createdAt = now,
            nodePath = path(sessionId, "storage-strategy", "Node1"),
            lastOutput = JSONPrimitive("Node 1 output"),
            messageHistory = listOf(
                Message.User("ignored", metaInfo = RequestMetaInfo(now)),
            ),
            version = 0L,
            storage = JSONObject(
                mapOf(
                    "retry_counter" to JSONPrimitive(7),
                    "retry_stats" to JSONObject(
                        mapOf(
                            "attempts" to JSONPrimitive(2),
                            "lastReason" to JSONPrimitive("timeout"),
                        )
                    ),
                )
            ),
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingGraph(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val output = Persistence.runFromCheckpoint(
            agent = agent,
            agentInput = "ignored",
            checkpoint = checkpoint,
            sessionId = sessionId,
            persistedKeys = listOf(
                persisted(RetryCounterKey, Int.serializer()),
                persisted(RetryStatsKey, RetryStats.serializer()),
            ),
        )

        // The `storageReader` node, which runs after restore at Node2, observes the restored values
        // through the same key instances declared at file scope - this is what proves identity-keyed
        // restoration works.
        assertEquals("retry=7 attempts=2 reason=timeout", output)
    }

    @Test
    fun runFromCheckpointSkipsUnregisteredStorageEntries() = runTest {
        val sessionId = "test-session-unreg"
        val now = Clock.System.now()

        val checkpoint = AgentCheckpointData(
            checkpointId = "cp-unreg",
            createdAt = now,
            nodePath = path(sessionId, "storage-strategy", "Node1"),
            lastOutput = JSONPrimitive("Node 1 output"),
            messageHistory = emptyList(),
            version = 0L,
            storage = JSONObject(
                mapOf(
                    "retry_counter" to JSONPrimitive(4),
                    "secret_unregistered" to JSONPrimitive("should-not-restore"),
                )
            ),
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingGraph { ctx ->
                val retry = ctx.rootContext().storage.get(RetryCounterKey)
                val secret = ctx.rootContext().storage.get(SecretKey)
                "retry=$retry secret=$secret"
            },
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )

        val output = Persistence.runFromCheckpoint(
            agent = agent,
            agentInput = "ignored",
            checkpoint = checkpoint,
            sessionId = sessionId,
            // Only the retry counter is registered; the unrelated entry must NOT leak into storage.
            persistedKeys = listOf(persisted(RetryCounterKey, Int.serializer())),
        )

        assertEquals("retry=4 secret=null", output)
    }

    @Test
    fun automaticPersistenceCapturesStorageAndRunFromCheckpointRestoresIt() = runTest {
        val storageProvider = InMemoryPersistenceStorageProvider()
        val sessionId = "auto-storage-run"

        // Run an agent whose first node writes to storage. Auto-persistence captures a checkpoint
        // after each user node, so the post-writer checkpoint must encode the storage entry under
        // the registered key.
        val firstAgent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = writerThenReaderGraph(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            install(Persistence) {
                storage = storageProvider
                persistedKeys = listOf(persisted(RetryCounterKey, Int.serializer()))
            }
        }
        firstAgent.run("seed", sessionId)

        val checkpoints = storageProvider.getCheckpoints(sessionId).filter { it.storage != null }
        val afterWriter = checkpoints.first { it.nodePath.endsWith("/writer") }
        assertEquals(JSONPrimitive(5), afterWriter.storage?.entries?.get("retry_counter"))

        // Resume into a fresh agent with the same strategy but no installed persistence. The
        // restored storage is what the reader observes when it executes after the resumed
        // execution point. This is the human-in-the-loop pattern from issue #1944: stash a
        // checkpoint when waiting for input, then resume later from it.
        val resumed = Persistence.runFromCheckpoint(
            agent = AIAgent(
                promptExecutor = getMockExecutor(serializer) { },
                strategy = writerThenReaderGraph(),
                agentConfig = agentConfig,
                toolRegistry = toolRegistry,
            ),
            agentInput = "ignored",
            checkpoint = afterWriter,
            sessionId = "resume-session",
            persistedKeys = listOf(persisted(RetryCounterKey, Int.serializer())),
        )

        assertEquals("restored=5", resumed)
    }
}

private fun storageReadingGraph(
    reader: suspend (AIAgentContext) -> String = { ctx ->
        val retry = ctx.rootContext().storage.get(RetryCounterKey)
        val stats = ctx.rootContext().storage.get(RetryStatsKey)
        "retry=$retry attempts=${stats?.attempts} reason=${stats?.lastReason}"
    },
) = strategy("storage-strategy") {
    val node1 by passthroughNode("Node1")
    val node2 by passthroughNode("Node2")
    val storageReader by node<String, String>("storageReader") { reader(this) }

    edge(nodeStart forwardTo node1)
    edge(node1 forwardTo node2)
    edge(node2 forwardTo storageReader)
    edge(storageReader forwardTo nodeFinish)
}

private fun writerThenReaderGraph() = strategy("auto-storage") {
    val writer by node<String, String>("writer") { input ->
        rootContext().storage.set(RetryCounterKey, 5)
        input
    }
    val reader by node<String, String>("reader") { _ ->
        "restored=${rootContext().storage.get(RetryCounterKey)}"
    }

    edge(nodeStart forwardTo writer)
    edge(writer forwardTo reader)
    edge(reader forwardTo nodeFinish)
}

private fun passthroughNode(name: String): AIAgentNodeDelegate<String, String> = node(name) { it }
