package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleGenaiUtilsTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    // region Signature encoding (tested via buildSdkContents → processCandidate round-trip)

    @Test
    fun `thought signature round-trips through request and response`() {
        // Build request with a signature
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(
                    content = "thinking",
                    encrypted = "test-signature_value",
                    metaInfo = ResponseMetaInfo.Empty
                ),
            ),
            id = "sig-test"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)
        val thoughtPart = contents[1].parts().orElse(emptyList())[0]

        // Verify signature bytes were set
        val sigBytes = thoughtPart.thoughtSignature().orElseThrow()

        // Now process a response with the same signature bytes
        val responsePart = Part.builder().text("answer").thought(true).thoughtSignature(sigBytes).build()
        val candidate = Candidate.builder()
            .content(Content.builder().role("model").parts(listOf(responsePart)).build())
            .build()
        val responses = subject.processCandidate(candidate, ResponseMetaInfo.Empty)

        val reasoning = responses[0].shouldBeInstanceOf<Message.Reasoning>()
        reasoning.encrypted shouldBe "test-signature_value"
    }

    // endregion

    // region JSON parsing (tested via buildSdkContents with Tool.Call args)

    @Test
    fun `tool call with JSON args is correctly parsed in buildSdkContents`() {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(
                    id = "1",
                    tool = "search",
                    content = """{"query":"hello","limit":10}""",
                    metaInfo = ResponseMetaInfo.Empty
                ),
            ),
            id = "json-test"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)

        val fc = contents[1].parts().orElse(emptyList())[0].functionCall().orElseThrow()
        fc.name().orElse(null) shouldBe "search"
        val args = fc.args().orElse(emptyMap())
        args["query"] shouldBe "hello"
        args["limit"] shouldBe 10L
    }

    @Test
    fun `tool call with empty JSON args is handled`() {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "ping", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "empty-args"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)

        val fc = contents[1].parts().orElse(emptyList())[0].functionCall().orElseThrow()
        fc.args().orElse(emptyMap()) shouldBe emptyMap()
    }

    // endregion

    // region Map-to-JSON conversion (tested via processCandidate with function call args)

    @Test
    fun `function call response args are converted to JSON string`() {
        val candidate = Candidate.builder()
            .content(
                Content.builder().role("model").parts(
                    Part.fromFunctionCall("calc", mapOf("x" to 42, "label" to "test", "flag" to true))
                ).build()
            )
            .build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "calc"
        // Verify the JSON content contains the expected values
        toolCall.content.contains("42") shouldBe true
        toolCall.content.contains("test") shouldBe true
        toolCall.content.contains("true") shouldBe true
    }

    @Test
    fun `function call with null args produces empty JSON object`() {
        val part = Part.builder()
            .functionCall(FunctionCall.builder().name("my_tool").build())
            .build()
        val candidate = Candidate.builder()
            .content(Content.builder().role("model").parts(listOf(part)).build())
            .build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "my_tool"
        toolCall.content shouldBe "{}"
    }

    // endregion

    // region Error handling

    @Test
    fun `processResponse throws LLMClientException on empty candidates`() {
        val response = GenerateContentResponse.builder().candidates(emptyList()).build()
        assertThrows<LLMClientException> { subject.processResponse(response) }
    }

    // endregion
}
