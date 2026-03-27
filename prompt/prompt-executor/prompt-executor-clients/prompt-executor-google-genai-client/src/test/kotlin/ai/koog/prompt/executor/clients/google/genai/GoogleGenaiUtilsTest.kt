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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class GoogleGenaiUtilsTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    // region Signature encoding — binary bytes must survive the round-trip without corruption

    /**
     * Thought signatures are opaque binary blobs from the Google API.
     * They cannot be decoded/re-encoded as UTF-8 strings — that corrupts the bytes.
     * The fix is Base64 encoding, verified here by using bytes outside the valid UTF-8 range.
     */
    @Test
    fun `thought signature round-trips through request and response without byte corruption`() {
        // Bytes that are NOT valid UTF-8 — decodeToString() would corrupt these
        val rawBytes = byteArrayOf(0x00, 0x01, 0x7F, 0xFF.toByte(), 0xFE.toByte(), 0xD8.toByte())
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(
                    content = "thinking",
                    encrypted = base64Signature,
                    metaInfo = ResponseMetaInfo.Empty
                ),
            ),
            id = "sig-test"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)
        val thoughtPart = contents[1].parts().orElse(emptyList())[0]

        // The exact raw bytes must be preserved — Base64 guarantees lossless round-trip
        val sigBytes = thoughtPart.thoughtSignature().orElseThrow()
        sigBytes shouldBe rawBytes

        // Round-trip through response processing
        val responsePart = Part.builder().text("answer").thought(true).thoughtSignature(sigBytes).build()
        val candidate = Candidate.builder()
            .content(Content.builder().role("model").parts(listOf(responsePart)).build())
            .build()
        val responses = subject.processCandidate(candidate, ResponseMetaInfo.Empty)

        val reasoning = responses[0].shouldBeInstanceOf<Message.Reasoning>()
        // The Base64-encoded string is restored exactly — bytes were not corrupted
        reasoning.encrypted shouldBe base64Signature
    }

    // endregion

    // region Signature propagation — reasoning signature must flow to the first tool call

    @Test
    fun `thought signature is propagated from reasoning to first tool call (blank reasoning content)`() {
        val rawBytes = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        // Reasoning has no visible content — the content block is skipped, but signature must still propagate
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(content = "", encrypted = base64Signature, metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "search", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "sig-propagate-blank"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)

        // contents[0]=user, contents[1]=tool call batch (reasoning had blank content → no separate block)
        val toolCallPart = contents[1].parts().orElse(emptyList())[0]
        toolCallPart.thoughtSignature().orElse(null) shouldBe rawBytes
    }

    @Test
    fun `thought signature is propagated from reasoning with content to first tool call`() {
        // This was the bug reported by @andruhon: when reasoning had non-blank content,
        // the signature was not forwarded to the subsequent tool call parts.
        val rawBytes = byteArrayOf(0x10, 0x20, 0x30)
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(
                    content = "I should search for this",
                    encrypted = base64Signature,
                    metaInfo = ResponseMetaInfo.Empty
                ),
                Message.Tool.Call(id = "1", tool = "calc", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "sig-propagate-with-content"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)

        // contents[0]=user, contents[1]=reasoning block (has visible text), contents[2]=tool call batch
        val toolCallPart = contents[2].parts().orElse(emptyList())[0]
        toolCallPart.thoughtSignature().orElse(null) shouldBe rawBytes
    }

    // endregion

    // region Parallel tool calls — only the first call in a batch may carry a signature

    @Test
    fun `only first tool call in parallel batch has signature, subsequent calls do not`() {
        // Per Google API spec: in a parallel tool call batch, only the first Part may carry
        // a thoughtSignature. Subsequent parts must have no signature.
        // See https://docs.cloud.google.com/vertex-ai/generative-ai/docs/thought-signatures
        val rawBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(content = "", encrypted = base64Signature, metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "tool_a", content = "{}", metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "2", tool = "tool_b", content = "{}", metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "3", tool = "tool_c", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "parallel-calls"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)

        // contents[0]=user, contents[1]=model batch with all 3 tool call parts
        val batchParts = contents[1].parts().orElse(emptyList())
        batchParts shouldHaveSize 3

        // First call carries the signature
        batchParts[0].thoughtSignature().orElse(null) shouldBe rawBytes
        // Second and third calls must NOT carry a signature
        batchParts[1].thoughtSignature().orElse(null).shouldBeNull()
        batchParts[2].thoughtSignature().orElse(null).shouldBeNull()
    }

    @Test
    fun `tool calls without preceding reasoning carry no signature for non-thinking model`() {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "search", content = "{}", metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "2", tool = "lookup", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "no-sig-non-thinking"
        )
        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)

        val batchParts = contents[1].parts().orElse(emptyList())
        batchParts shouldHaveSize 2
        batchParts[0].thoughtSignature().orElse(null).shouldBeNull()
        batchParts[1].thoughtSignature().orElse(null).shouldBeNull()
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
