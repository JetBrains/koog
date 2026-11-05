package ai.koog.prompt.executor.clients.litert

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.github.johnrocky.litertlm.LiteRtLmException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Runs without a model. A test binary does not have Google's runtime, which the app adds, so a request fails
 * the way it fails in an app that has not added it.
 */
class LiteRTLLMClientTest {
    private val prompt = Prompt(listOf(Message.User("hi", RequestMetaInfo.Empty)), "test")
    private val model = LiteRTLLModels.FunctionGemma

    @Test
    fun testExecuteWithoutTheRuntimeFailsWithAClientException() = runTest {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        val failure = assertFailsWith<LLMClientException> { client.execute(prompt, model, emptyList()) }
        assertIs<LiteRtLmException>(failure.cause)
        assertContains(failure.message.orEmpty(), "CLiteRTLM")
        client.close()
    }

    @Test
    fun testExecuteStreamingWithoutTheRuntimeFailsWithAClientException() = runTest {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        val failure = assertFailsWith<LLMClientException> {
            client.executeStreaming(prompt, model, emptyList()).toList()
        }
        assertIs<LiteRtLmException>(failure.cause)
        client.close()
    }

    @Test
    fun testExecuteWithAnEmptyPromptIsAnArgumentError() = runTest {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        assertFailsWith<IllegalArgumentException> { client.execute(Prompt(emptyList(), "test"), model, emptyList()) }
        client.close()
    }

    @Test
    fun testModerationIsNotSupported() = runTest {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        assertFailsWith<UnsupportedOperationException> { client.moderate(prompt, model) }
        client.close()
    }

    @Test
    fun testTheProviderIsLiteRT() {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        assertEquals(LiteRTLLMProvider, client.llmProvider())
        client.close()
    }

    @Test
    fun testCloseTwiceIsHarmless() {
        val client = LiteRTLLMClient(LiteRTClientConfig())
        client.close()
        client.close()
    }
}
