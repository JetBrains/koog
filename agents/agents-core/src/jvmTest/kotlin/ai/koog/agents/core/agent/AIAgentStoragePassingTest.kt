package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.session.AdditionalInputs
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class AIAgentStoragePassingTest {
    private val serializer = KotlinxSerializer()

    companion object {
        val greetingKey = AIAgentStorageKey<String>("greeting")
        val counterKey = AIAgentStorageKey<Int>("counter")
    }

    private val agentConfig = AIAgentConfig(
        prompt = prompt("test") { system("You are a test agent.") },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 10
    )

    private fun storageReadingStrategy() = strategy("storage-test") {
        val readNode by node<String, String>("readStorage") {
            val greeting = storage.get(greetingKey)
            val counter = storage.get(counterKey)
            "greeting=$greeting, counter=$counter"
        }

        edge(nodeStart forwardTo readNode)
        edge(readNode forwardTo nodeFinish)
    }

    private fun storageWriteAndReadStrategy() = strategy("storage-write-read") {
        val writeNode by node<String, String>("writeStorage") {
            storage.set(greetingKey, "written-in-node")
            "wrote"
        }

        val readNode by node<String, String>("readStorage") {
            val greeting = storage.get(greetingKey)
            "greeting=$greeting"
        }

        edge(nodeStart forwardTo writeNode)
        edge(writeNode forwardTo readNode)
        edge(readNode forwardTo nodeFinish)
    }

    @Suppress("DEPRECATION")
    private fun legacyStoreApiStrategy() = strategy("legacy-store-api") {
        val writeNode by node<String, String>("writeLegacyStorage") {
            store(greetingKey, "legacy-value")
            val stored = storage.get(greetingKey)
            "greeting=$stored"
        }

        val removeNode by node<String, String>("removeLegacyStorage") {
            val beforeRemove = get<String>(greetingKey)
            val removed = remove(greetingKey)
            val afterRemove = storage.get(greetingKey)
            "before=$beforeRemove, removed=$removed, after=$afterRemove"
        }

        edge(nodeStart forwardTo writeNode)
        edge(writeNode forwardTo removeNode)
        edge(removeNode forwardTo nodeFinish)
    }

    private fun storageAddNewKeyStrategy() = strategy("storage-add-new-key") {
        val addNode by node<String, String>("addNewKey") {
            storage.set(counterKey, 99)
            "added"
        }

        edge(nodeStart forwardTo addNode)
        edge(addNode forwardTo nodeFinish)
    }

    private fun storageRemoveKeyStrategy() = strategy("storage-remove-key") {
        val removeNode by node<String, String>("removeKey") {
            storage.remove(greetingKey)
            "removed"
        }

        edge(nodeStart forwardTo removeNode)
        edge(removeNode forwardTo nodeFinish)
    }

    private fun storageWriteThenFailStrategy() = strategy("storage-write-then-fail") {
        val writeAndFailNode by node<String, String>("writeAndFail") {
            storage.set(greetingKey, "written-before-fail")
            error("boom")
        }

        edge(nodeStart forwardTo writeAndFailNode)
        edge(writeAndFailNode forwardTo nodeFinish)
    }

    private fun captureContextStrategy(
        capturedContexts: MutableList<AIAgentContext>
    ) = strategy("capture-context") {
        val captureNode by node<String, String>("captureStorage") {
            capturedContexts += this
            val greeting = storage.get(greetingKey)
            "greeting=$greeting"
        }

        edge(nodeStart forwardTo captureNode)
        edge(captureNode forwardTo nodeFinish)
    }

    @Test
    fun testSessionRunWithPrePopulatedStorage() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val storage = AIAgentStorage()
        storage.set(greetingKey, "hello from outside")
        storage.set(counterKey, 42)

        val session = agent.createSession("test-session")
        val output = session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(storage),
        )

        assertEquals("greeting=hello from outside, counter=42", output)
    }

    @Test
    fun testSessionRunWithoutStorageInputUsesEmptyStorage() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val session = agent.createSession("test-session")
        val output = session.run(
            input = "ignored",
        )

        assertEquals("greeting=null, counter=null", output)
    }

    @Test
    fun testSessionRunWithEmptyStorageInput() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val session = agent.createSession("test-session")
        val output = session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(AIAgentStorage()),
        )

        assertEquals("greeting=null, counter=null", output)
    }

    @Test
    fun testNodeCanOverwritePrePopulatedStorage() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageWriteAndReadStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val storage = AIAgentStorage()
        storage.set(greetingKey, "original")

        val session = agent.createSession("test-session")
        val output = session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(storage),
        )

        // The write node overwrites the pre-populated value
        assertEquals("greeting=written-in-node", output)
    }

    @Test
    fun testPrePopulatedStorageReceivesFinalStateAfterRun() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageWriteAndReadStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val externalStorage = AIAgentStorage()
        externalStorage.set(greetingKey, "original")

        val session = agent.createSession("test-session")
        session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(externalStorage),
        )

        assertEquals("written-in-node", externalStorage.get(greetingKey))
    }

    @Test
    fun testNewKeysWrittenInNodeAreReflectedBackToExternalStorage() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageAddNewKeyStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val externalStorage = AIAgentStorage()
        externalStorage.set(greetingKey, "keep-me")

        val session = agent.createSession("test-session")
        session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(externalStorage),
        )

        assertEquals("keep-me", externalStorage.get(greetingKey))
        assertEquals(99, externalStorage.get(counterKey))
    }

    @Test
    fun testKeysRemovedInNodeAreRemovedFromExternalStorage() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageRemoveKeyStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val externalStorage = AIAgentStorage()
        externalStorage.set(greetingKey, "to-be-removed")
        externalStorage.set(counterKey, 7)

        val session = agent.createSession("test-session")
        session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(externalStorage),
        )

        assertNull(externalStorage.get(greetingKey))
        assertEquals(7, externalStorage.get(counterKey))
    }

    @Test
    fun testExternalStorageReceivesWritesEvenWhenAgentFails() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageWriteThenFailStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val externalStorage = AIAgentStorage()
        externalStorage.set(greetingKey, "original")

        val session = agent.createSession("test-session")
        assertFailsWith<IllegalStateException> {
            session.run(
                input = "ignored",
                sessionInputs = AdditionalInputs.Storage(externalStorage),
            )
        }

        assertEquals("written-before-fail", externalStorage.get(greetingKey))
    }

    @Test
    fun testSessionRunDoesNotLeakStorageBetweenRuns() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = storageReadingStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val firstStorage = AIAgentStorage()
        firstStorage.set(greetingKey, "first-run")
        firstStorage.set(counterKey, 1)

        val session = agent.createSession("test-session")
        val firstOutput = session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(firstStorage),
        )
        val secondOutput = session.run(input = "ignored")

        assertEquals("greeting=first-run, counter=1", firstOutput)
        assertEquals("greeting=null, counter=null", secondOutput)
    }

    @Test
    fun testSessionContextUsesIndependentStorageInstance() = runTest {
        val capturedContexts = mutableListOf<AIAgentContext>()
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = captureContextStrategy(capturedContexts),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val externalStorage = AIAgentStorage()
        externalStorage.set(greetingKey, "hello")

        val session = agent.createSession("test-session")
        session.run(
            input = "ignored",
            sessionInputs = AdditionalInputs.Storage(externalStorage),
        )

        assertEquals(1, capturedContexts.size)
        assertEquals("hello", capturedContexts.single().storage.get(greetingKey))
        assertNotSame(capturedContexts.single().storage, externalStorage)
    }

    @Test
    fun testLegacyStoreApiRemainsIndependentFromConcurrentStorageApi() = runTest {
        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) { },
            strategy = legacyStoreApiStrategy(),
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.EMPTY,
        )

        val session = agent.createSession("test-session")
        val output = session.run(input = "ignored")

        assertEquals("before=legacy-value, removed=true, after=null", output)
    }
}
