package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val MAX_AGENT_ITERATIONS = 20
private const val SUCCESS = "success"
private val TEST_CONDITION: AIAgentGraphContextBase.(String) -> ConditionResult = { (it == SUCCESS).asConditionResult }
private fun getBasicResult(
    output: String? = "test output",
    success: Boolean = true,
    retryCount: Int = 2,
) =
    RetrySubgraphResult(
        output = output,
        success = success,
        retryCount = retryCount,
    )

class SubgraphWithRetryTest {

    @Test
    fun testRetrySubgraphResult() = runTest {
        val result = getBasicResult()

        assertEquals("test output", result.output)
        assertTrue(result.success)
        assertEquals(2, result.retryCount)
    }

    @Test
    fun testRetrySubgraphResultNullOutput() = runTest {
        val result = getBasicResult(null)

        assertEquals(null, result.output)
        assertTrue(result.success)
        assertEquals(2, result.retryCount)
    }

    @Test
    fun testRetrySubgraphResultZeroRetries() = runTest {
        assertFailsWith<IllegalArgumentException> {
            getBasicResult(retryCount = 0)
        }
    }

    @Test
    fun testRetrySubgraphResultCopy() = runTest {
        val result = getBasicResult()

        val copiedResult = result.copy()

        assertEquals(result.output, copiedResult.output)
        assertEquals(result.success, copiedResult.success)
        assertEquals(result.retryCount, copiedResult.retryCount)
    }

    @Test
    fun testRetrySubgraphResultCopyWithChanges() = runTest {
        val result = getBasicResult()

        val copiedResult = result.copy(success = false)

        assertEquals(result.output, copiedResult.output)
        assertNotEquals(result.success, copiedResult.success)
        assertFalse(copiedResult.success)
        assertEquals(result.retryCount, copiedResult.retryCount)
    }

    @Test
    fun testRetrySubgraphResultCopyZeroRetries() = runTest {
        val result = getBasicResult()

        assertFailsWith<IllegalArgumentException> {
            result.copy(retryCount = 0)
        }
    }

