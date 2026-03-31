package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

/**
 * Black-box tests for signature encoding, JSON parsing, Map-to-JSON conversion,
 * and error handling in [GoogleGenaiLLMClient].
 */
class GoogleGenaiUtilsTest {

    private val delegate: com.google.genai.Client
    private val asyncModels: com.google.genai.AsyncModels
    private val subject: CustomizedGoogleGenaiLLMClient

    private val flashModel get() = TestModels.flash
    private val thinkingModel get() = TestModels.thinking

    init {
        val (d, am) = mockGoogleGenaiClient()
        delegate = d
        asyncModels = am
        subject = CustomizedGoogleGenaiLLMClient(delegate, models = TestModels.all)
    }

    private fun mockGenerateContent(response: GenerateContentResponse) =
        asyncModels.stubGenerateContent(response)

    // region Signature encoding — binary bytes must survive the round-trip without corruption

    @Test
    fun `thought signature round-trips through request and response without byte corruption`() = runTest {
        val rawBytes = byteArrayOf(0x00, 0x01, 0x7F, 0xFF.toByte(), 0xFE.toByte(), 0xD8.toByte())
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(content = "thinking", encrypted = base64Signature, metaInfo = ResponseMetaInfo.Empty),
                Message.User("follow-up", RequestMetaInfo.Empty),
            ),
            id = "sig-test"
        )
        val responsePart = Part.builder().text("answer").thought(true).thoughtSignature(rawBytes).build()
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(
                            Content.builder().role("model").parts(listOf(responsePart, Part.fromText("final"))).build()
                        )
                        .finishReason("STOP")
                        .build()
                )
            ).build()
        val captured = mockGenerateContent(response)

        val results = subject.execute(prompt, thinkingModel)

        // Verify request: thought part carries exact raw bytes
        val thoughtPart = captured.contents[1].parts().get()[0]
        thoughtPart.thoughtSignature().get() shouldBe rawBytes

        // Verify response: Base64-encoded string is restored exactly
        val reasoning = results[0].shouldBeInstanceOf<Message.Reasoning>()
        reasoning.encrypted shouldBe base64Signature
    }

    // endregion

    // region Signature propagation — reasoning signature must flow to the first tool call

    @Test
    fun `thought signature is propagated from reasoning to first tool call (blank reasoning content)`() = runTest {
        val rawBytes = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val base64Signature = Base64.getEncoder().encodeToString(rawBytes)

        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Reasoning(content = "", encrypted = base64Signature, metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "search", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "sig-propagate-blank"
        )
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, thinkingModel)

        val toolCallPart = captured.contents[1].parts().get()[0]
        toolCallPart.thoughtSignature().get() shouldBe rawBytes
    }

    @Test
    fun `thought signature is propagated from reasoning with content to first tool call`() = runTest {
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
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, thinkingModel)

        // contents[0]=user, contents[1]=reasoning block, contents[2]=tool call batch
        val toolCallPart = captured.contents[2].parts().get()[0]
        toolCallPart.thoughtSignature().get() shouldBe rawBytes
    }

    // endregion

    // region Parallel tool calls — only the first call in a batch may carry a signature

    @Test
    fun `only first tool call in parallel batch has signature, subsequent calls do not`() = runTest {
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
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, thinkingModel)

        val batchParts = captured.contents[1].parts().get()
        batchParts shouldHaveSize 3
        batchParts[0].thoughtSignature().get() shouldBe rawBytes
        batchParts[1].thoughtSignature().isPresent shouldBe false
        batchParts[2].thoughtSignature().isPresent shouldBe false
    }

    @Test
    fun `tool calls without preceding reasoning carry no signature for non-thinking model`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "search", content = "{}", metaInfo = ResponseMetaInfo.Empty),
                Message.Tool.Call(id = "2", tool = "lookup", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "no-sig-non-thinking"
        )
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, flashModel)

        val batchParts = captured.contents[1].parts().get()
        batchParts shouldHaveSize 2
        batchParts[0].thoughtSignature().isPresent shouldBe false
        batchParts[1].thoughtSignature().isPresent shouldBe false
    }

    // endregion

    // region JSON parsing (tested via tool call args round-trip)

    @Test
    fun `tool call with JSON args is correctly parsed`() = runTest {
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
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, flashModel)

        val fc = captured.contents[1].parts().get()[0].functionCall().get()
        fc.shouldNotBeNull()
        fc.name().get() shouldBe "search"
        val args = fc.args().get()
        args["query"] shouldBe "hello"
        args["limit"] shouldBe 10L
    }

    @Test
    fun `tool call with empty JSON args is handled`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(id = "1", tool = "ping", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            ),
            id = "empty-args"
        )
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(prompt, flashModel)

        val fc = captured.contents[1].parts().get()[0].functionCall().get()
        fc.shouldNotBeNull()
        fc.args().orElse(emptyMap()) shouldBe emptyMap()
    }

    @Test
    fun `tool call with malformed JSON args throws LLMClientException`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(
                    id = "1",
                    tool = "search",
                    content = "not valid json{{{",
                    metaInfo = ResponseMetaInfo.Empty
                ),
            ),
            id = "malformed-json"
        )
        mockGenerateContent(textResponse("ok"))

        val error = assertThrows<LLMClientException> {
            subject.execute(prompt, flashModel)
        }
        error.message shouldContain "Failed to parse tool call JSON args"
    }

    // endregion

    // region Map-to-JSON conversion (tested via function call response)

    @Test
    fun `function call response args are converted to JSON string`() = runTest {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder().content(
                        Content.builder().role("model").parts(
                            Part.fromFunctionCall("calc", mapOf("x" to 42, "label" to "test", "flag" to true))
                        ).build()
                    ).build()
                )
            ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("q", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "calc"
        toolCall.content shouldEqualJson """{"x":42,"label":"test","flag":true}"""
    }

    @Test
    fun `function call with null args produces empty JSON object`() = runTest {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder().content(
                        Content.builder().role("model").parts(
                            listOf(Part.builder().functionCall(FunctionCall.builder().name("my_tool").build()).build())
                        ).build()
                    ).build()
                )
            ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("q", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "my_tool"
        toolCall.content shouldBe "{}"
    }

    // endregion

    // region Error handling

    @Test
    fun `execute throws LLMClientException on empty candidates`() = runTest {
        mockGenerateContent(GenerateContentResponse.builder().candidates(emptyList()).build())

        val error = assertThrows<LLMClientException> {
            subject.execute(
                Prompt(messages = listOf(Message.User("q", RequestMetaInfo.Empty)), id = "t"),
                flashModel
            )
        }
        error.message shouldContain "Empty candidates"
    }

    // endregion
}
