package ai.koog.agents.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersistenceRunsTwiceTest {
    private val serializer = KotlinxSerializer()

    @Test
    fun `agent runs to end and on second run starts from beginning again`() = runTest {
        // Arrange
        val provider = InMemoryPersistenceStorageProvider()

        val testCollector = TestAgentLogsCollector()

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") { system("You are a test agent.") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10,
        )

        val agent = GraphAIAgent(
            promptExecutor = getMockExecutor(serializer) {
                // No LLM calls needed for this test; nodes write directly to the prompt/history
            },
            strategy = loggingGraphStrategy(testCollector),
            agentConfig = agentConfig,
        ) {
            install(Persistence) {
                storage = provider
            }
        }

        val agentId1 = "SAME_ID"

        // Act: first run
        agent.run("Start the test", agentId1)

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
        // Act: second run with the same storage (should not resume mid-graph)
        agent.run("Start the test2", agentId1)

        // And still ends with a tombstone as the latest checkpoint
        await.until {
            runBlocking {
                val latest2 = provider.getLatestCheckpoint(agentId1)
                latest2 != firstCheckpoint
            }
        }
    }

    @Test
    fun `agent fails on the first run and second run running successfully`() = runTest {
        val provider = InMemoryPersistenceStorageProvider()
        val testCollector = TestAgentLogsCollector()

        val agent = AIAgent(
            promptExecutor = getMockExecutor(serializer) {
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
            }
        }

        val sessionId = "test-agent-id"

        // Act: first run
        val result = runCatching { agent.run("Start the test", sessionId = sessionId) }

        // Assert: first run fails
        assert(result.isFailure)

        testCollector.logs() shouldContainExactly listOf(
            "First Step",
            "Second Step"
        )

        await.until {
            runBlocking {
                val checkpoints = provider.getCheckpoints(sessionId)
                println(checkpoints)
                checkpoints.size == 2
            }
        }

        // Clear the collector to isolate the second run
        testCollector.clear()

        agent.run("Start the test", sessionId = sessionId)

        testCollector.logs() shouldContainExactly listOf(
            "Second try successful",
        )

        await.until {
            runBlocking {
                provider.getCheckpoints(sessionId).filter { !it.isTombstone() }.size == 3
            }
        }
    }

    @Serializable
    data class CustomInput(
        val question: String
    )

    @Serializable
    data class CustomOutput(
        val x: Int,
        val y: String
    )

    object GuesserTool : Tool<CustomInput, CustomOutput>(
        argsType = typeToken<CustomInput>(),
        resultType = typeToken<CustomOutput>(),
        name = "guesser",
        description = "Very important tool. You MUST call it ALWAYS and exactly once!"
    ) {
        override suspend fun execute(args: CustomInput): CustomOutput = CustomOutput(x = 100500, y = "Hidden Value")

        override fun encodeResultToString(result: CustomOutput, serializer: JSONSerializer): String {
            return "encoded_result(\"${result.y}\")"
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `test ReceivedToolResult contains resultObject that is NOT persisted via Persistence feature`() = runTest {
        val checkpointStorage = InMemoryPersistenceStorageProvider()

        val promptExecutor = getMockExecutor {
            mockLLMToolCall(
                GuesserTool,
                CustomInput(question = "What is the secret value?")
            ) onRequestEquals "Tell me the secret!"

            mockLLMAnswer("Done! Value is Hidden Value") onRequestEquals "encoded_result(\"Hidden Value\")"
        }

        val events = mutableListOf<String>()

        var numberOfRun = 0

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = """
                    You are a helpful assistant.
                    You must use `guesser` tool to answer all questions.
            """.trimIndent(),
            toolRegistry = ToolRegistry {
                tool(GuesserTool)
            },
            strategy = singleRunStrategy(parallelTools = false),
            llmModel = AnthropicModels.Sonnet_4_5
        ) {
            install(Persistence) {
                storage = checkpointStorage
                enableAutomaticPersistence = true
            }

            handleEvents {
                onAgentStarting {
                    numberOfRun++
                }
                onToolCallStarting { ctx ->
                    events += "onToolCallStarting(${ctx.toolName}, args=${ctx.toolArgs})"
                }
                onNodeExecutionCompleted { ctx ->
                    if (ctx.node.name == "nodeExecuteTool") {
                        val toolResult = (ctx.output as ReceivedToolResults).toolResults.single()
                        // resultObject is set in the live result but must NOT survive persistence
                        assertNotNull(toolResult.resultObject)
                        events += "finished: nodeExecuteTool(tool=${toolResult.tool}, output=${toolResult.output})"
                    }
                }
                onNodeExecutionStarting { ctx ->
                    val input = ctx.input
                    if (input is ToolCalls) {
                        val toolCall = input.toolCalls.single()
                        events += "started: nodeExecuteTool(tool=${toolCall.tool}, content=${toolCall.args})"
                    }
                }
                onToolCallCompleted { ctx ->
                    events += "onToolCallCompleted(guesser, toolResult=${ctx.toolResult})"
                }
                onLLMCallStarting { ctx ->
                    val lastText = (ctx.prompt.messages.last() as? Message.User)?.parts
                        ?.joinToString(separator = "\n") { part ->
                            when (part) {
                                is MessagePart.Text -> part.text
                                is MessagePart.Tool.Result -> part.output
                                else -> ""
                            }
                        } ?: ""
                    val event = "onLLMCallStarting($lastText)"

                    if (event == "onLLMCallStarting(encoded_result(\"Hidden Value\"))" && numberOfRun == 1) {
                        throw IllegalStateException("Interrupted")
                    }

                    events += event
                }
            }
        }

        assertThrows<IllegalStateException> {
            agent.run("Tell me the secret!", "session-01")
        }

        val lastCheckpoint = checkpointStorage.getCheckpoints("session-01").last()

        val lastGraphProps = lastCheckpoint.graphProperties!!
        assertEquals("nodeExecuteTool", lastGraphProps.nodePath.substringAfterLast("/"))
        assertNotNull(lastGraphProps.lastOutput, lastGraphProps.nodePath.substringAfterLast("/"))

        val lastOutputValue = KotlinxSerializer().decodeFromJSONElement<ReceivedToolResults>(
            lastGraphProps.lastOutput,
            typeToken<ReceivedToolResults>()
        )
        val persistedToolResult = lastOutputValue.toolResults.single()

        assertEquals("guesser", persistedToolResult.tool)
        assertEquals("encoded_result(\"Hidden Value\")", persistedToolResult.output)
        // resultObject is @Transient — it is NOT persisted via the Persistence feature
        assertNull(persistedToolResult.resultObject)

        val expectedEventsFirstRun = listOf(
            "onLLMCallStarting(Tell me the secret!)",
            "started: nodeExecuteTool(tool=guesser, content={\"question\":\"What is the secret value?\"})",
            "onToolCallStarting(guesser, args={\"question\":\"What is the secret value?\"})",
            "onToolCallCompleted(guesser, toolResult={\"x\":100500, \"y\":\"Hidden Value\"})",
            "finished: nodeExecuteTool(tool=guesser, output=encoded_result(\"Hidden Value\"))"
        )

        assertEquals(expectedEventsFirstRun.size, events.size)
        assertContentEquals(expectedEventsFirstRun, events)

        events.clear()

        val result = agent.run("Tell me the secret!", "session-01")
        assertEquals("Done! Value is Hidden Value", result)

        val expectedEventsSecondRun = listOf(
            "onLLMCallStarting(encoded_result(\"Hidden Value\"))"
        )

        assertEquals(expectedEventsSecondRun.size, events.size)
        assertContentEquals(expectedEventsSecondRun, events)
    }
}
