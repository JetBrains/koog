package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class HistoryCompressionStrategiesTest {

    private fun createMockExecutor() = getMockExecutor {
        mockLLMAnswer("TLDR").onRequestContains("Create a comprehensive summary")
    }

    private fun createBaseAgentConfig(): AIAgentConfig {
        return AIAgentConfig(
            prompt = prompt("test-agent") { user("test prompt") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )
    }

    private fun createToolRegistry() = ToolRegistry.Companion {
        tool(DummyTool())
    }

    private fun createHistoryCompressionStrategy(strategy: HistoryCompressionStrategy, messages: List<Message>) =
        strategy<String, List<Message>>("strategy") {
            return strategy<String, List<Message>>("strategy") {
                val setMessageHistory by node<String, String> { input ->
                    llm.writeSession {
                        rewritePrompt {
                            prompt.withMessages { messages }
                        }
                    }
                    input
                }

                val compressNode by nodeLLMCompressHistory<String>(
                    strategy = strategy
                )
                nodeStart then setMessageHistory then compressNode
                edge(compressNode forwardTo nodeFinish transformed { llm.prompt.messages })
            }
        }

    companion object {

        private fun testClock(delay: Duration): Clock = object : Clock {
            override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z").plus(delay)
        }

        val simpleHistory = listOf(
            Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
        )

        val multipleUserMessagesHistory = listOf(
            Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.User("User message 1", metaInfo = RequestMetaInfo.create(testClock(2.minutes))),
            Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo.create(testClock(3.minutes))),
        )

        val leadingUserMessagesHistory = listOf(
            Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo.create(testClock(2.minutes))),
        )

        val trailingToolCallHistory = listOf(
            Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.Assistant("Assistant message", metaInfo = ResponseMetaInfo.create(testClock(2.minutes))),
            Message.Tool.Call("ID", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(3.minutes))),
        )

        val multipleSystemMessagesHistory = listOf(
            Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.Assistant("Assistant message 0", metaInfo = ResponseMetaInfo.create(testClock(2.minutes))),
            Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(3.minutes))),
            Message.User("User message 1", metaInfo = RequestMetaInfo.create(testClock(4.minutes))),
            Message.Assistant("Assistant message 1", metaInfo = ResponseMetaInfo.create(testClock(5.minutes))),
            Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(6.minutes))),
        )

        val longMessagesHistory = listOf(
            Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
            Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
            Message.Assistant("Assistant message 0", metaInfo = ResponseMetaInfo.create(testClock(2.minutes))),
            Message.Tool.Call("id1", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(3.minutes))),
            Message.Tool.Result("id1", "DummyTool", "Result", metaInfo = RequestMetaInfo.create(testClock(4.minutes))),
            Message.Tool.Call("id2", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(5.minutes))),
            Message.Tool.Result("id2", "DummyTool", "Result", metaInfo = RequestMetaInfo.create(testClock(6.minutes))),
            Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(7.minutes))),
            Message.User("User message 1", metaInfo = RequestMetaInfo.create(testClock(8.minutes))),
            Message.Assistant("Assistant message 1", metaInfo = ResponseMetaInfo.create(testClock(9.minutes))),
            Message.Tool.Call("id3", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(10.minutes))),
            Message.Tool.Result("id3", "DummyTool", "Result", metaInfo = RequestMetaInfo.create(testClock(11.minutes))),
            Message.Tool.Call("id4", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(12.minutes))),
            Message.Tool.Result("id4", "DummyTool", "Result", metaInfo = RequestMetaInfo.create(testClock(13.minutes))),
            Message.Assistant("Assistant message 2", metaInfo = ResponseMetaInfo.create(testClock(14.minutes))),
            Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(15.minutes))),
            Message.Assistant("Assistant message 3", metaInfo = ResponseMetaInfo.create(testClock(16.minutes))),
            Message.Tool.Call("id5", "DummyTool", "Args", metaInfo = ResponseMetaInfo.create(testClock(17.minutes))),
            Message.Tool.Result("id5", "DummyTool", "Result", metaInfo = RequestMetaInfo.create(testClock(18.minutes))),
        )

        @JvmStatic
        fun wholeHistoryCompressionMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(
                simpleHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                trailingToolCallHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                multipleUserMessagesHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                leadingUserMessagesHistory,
                listOf(
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                multipleSystemMessagesHistory,
                listOf(
                    Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(3.minutes))),
                    Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(6.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(7.minutes)))
                )
            ),
        )

        @JvmStatic
        fun wholeHistoryMultipleSystemMessagesCompressionMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(
                simpleHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                trailingToolCallHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                multipleUserMessagesHistory,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                leadingUserMessagesHistory,
                listOf(
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                multipleSystemMessagesHistory,
                listOf(
                    Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes))),
                    Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(3.minutes))),
                    Message.User("User message 1", metaInfo = RequestMetaInfo.create(testClock(4.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(5.minutes))),
                    Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(6.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(7.minutes)))
                )
            )
        )

        @JvmStatic
        fun fromLastNMessagesCompressionMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(
                simpleHistory,
                2,
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            ),
            Arguments.of(
                multipleSystemMessagesHistory,
                3,
                listOf(
                    Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(2.minutes))),
                    Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(3.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(4.minutes)))
                )
            )
        )

        @JvmStatic
        fun fromTimestampCompressionMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(
                simpleHistory,
                Instant.parse("2023-01-01T00:00:00Z"),
                listOf(
                    Message.System("System message", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(2.minutes)))
                )
            )
        )

        @JvmStatic
        fun chunkedCompressionMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(
                multipleSystemMessagesHistory,
                2,
                listOf(
                    Message.System("System message 0", metaInfo = RequestMetaInfo.create(testClock(0.minutes))),
                    Message.User("User message 0", metaInfo = RequestMetaInfo.create(testClock(1.minutes))),
                    Message.System("System message 1", metaInfo = RequestMetaInfo.create(testClock(3.minutes))),
                    Message.System("System message 2", metaInfo = RequestMetaInfo.create(testClock(6.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(7.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(8.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(9.minutes))),
                    Message.Assistant("TLDR", metaInfo = ResponseMetaInfo.create(testClock(10.minutes)))
                )
            )
        )
    }

    private fun compareHistory(resultMessages: List<Message>, compressedMessages: List<Message>) {
        assert(resultMessages.size == compressedMessages.size)
        resultMessages.forEachIndexed { index, message ->
            assert(message.content == compressedMessages[index].content)
            assert(message.role == compressedMessages[index].role)
        }
    }

    @ParameterizedTest
    @MethodSource("wholeHistoryCompressionMessages")
    fun testWholeHistoryCompression(originalMessages: List<Message>, compressedMessages: List<Message>) = runTest {
        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = createHistoryCompressionStrategy(
                HistoryCompressionStrategy.WholeHistory,
                originalMessages,
            ),
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val resultMessages = agent.run("User input")
        compareHistory(resultMessages, compressedMessages)
    }

    @ParameterizedTest
    @MethodSource("wholeHistoryMultipleSystemMessagesCompressionMessages")
    fun testWholeHistoryMultipleSystemMessagesCompression(
        originalMessages: List<Message>,
        compressedMessages: List<Message>
    ) = runTest {
        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = createHistoryCompressionStrategy(
                HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages,
                originalMessages,
            ),
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val resultMessages = agent.run("User input")
        compareHistory(resultMessages, compressedMessages)
    }

    @ParameterizedTest
    @MethodSource("fromLastNMessagesCompressionMessages")
    fun testFromLastNMessagesCompression(originalMessages: List<Message>, n: Int, compressedMessages: List<Message>) =
        runTest {
            val agent = AIAgent.Companion(
                promptExecutor = createMockExecutor(),
                strategy = createHistoryCompressionStrategy(
                    HistoryCompressionStrategy.FromLastNMessages(n),
                    originalMessages,
                ),
                agentConfig = createBaseAgentConfig(),
                toolRegistry = createToolRegistry()
            )

            val resultMessages = agent.run("User input")
            compareHistory(resultMessages, compressedMessages)
        }

    @ParameterizedTest
    @MethodSource("fromTimestampCompressionMessages")
    fun testFromTimestampCompression(
        originalMessages: List<Message>,
        timestamp: Instant,
        compressedMessages: List<Message>
    ) = runTest {
        val agent = AIAgent.Companion(
            promptExecutor = createMockExecutor(),
            strategy = createHistoryCompressionStrategy(
                HistoryCompressionStrategy.FromTimestamp(timestamp),
                originalMessages,
            ),
            agentConfig = createBaseAgentConfig(),
            toolRegistry = createToolRegistry()
        )

        val resultMessages = agent.run("User input")
        compareHistory(resultMessages, compressedMessages)
    }

    @ParameterizedTest
    @MethodSource("chunkedCompressionMessages")
    fun testChunkedCompression(originalMessages: List<Message>, chunkSize: Int, compressedMessages: List<Message>) =
        runTest {
            val agent = AIAgent.Companion(
                promptExecutor = createMockExecutor(),
                strategy = createHistoryCompressionStrategy(
                    HistoryCompressionStrategy.Chunked(chunkSize),
                    originalMessages,
                ),
                agentConfig = createBaseAgentConfig(),
                toolRegistry = createToolRegistry()
            )

            val resultMessages = agent.run("User input")
            compareHistory(resultMessages, compressedMessages)
        }
}