    @Test
    fun testSubgraphWithRetrySuccessFirstTry() = runTest {
        val results = mutableListOf<Any?>()

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetry(
                condition = TEST_CONDITION,
                maxRetries = 3,
                name = "test-retry",
            ) {
                val processNode by node<String, String> { input ->
                    SUCCESS
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        agent.run("test input")

        assertEquals(1, results.size)
        val result = results.first() as RetrySubgraphResult<*>
        assertEquals(SUCCESS, result.output)
        assertTrue(result.success)
        assertEquals(1, result.retryCount)
    }

    @Test
    fun testSubgraphWithRetrySuccessAfterRetries() = runTest {
        val results = mutableListOf<Any?>()
        val attemptCount = mutableListOf<Int>()
        val maxAttempts = 3

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetry(
                condition = TEST_CONDITION,
                maxRetries = maxAttempts,
                name = "test-retry",
            ) {
                val processNode by node<String, String> { input ->
                    val currentAttempt = attemptCount.size + 1
                    attemptCount.add(currentAttempt)

                    if (currentAttempt < maxAttempts) {
                        "failure"
                    } else {
                        SUCCESS
                    }
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        agent.run("test input")

        assertEquals(1, results.size)
        val result = results.first() as RetrySubgraphResult<*>
        assertEquals(SUCCESS, result.output)
        assertTrue(result.success)
        assertEquals(maxAttempts, result.retryCount)
        assertEquals(maxAttempts, attemptCount.size)
    }

    @Test
    fun testSubgraphWithRetryMaxRetriesReached() = runTest {
        val results = mutableListOf<Any?>()
        val attemptCount = mutableListOf<Int>()
        val maxAttempts = 3

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetry(
                condition = TEST_CONDITION,
                maxRetries = maxAttempts,
                name = "test-retry",
            ) {
                val processNode by node<String, String> { input ->
                    val currentAttempt = attemptCount.size + 1
                    attemptCount.add(currentAttempt)
                    "failure"
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        agent.run("test input")

        assertEquals(1, results.size)
        val result = results.first() as RetrySubgraphResult<*>
        assertEquals(maxAttempts, result.retryCount)
        assertEquals(maxAttempts, attemptCount.size)
        assertFalse(result.success)
    }

    @Test
    fun testSubgraphWithRetryZeroMaxRetries() = runTest {
        assertFailsWith<IllegalArgumentException> {
            strategy<String, String>("test-strategy") {
                subgraphWithRetry(
                    condition = TEST_CONDITION,
                    maxRetries = 0,
                    name = "test-retry",
                ) {
                    val processNode by node<String, String> { input -> input }
                    nodeStart then processNode then nodeFinish
                }
            }
        }
    }

    @Test
    fun testSubgraphWithRetrySimpleSuccess() = runTest {
        val results = mutableListOf<Any?>()

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetrySimple(
                condition = TEST_CONDITION,
                maxRetries = 3,
                name = "test-retry-simple",
            ) {
                val processNode by node<String, String> { input ->
                    SUCCESS
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        agent.run("test input")

        assertEquals(1, results.size)
        assertEquals(SUCCESS, results.first())
    }

    @Test
    fun testSubgraphWithRetrySimpleFailureStrict() = runTest {
        val results = mutableListOf<Any?>()
        val attemptCount = mutableListOf<Int>()
        val maxAttempts = 3

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetrySimple(
                condition = TEST_CONDITION,
                maxRetries = maxAttempts,
                strict = true,
                name = "test-retry-simple",
            ) {
                val processNode by node<String, String> { input ->
                    val currentAttempt = attemptCount.size + 1
                    attemptCount.add(currentAttempt)
                    "failure"
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        assertFailsWith<IllegalStateException> {
            agent.run("test input")
        }

        assertEquals(maxAttempts, attemptCount.size)
    }

    @Test
    fun testSubgraphWithRetrySimpleFailureNonStrict() = runTest {
        val results = mutableListOf<Any?>()
        val attemptCount = mutableListOf<Int>()
        val maxAttempts = 3

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetrySimple(
                condition = TEST_CONDITION,
                maxRetries = maxAttempts,
                strict = false,
                name = "test-retry-simple",
            ) {
                val processNode by node<String, String> { input ->
                    val currentAttempt = attemptCount.size + 1
                    attemptCount.add(currentAttempt)
                    "failure"
                }

                nodeStart then processNode then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        ) {
            install(EventHandler) {
                onAgentFinished { eventContext -> results += eventContext.result }
            }
        }

        agent.run("test input")

        assertEquals(1, results.size)
        assertEquals("failure", results.first())
        assertEquals(maxAttempts, attemptCount.size)
    }

    @Test
    fun testSubgraphWithRetrySimpleZeroMaxRetries() = runTest {
        assertFailsWith<IllegalArgumentException> {
            strategy<String, String>("test-strategy") {
                subgraphWithRetrySimple(
                    condition = TEST_CONDITION,
                    maxRetries = 0,
                    strict = false,
                    name = "test-retry-simple",
                ) {
                    val processNode by node<String, String> { input -> input }
                    nodeStart then processNode then nodeFinish
                }
            }
        }
    }

    @Test
    fun testSubgraphWithRetryFeedback() = runTest {
        val numRetries = 4
        val lastMessagesInThePrompt = mutableListOf<Message?>()
        var retries = 0

        val testStrategy = strategy("test-strategy") {
            val retrySubgraph by subgraphWithRetry(
                condition = { result ->
                    if (result == SUCCESS) {
                        ConditionResult.Approve
                    } else {
                        ConditionResult.Reject("Retry ${++retries}")
                    }
                },
                conditionDescription = "Condition description",
                maxRetries = numRetries,
                name = "test-retry-simple",
            ) {
                val checkLastMessage by node<String, String> { input ->
                    lastMessagesInThePrompt.add(llm.readSession { prompt.messages.lastOrNull() })
                    "failure"
                }
                nodeStart then checkLastMessage then nodeFinish
            }

            nodeStart then retrySubgraph then nodeFinish
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = MAX_AGENT_ITERATIONS,
        )

        val agent = AIAgent(
            promptExecutor = getMockExecutor {},
            strategy = testStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            },
        )

        agent.run("test input")

        val actualConditionDescriptionMessage = lastMessagesInThePrompt[0]
        assertIs<Message.User>(actualConditionDescriptionMessage)
        assertEquals(
            "Condition description",
            actualConditionDescriptionMessage.content,
            "Condition description message should be added to the prompt"
        )
        for (i in 1..numRetries - 1) {
            val actualFeedbackMessage = lastMessagesInThePrompt[i]
            assertIs<Message.User>(actualFeedbackMessage)
            assertEquals(
                "Retry $i",
                actualFeedbackMessage.content,
                "Feedback message number $i should be added to the prompt"
            )
        }
    }
}
