package ai.koog.prompt.executor.clients.openai.models

import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

class OpenAIStreamEventsTest {

    @Test
    fun `test ResponseCreated event serialization`() =
        runWithBothJsonConfigurations("ResponseCreated serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCreated>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseCreated(
                        OpenAIResponsesAPIResponse(
                            created = 1699500000L,
                            id = "resp_123",
                            model = "gpt-4o",
                            output = emptyList(),
                            parallelToolCalls = false,
                            status = OpenAIInputStatus.IN_PROGRESS,
                            text = OpenAITextConfig()
                        ),
                        1
                    )
                )
            ).shouldNotBeNull {
                sequenceNumber shouldBe 1
                response.id shouldBe "resp_123"
            }
        }

    @Test
    fun `test ResponseInProgress event serialization`() =
        runWithBothJsonConfigurations("ResponseInProgress serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseInProgress>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseInProgress(
                        OpenAIResponsesAPIResponse(
                            created = 1699500000L,
                            id = "resp_456",
                            model = "gpt-4o",
                            output = emptyList(),
                            parallelToolCalls = false,
                            status = OpenAIInputStatus.IN_PROGRESS,
                            text = OpenAITextConfig()
                        ),
                        2
                    )
                )
            ).shouldNotBeNull {
                sequenceNumber shouldBe 2
                response.status shouldBe OpenAIInputStatus.IN_PROGRESS
            }
        }

    @Test
    fun `test ResponseCompleted event serialization`() =
        runWithBothJsonConfigurations("ResponseCompleted serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCompleted>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseCompleted(
                        OpenAIResponsesAPIResponse(
                            created = 1699500000L,
                            id = "resp_789",
                            model = "gpt-4o",
                            output = emptyList(),
                            parallelToolCalls = false,
                            status = OpenAIInputStatus.COMPLETED,
                            text = OpenAITextConfig()
                        ),
                        3
                    )
                )
            ).shouldNotBeNull {
                sequenceNumber shouldBe 3
                response.status shouldBe OpenAIInputStatus.COMPLETED
            }
        }

    @Test
    fun `test ResponseFailed event serialization`() =
        runWithBothJsonConfigurations("ResponseFailed serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFailed>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseFailed(
                        OpenAIResponsesAPIResponse(
                            created = 1699500000L,
                            id = "resp_fail",
                            model = "gpt-4o",
                            output = emptyList(),
                            parallelToolCalls = false,
                            status = OpenAIInputStatus.FAILED,
                            text = OpenAITextConfig()
                        ),
                        4
                    )
                )
            ).shouldNotBeNull {
                sequenceNumber shouldBe 4
                response.status shouldBe OpenAIInputStatus.FAILED
            }
        }

    @Test
    fun `test ResponseIncomplete event serialization`() =
        runWithBothJsonConfigurations("ResponseIncomplete serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseIncomplete>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseIncomplete(
                        OpenAIResponsesAPIResponse(
                            created = 1699500000L,
                            id = "resp_incomplete",
                            model = "gpt-4o",
                            output = emptyList(),
                            parallelToolCalls = false,
                            status = OpenAIInputStatus.INCOMPLETE,
                            text = OpenAITextConfig()
                        ),
                        5
                    )
                )
            ).shouldNotBeNull {
                sequenceNumber shouldBe 5
                response.status shouldBe OpenAIInputStatus.INCOMPLETE
            }
        }

    @Test
    fun `test ResponseOutputItemAdded event serialization`() =
        runWithBothJsonConfigurations("ResponseOutputItemAdded serialization") { json ->
            val item = Item.OutputMessage(
                content = listOf(OutputContent.Text(annotations = emptyList(), text = "Test")),
                id = "msg_test_123"
            )
            json.decodeFromString<OpenAIStreamEvent.ResponseOutputItemAdded>(
                json.encodeToString(OpenAIStreamEvent.ResponseOutputItemAdded(item, 0, 6))
            ).shouldNotBeNull {
                outputIndex shouldBe 0
                sequenceNumber shouldBe 6
                (this.item as Item.OutputMessage).content shouldHaveSize 1
                (this.item.content[0] as OutputContent.Text).text shouldBe "Test"
            }
        }

    @Test
    fun `test ResponseOutputItemDone event serialization`() =
        runWithBothJsonConfigurations("ResponseOutputItemDone serialization") { json ->
            val item = Item.OutputMessage(
                content = listOf(OutputContent.Text(annotations = emptyList(), text = "Done")),
                id = "msg_done_456"
            )
            json.decodeFromString<OpenAIStreamEvent.ResponseOutputItemDone>(
                json.encodeToString(OpenAIStreamEvent.ResponseOutputItemDone(item, 1, 7))
            ).shouldNotBeNull {
                outputIndex shouldBe 1
                sequenceNumber shouldBe 7
                (this.item as Item.OutputMessage).content shouldHaveSize 1
                (this.item.content[0] as OutputContent.Text).text shouldBe "Done"
            }
        }

    @Test
    fun `test ResponseContentPartAdded event serialization`() =
        runWithBothJsonConfigurations("ResponseContentPartAdded serialization") { json ->
            val part = OutputContent.Text(annotations = emptyList(), text = "Content part")
            json.decodeFromString<OpenAIStreamEvent.ResponseContentPartAdded>(
                json.encodeToString(OpenAIStreamEvent.ResponseContentPartAdded("item_123", 0, 0, part, 8))
            ).shouldNotBeNull {
                itemId shouldBe "item_123"
                outputIndex shouldBe 0
                contentIndex shouldBe 0
                sequenceNumber shouldBe 8
                (this.part as OutputContent.Text).text shouldBe "Content part"
                this.part.annotations shouldBe emptyList()
            }
        }

    @Test
    fun `test ResponseOutputTextDelta event serialization`() =
        runWithBothJsonConfigurations("ResponseOutputTextDelta serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseOutputTextDelta>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseOutputTextDelta(
                        itemId = "item_456",
                        outputIndex = 1,
                        contentIndex = 0,
                        delta = "Hello",
                        logprobs = listOf(
                            OpenAIStreamEvent.LogProbWithTop(
                                logprob = -0.1,
                                token = "Hello",
                                topLogprobs = listOf(OpenAIStreamEvent.LogProb(-0.1, "Hello"))
                            )
                        ),
                        sequenceNumber = 9
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "item_456"
                outputIndex shouldBe 1
                contentIndex shouldBe 0
                delta shouldBe "Hello"
                logprobs?.size shouldBe 1
                logprobs?.first()?.token shouldBe "Hello"
                sequenceNumber shouldBe 9
            }
        }

    @Test
    fun `test ResponseOutputTextDone event serialization`() =
        runWithBothJsonConfigurations("ResponseOutputTextDone serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseOutputTextDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseOutputTextDone(
                        itemId = "item_789",
                        outputIndex = 2,
                        contentIndex = 1,
                        text = "Complete text",
                        logprobs = null,
                        sequenceNumber = 10
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "item_789"
                outputIndex shouldBe 2
                contentIndex shouldBe 1
                text shouldBe "Complete text"
                logprobs shouldBe null
                sequenceNumber shouldBe 10
            }
        }

    @Test
    fun `test ResponseRefusalDelta event serialization`() =
        runWithBothJsonConfigurations("ResponseRefusalDelta serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseRefusalDelta>(
                json.encodeToString(OpenAIStreamEvent.ResponseRefusalDelta("item_ref", 0, 0, "I cannot", 11))
            ).shouldNotBeNull {
                itemId shouldBe "item_ref"
                outputIndex shouldBe 0
                contentIndex shouldBe 0
                delta shouldBe "I cannot"
                sequenceNumber shouldBe 11
            }
        }

    @Test
    fun `test ResponseRefusalDone event serialization`() =
        runWithBothJsonConfigurations("ResponseRefusalDone serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseRefusalDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseRefusalDone(
                        "item_ref_done",
                        0,
                        0,
                        "I cannot help with that",
                        12
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "item_ref_done"
                outputIndex shouldBe 0
                contentIndex shouldBe 0
                refusal shouldBe "I cannot help with that"
                sequenceNumber shouldBe 12
            }
        }

    @Test
    fun `test function call streaming events delta`() =
        runWithBothJsonConfigurations("function call events delta") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseFunctionCallArgumentsDelta(
                        "item_func",
                        0,
                        """{"param":""",
                        13
                    )
                )
            ).shouldNotBeNull {
                delta shouldBe """{"param":"""
                sequenceNumber shouldBe 13
            }
        }

    @Test
    fun `test function call streaming events done`() =
        runWithBothJsonConfigurations("function call events done") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFunctionCallArgumentsDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseFunctionCallArgumentsDone(
                        "item_func",
                        0,
                        """{"param":"value"}""",
                        14
                    )
                )
            ).shouldNotBeNull {
                arguments shouldBe """{"param":"value"}"""
                sequenceNumber shouldBe 14
            }
        }

    @Test
    fun `test file search call in progress event`() =
        runWithBothJsonConfigurations("file search call in progress") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFileSearchCallInProgress>(
                json.encodeToString(OpenAIStreamEvent.ResponseFileSearchCallInProgress("search_item", 0, 15))
            ).shouldNotBeNull {
                itemId shouldBe "search_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 15
            }
        }

    @Test
    fun `test file search call searching event`() =
        runWithBothJsonConfigurations("file search call searching") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFileSearchCallSearching>(
                json.encodeToString(OpenAIStreamEvent.ResponseFileSearchCallSearching("search_item", 0, 16))
            ).shouldNotBeNull {
                itemId shouldBe "search_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 16
            }
        }

    @Test
    fun `test file search call completed event`() =
        runWithBothJsonConfigurations("file search call completed") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseFileSearchCallCompleted>(
                json.encodeToString(OpenAIStreamEvent.ResponseFileSearchCallCompleted("search_item", 0, 17))
            ).shouldNotBeNull {
                itemId shouldBe "search_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 17
            }
        }

    @Test
    fun `test web search call in progress event`() =
        runWithBothJsonConfigurations("web search call in progress") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseWebSearchCallInProgress>(
                json.encodeToString(OpenAIStreamEvent.ResponseWebSearchCallInProgress("web_item", 1, 18))
            ).shouldNotBeNull {
                itemId shouldBe "web_item"
                outputIndex shouldBe 1
                sequenceNumber shouldBe 18
            }
        }

    @Test
    fun `test web search call searching event`() = runWithBothJsonConfigurations("web search call searching") { json ->
        json.decodeFromString<OpenAIStreamEvent.ResponseWebSearchCallSearching>(
            json.encodeToString(OpenAIStreamEvent.ResponseWebSearchCallSearching("web_item", 1, 19))
        ).shouldNotBeNull {
            itemId shouldBe "web_item"
            outputIndex shouldBe 1
            sequenceNumber shouldBe 19
        }
    }

    @Test
    fun `test web search call completed event`() = runWithBothJsonConfigurations("web search call completed") { json ->
        json.decodeFromString<OpenAIStreamEvent.ResponseWebSearchCallCompleted>(
            json.encodeToString(OpenAIStreamEvent.ResponseWebSearchCallCompleted("web_item", 1, 20))
        ).shouldNotBeNull {
            itemId shouldBe "web_item"
            outputIndex shouldBe 1
            sequenceNumber shouldBe 20
        }
    }

    @Test
    fun `test SummaryPart serialization`() = runWithBothJsonConfigurations("SummaryPart serialization") { json ->
        val summaryPart = OpenAIStreamEvent.SummaryPart("Summary text")

        summaryPart.text shouldBe "Summary text"
        summaryPart.type shouldBe "summary_part"

        json.decodeFromString<OpenAIStreamEvent.SummaryPart>(
            json.encodeToString(summaryPart)
        ).shouldNotBeNull {
            text shouldBe "Summary text"
            type shouldBe "summary_part"
        }
    }

    @Test
    fun `test reasoning summary part added event`() =
        runWithBothJsonConfigurations("reasoning summary part added") { json ->
            val summaryPart = OpenAIStreamEvent.SummaryPart("Summary text")
            json.decodeFromString<OpenAIStreamEvent.ResponseReasoningSummaryPartAdded>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseReasoningSummaryPartAdded(
                        "reasoning_item",
                        0,
                        0,
                        summaryPart,
                        21
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "reasoning_item"
                part.text shouldBe "Summary text"
                sequenceNumber shouldBe 21
            }
        }

    @Test
    fun `test reasoning summary part done event`() =
        runWithBothJsonConfigurations("reasoning summary part done") { json ->
            val summaryPart = OpenAIStreamEvent.SummaryPart("Summary text")
            json.decodeFromString<OpenAIStreamEvent.ResponseReasoningSummaryPartDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseReasoningSummaryPartDone(
                        "reasoning_item",
                        0,
                        0,
                        summaryPart,
                        22
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "reasoning_item"
                sequenceNumber shouldBe 22
            }
        }

    @Test
    fun `test reasoning summary text delta event`() =
        runWithBothJsonConfigurations("reasoning summary text delta") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseReasoningSummaryTextDelta>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseReasoningSummaryTextDelta(
                        "reasoning_item",
                        0,
                        0,
                        "Summary",
                        23
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "reasoning_item"
                delta shouldBe "Summary"
                sequenceNumber shouldBe 23
            }
        }

    @Test
    fun `test reasoning summary text done event`() =
        runWithBothJsonConfigurations("reasoning summary text done") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseReasoningSummaryTextDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseReasoningSummaryTextDone(
                        "reasoning_item",
                        0,
                        0,
                        "Summary complete",
                        24
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "reasoning_item"
                text shouldBe "Summary complete"
                sequenceNumber shouldBe 24
            }
        }

    @Test
    fun `test reasoning text delta event`() = runWithBothJsonConfigurations("reasoning text delta") { json ->
        json.decodeFromString<OpenAIStreamEvent.ResponseReasoningTextDelta>(
            json.encodeToString(
                OpenAIStreamEvent.ResponseReasoningTextDelta(
                    "reasoning_item",
                    0,
                    0,
                    "Thinking",
                    25
                )
            )
        ).shouldNotBeNull {
            itemId shouldBe "reasoning_item"
            delta shouldBe "Thinking"
            sequenceNumber shouldBe 25
        }
    }

    @Test
    fun `test reasoning text done event`() = runWithBothJsonConfigurations("reasoning text done") { json ->
        json.decodeFromString<OpenAIStreamEvent.ResponseReasoningTextDone>(
            json.encodeToString(
                OpenAIStreamEvent.ResponseReasoningTextDone(
                    "reasoning_item",
                    0,
                    0,
                    "Thinking complete",
                    26
                )
            )
        ).shouldNotBeNull {
            itemId shouldBe "reasoning_item"
            text shouldBe "Thinking complete"
            sequenceNumber shouldBe 26
        }
    }

    @Test
    fun `test image generation call in progress event`() =
        runWithBothJsonConfigurations("image generation call in progress") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseImageGenerationCallInProgress>(
                json.encodeToString(OpenAIStreamEvent.ResponseImageGenerationCallInProgress("img_item", 0, 27))
            ).shouldNotBeNull {
                itemId shouldBe "img_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 27
            }
        }

    @Test
    fun `test image generation call generating event`() =
        runWithBothJsonConfigurations("image generation call generating") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseImageGenerationCallGenerating>(
                json.encodeToString(OpenAIStreamEvent.ResponseImageGenerationCallGenerating("img_item", 0, 28))
            ).shouldNotBeNull {
                itemId shouldBe "img_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 28
            }
        }

    @Test
    fun `test image generation call partial image event`() =
        runWithBothJsonConfigurations("image generation call partial image") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseImageGenerationCallPartialImage>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseImageGenerationCallPartialImage(
                        "img_item",
                        0,
                        0,
                        "base64data",
                        29
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "img_item"
                outputIndex shouldBe 0
                partialImageIndex shouldBe 0
                partialImageB64 shouldBe "base64data"
                sequenceNumber shouldBe 29
            }
        }

    @Test
    fun `test image generation call completed event`() =
        runWithBothJsonConfigurations("image generation call completed") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseImageGenerationCallCompleted>(
                json.encodeToString(OpenAIStreamEvent.ResponseImageGenerationCallCompleted("img_item", 0, 30))
            ).shouldNotBeNull {
                itemId shouldBe "img_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 30
            }
        }

    @Test
    fun `test code interpreter call in progress event`() =
        runWithBothJsonConfigurations("code interpreter call in progress") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCodeInterpreterCallInProgress>(
                json.encodeToString(OpenAIStreamEvent.ResponseCodeInterpreterCallInProgress("code_item", 0, 39))
            ).shouldNotBeNull {
                itemId shouldBe "code_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 39
            }
        }

    @Test
    fun `test code interpreter call interpreting event`() =
        runWithBothJsonConfigurations("code interpreter call interpreting") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCodeInterpreterCallInterpreting>(
                json.encodeToString(OpenAIStreamEvent.ResponseCodeInterpreterCallInterpreting("code_item", 0, 40))
            ).shouldNotBeNull {
                itemId shouldBe "code_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 40
            }
        }

    @Test
    fun `test code interpreter call completed event`() =
        runWithBothJsonConfigurations("code interpreter call completed") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCodeInterpreterCallCompleted>(
                json.encodeToString(OpenAIStreamEvent.ResponseCodeInterpreterCallCompleted("code_item", 0, 41))
            ).shouldNotBeNull {
                itemId shouldBe "code_item"
                outputIndex shouldBe 0
                sequenceNumber shouldBe 41
            }
        }

    @Test
    fun `test code interpreter call code delta event`() =
        runWithBothJsonConfigurations("code interpreter call code delta") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCodeInterpreterCallCodeDelta>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseCodeInterpreterCallCodeDelta(
                        "code_item",
                        0,
                        "print(",
                        42
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "code_item"
                outputIndex shouldBe 0
                delta shouldBe "print("
                sequenceNumber shouldBe 42
            }
        }

    @Test
    fun `test code interpreter call code done event`() =
        runWithBothJsonConfigurations("code interpreter call code done") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCodeInterpreterCallCodeDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseCodeInterpreterCallCodeDone(
                        "code_item",
                        0,
                        "print('Hello')",
                        43
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "code_item"
                outputIndex shouldBe 0
                code shouldBe "print('Hello')"
                sequenceNumber shouldBe 43
            }
        }

    @Test
    fun `test output text annotation added event`() =
        runWithBothJsonConfigurations("output text annotation added") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseOutputTextAnnotationAdded>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseOutputTextAnnotationAdded(
                        "item_anno",
                        0,
                        0,
                        0,
                        buildJsonObject { put("type", JsonPrimitive("test")) },
                        44
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "item_anno"
                outputIndex shouldBe 0
                contentIndex shouldBe 0
                annotationIndex shouldBe 0
                sequenceNumber shouldBe 44
            }
        }

    @Test
    fun `test response queued event`() = runWithBothJsonConfigurations("response queued") { json ->
        json.decodeFromString<OpenAIStreamEvent.ResponseQueued>(
            json.encodeToString(
                OpenAIStreamEvent.ResponseQueued(
                    OpenAIResponsesAPIResponse(
                        created = 1699500000L,
                        id = "queued_resp",
                        model = "gpt-4o",
                        output = emptyList(),
                        parallelToolCalls = false,
                        status = OpenAIInputStatus.QUEUED,
                        text = OpenAITextConfig()
                    ),
                    45
                )
            )
        ).shouldNotBeNull {
            response.id shouldBe "queued_resp"
            response.status shouldBe OpenAIInputStatus.QUEUED
            sequenceNumber shouldBe 45
        }
    }

    @Test
    fun `test custom tool call input delta event`() =
        runWithBothJsonConfigurations("custom tool call input delta") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCustomToolCallInputDelta>(
                json.encodeToString(OpenAIStreamEvent.ResponseCustomToolCallInputDelta("custom_item", 0, "input_", 46))
            ).shouldNotBeNull {
                itemId shouldBe "custom_item"
                outputIndex shouldBe 0
                delta shouldBe "input_"
                sequenceNumber shouldBe 46
            }
        }

    @Test
    fun `test custom tool call input done event`() =
        runWithBothJsonConfigurations("custom tool call input done") { json ->
            json.decodeFromString<OpenAIStreamEvent.ResponseCustomToolCallInputDone>(
                json.encodeToString(
                    OpenAIStreamEvent.ResponseCustomToolCallInputDone(
                        "custom_item",
                        0,
                        "input_data",
                        47
                    )
                )
            ).shouldNotBeNull {
                itemId shouldBe "custom_item"
                outputIndex shouldBe 0
                input shouldBe "input_data"
                sequenceNumber shouldBe 47
            }
        }

    @Test
    fun `test stream error event`() = runWithBothJsonConfigurations("stream error") { json ->
        json.decodeFromString<OpenAIStreamEvent.Error>(
            json.encodeToString(OpenAIStreamEvent.Error("invalid_request", "Bad request", "param", 48))
        ).shouldNotBeNull {
            code shouldBe "invalid_request"
            message shouldBe "Bad request"
            param shouldBe "param"
            sequenceNumber shouldBe 48
        }
    }

    @Test
    fun `test LogProb and LogProbWithTop serialization`() =
        runWithBothJsonConfigurations("LogProb serialization") { json ->
            json.decodeFromString<OpenAIStreamEvent.LogProb>(
                json.encodeToString(OpenAIStreamEvent.LogProb(-0.5, "token"))
            ).shouldNotBeNull {
                logprob shouldBe -0.5
                token shouldBe "token"
            }

            json.decodeFromString<OpenAIStreamEvent.LogProbWithTop>(
                json.encodeToString(
                    OpenAIStreamEvent.LogProbWithTop(
                        -0.3,
                        "main_token",
                        listOf(
                            OpenAIStreamEvent.LogProb(-0.3, "main_token"),
                            OpenAIStreamEvent.LogProb(-1.2, "alt_token")
                        )
                    )
                )
            ).shouldNotBeNull {
                logprob shouldBe -0.3
                token shouldBe "main_token"
                topLogprobs.size shouldBe 2
                topLogprobs[1].token shouldBe "alt_token"
                topLogprobs[1].logprob shouldBe -1.2
            }
        }
}
