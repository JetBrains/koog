package ai.koog.prompt.executor.clients.retry

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@Isolated("Captures System.err used by SLF4J Simple")
@Execution(ExecutionMode.SAME_THREAD)
class RetryLoggingTest {
    @Test
    fun testOrdinaryRetryOmitsExceptionContents() = runTest {
        val delegate = FailingOnceClient()
        val client = RetryingLLMClient(delegate, retryConfig)

        val output = captureWarnings {
            assertEquals(emptyList(), client.models())
        }

        assertEquals(2, delegate.calls)
        assertSafeWarning(output, "models failed")
    }

    @Test
    fun testStreamingRetryOmitsExceptionContents() = runTest {
        val delegate = FailingOnceClient()
        val client = RetryingLLMClient(delegate, retryConfig)
        val model = LLModel(LLMProvider.OpenAI, "test-model", emptyList(), 4096)

        val output = captureWarnings {
            assertEquals(emptyList(), client.executeStreaming(prompt("test") {}, model).toList())
        }

        assertEquals(2, delegate.calls)
        assertSafeWarning(output, "Stream connection failed before first token")
    }

    private fun assertSafeWarning(output: String, operation: String) {
        assertTrue(output.contains("WARN"), output)
        assertTrue(output.contains(operation), output)
        assertTrue(output.contains("attempt 1/2"), output)
        assertTrue(output.contains("Retrying in 10ms"), output)
        assertFalse(output.contains("private-response-body"), output)
        assertFalse(output.contains("synthetic-api-key"), output)
        assertFalse(output.contains("private-cause"), output)
    }

    // The test JVM uses the SLF4J Simple backend supplied by test-utils.
    private suspend fun captureWarnings(block: suspend () -> Unit): String {
        val previous = System.err
        val output = ByteArrayOutputStream()
        val capture = PrintStream(output, true, Charsets.UTF_8)
        try {
            System.setErr(capture)
            block()
        } finally {
            System.setErr(previous)
            capture.close()
        }
        return output.toString(Charsets.UTF_8)
    }

    private class FailingOnceClient : LLMClient() {
        var calls = 0

        private fun failOnce() {
            if (++calls == 1) {
                throw IllegalStateException(
                    "429 private-response-body Authorization: Bearer synthetic-api-key",
                    IllegalArgumentException("private-cause")
                )
            }
        }

        override suspend fun models(): List<LLModel> {
            failOnce()
            return emptyList()
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
            flow { failOnce() }

        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
            error("Unused")

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("Unused")

        override fun llmProvider(): LLMProvider = LLMProvider.OpenAI

        override fun close() {}
    }

    private companion object {
        val retryConfig = RetryConfig(
            maxAttempts = 2,
            initialDelay = 10.milliseconds,
            jitterFactor = 0.01
        )
    }
}
