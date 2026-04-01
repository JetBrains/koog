package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.session.AIAgentSessionInputs
import ai.koog.agents.core.dsl.builder.forwardTo
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
            sessionInputs = AIAgentSessionInputs(storage = storage),
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
            sessionInputs = AIAgentSessionInputs(storage = AIAgentStorage()),
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
            sessionInputs = AIAgentSessionInputs(storage = storage),
        )

        // The write node overwrites the pre-populated value
        assertEquals("greeting=written-in-node", output)
    }

    @Test
    fun testPrePopulatedStorageDoesNotMutateOriginal() = runTest {
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
            sessionInputs = AIAgentSessionInputs(storage = externalStorage),
        )

        // Original storage should not be mutated by the node's write
        assertEquals("original", externalStorage.get(greetingKey))
        assertNull(externalStorage.get(AIAgentStorageKey<String>("written-in-node")))
    }
}
