package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.MessagePart
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FoundationModelsLLMClientTest {

    private val p = prompt("t") {
        system("be brief")
        user("hi")
    }

    @Test
    fun testExecuteReturnsAssistantTextAndForwardsInput() = runTest {
        val fake = FakeFoundationModelsSession(response = "hello there")
        val client = FoundationModelsLLMClient(fake)

        val result = client.execute(p, AppleLLModels.SystemDefault, emptyList())

        assertEquals("hello there", (result.parts.single() as MessagePart.Text).text)
        assertEquals("be brief", fake.lastInstructions)
        assertEquals("hi", fake.lastPrompt)
    }

    @Test
    fun testExecuteEmptyResponseStillYieldsOneTextPart() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession(response = ""))
        val result = client.execute(p, AppleLLModels.SystemDefault, emptyList())
        assertEquals(1, result.parts.size)
        assertTrue(result.parts.single() is MessagePart.Text)
    }

    @Test
    fun testExecuteThrowsTypedUnavailable() = runTest {
        val client = FoundationModelsLLMClient(
            FakeFoundationModelsSession(unavailableToken = "appleIntelligenceNotEnabled"),
        )
        val exception = assertFailsWith<FoundationModelsException.Unavailable> {
            client.execute(p, AppleLLModels.SystemDefault, emptyList())
        }
        assertEquals(
            FoundationModelsAvailability.Unavailable.AppleIntelligenceNotEnabled,
            exception.availability,
        )
    }

    @Test
    fun testAvailabilityReportsAvailableWhenSessionHasNoToken() {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession())
        assertEquals(FoundationModelsAvailability.Available, client.availability())
    }

    @Test
    fun testAvailabilityReportsTypedUnavailableCase() {
        val client = FoundationModelsLLMClient(
            FakeFoundationModelsSession(unavailableToken = "modelNotReady"),
        )
        assertEquals(FoundationModelsAvailability.Unavailable.ModelNotReady, client.availability())
    }

    @Test
    fun testModelsReturnsTheAppleCatalog() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession())
        assertEquals(AppleLLModels.models, client.models())
    }

    @Test
    fun testExecutePropagatesGenerationError() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession(error = "boom"))
        assertFailsWith<FoundationModelsException.Generation> {
            client.execute(p, AppleLLModels.SystemDefault, emptyList())
        }
    }

    @Test
    fun testModerateIsUnsupported() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession())
        assertFailsWith<UnsupportedOperationException> {
            client.moderate(p, AppleLLModels.SystemDefault)
        }
    }

    @Test
    fun testProvider() {
        assertEquals(AppleLLMProvider, FoundationModelsLLMClient(FakeFoundationModelsSession()).llmProvider())
    }
}
