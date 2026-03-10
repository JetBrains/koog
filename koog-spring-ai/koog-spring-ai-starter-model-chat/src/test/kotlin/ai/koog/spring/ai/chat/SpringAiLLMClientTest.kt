package ai.koog.spring.ai.chat

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.model.tool.ToolCallingChatOptions
import reactor.core.publisher.Flux
import kotlin.time.Clock
import org.springframework.ai.chat.prompt.Prompt as SpringPrompt

class SpringAiLLMClientTest {

    private val testModel = LLModel(
        provider = LLMProvider.Ollama,
        id = "test-model",
        capabilities = listOf(LLMCapability.Temperature, LLMCapability.Tools),
        contextLength = 4096
    )

    private fun createPrompt(vararg messages: Message): Prompt =
        Prompt(messages.toList(), "test-prompt", LLMParams())

    private fun requestMeta() = RequestMetaInfo.create(Clock.System)

    // ---- llmProvider ----

    @Test
    fun `llmProvider returns SpringAiLLMProvider by default`() {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        assertTrue(client.llmProvider() is SpringAiLLMProvider)
    }

    @Test
    fun `llmProvider returns custom provider when specified`() {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).provider(LLMProvider.Ollama).build()
        assertEquals(LLMProvider.Ollama, client.llmProvider())
    }

    // ---- models ----

    @Test
    fun `models returns LLModel with id from defaultOptions when model name is set`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
            override fun getDefaultOptions() = ToolCallingChatOptions.builder().model("gpt-4o").build()
        }).build()
        val models = client.models()

        assertEquals(1, models.size)
        assertEquals("gpt-4o", models[0].id)
        assertTrue(models[0].provider is SpringAiLLMProvider)
    }

    @Test
    fun `models returns empty list when defaultOptions has no model name`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
            override fun getDefaultOptions() = ToolCallingChatOptions.builder().build()
        }).build()
        val models = client.models()

        assertTrue(models.isEmpty())
    }

    @Test
    fun `models uses custom provider when specified`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
            override fun getDefaultOptions() = ToolCallingChatOptions.builder().model("llama3").build()
        }).provider(LLMProvider.Ollama).build()
        val models = client.models()

        assertEquals(1, models.size)
        assertEquals("llama3", models[0].id)
        assertEquals(LLMProvider.Ollama, models[0].provider)
    }

    // ---- execute ----

    @Test
    fun `execute returns assistant message from chat model response`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) =
                ChatResponse(listOf(Generation(AssistantMessage("Hello!"))))

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(
            Message.System("Be helpful.", requestMeta()),
            Message.User("Hi", requestMeta())
        )
        val results = client.execute(prompt, testModel, emptyList())

        assertEquals(1, results.size)
        assertTrue(results[0] is Message.Assistant)
        assertEquals("Hello!", results[0].content)
    }

    @Test
    fun `execute returns tool call messages when model responds with tool calls`() = runBlocking {
        val toolCall = AssistantMessage.ToolCall("id-1", "function", "get_weather", """{"city":"Paris"}""")
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) =
                ChatResponse(listOf(Generation(AssistantMessage.builder().toolCalls(listOf(toolCall)).build())))

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("What's the weather?", requestMeta()))
        val results = client.execute(prompt, testModel, emptyList())

        assertEquals(1, results.size)
        assertTrue(results[0] is Message.Tool.Call)
        val call = results[0] as Message.Tool.Call
        // Bug check: tool name and id must not be swapped
        assertEquals("id-1", call.id)
        assertEquals("get_weather", call.tool)
        assertEquals("""{"city":"Paris"}""", call.content)
    }

    @Test
    fun `execute maps usage metadata to response meta info`() = runBlocking {
        val usage = object : Usage {
            override fun getPromptTokens(): Int = 5
            override fun getCompletionTokens(): Int = 15
            override fun getNativeUsage(): Any = emptyMap<String, Any>()
        }
        val metadata = ChatResponseMetadata.builder().usage(usage).build()
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) =
                ChatResponse(listOf(Generation(AssistantMessage("Done"))), metadata)

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        val results = client.execute(prompt, testModel, emptyList())

        val metaInfo = (results[0] as Message.Assistant).metaInfo
        // Bug check: prompt tokens -> inputTokensCount, completion -> outputTokensCount (not swapped)
        assertEquals(5, metaInfo.inputTokensCount)
        assertEquals(15, metaInfo.outputTokensCount)
        assertEquals(20, metaInfo.totalTokensCount)
    }

    @Test
    fun `execute passes ToolCallingChatOptions when tools are provided`() = runBlocking {
        var capturedPrompt: SpringPrompt? = null
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse {
                capturedPrompt = prompt
                return ChatResponse(listOf(Generation(AssistantMessage("ok"))))
            }

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val tools = listOf(
            ToolDescriptor(
                "my_tool",
                "A tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor("arg", "An arg", ToolParameterType.String)
                )
            )
        )
        val prompt = createPrompt(Message.User("Use a tool", requestMeta()))
        client.execute(prompt, testModel, tools)

        assertTrue(capturedPrompt?.options is ToolCallingChatOptions)
    }

    @Test
    fun `execute passes model id in chat options`() = runBlocking {
        var capturedPrompt: SpringPrompt? = null
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse {
                capturedPrompt = prompt
                return ChatResponse(listOf(Generation(AssistantMessage("ok"))))
            }

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        client.execute(prompt, testModel, emptyList())

        assertEquals("test-model", capturedPrompt?.options?.model)
    }

    @Test
    fun `execute flattens multiple generations`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = ChatResponse(
                listOf(
                    Generation(AssistantMessage("First")),
                    Generation(AssistantMessage("Second"))
                )
            )

            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        val results = client.execute(prompt, testModel, emptyList())

        assertEquals(2, results.size)
        assertEquals("First", results[0].content)
        assertEquals("Second", results[1].content)
    }

    // ---- executeStreaming ----

    @Test
    fun `executeStreaming emits TextDelta frames for text responses`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt) = Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage("Hello")))),
                ChatResponse(listOf(Generation(AssistantMessage(" world"))))
            )
        }).build()
        val prompt = createPrompt(Message.User("Hi", requestMeta()))
        val frames = client.executeStreaming(prompt, testModel, emptyList()).toList()

        val textFrames = frames.filterIsInstance<StreamFrame.TextDelta>()
        assertEquals(2, textFrames.size)
        // Bug check: text content must be in .text property
        assertEquals("Hello", textFrames[0].text)
        assertEquals(" world", textFrames[1].text)
        assertTrue(frames.last() is StreamFrame.End)
    }

    @Test
    fun `executeStreaming emits ToolCallDelta frames for tool call responses`() = runBlocking {
        val toolCall = AssistantMessage.ToolCall("tc-1", "function", "search", """{"q":"test"}""")
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt) = Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage.builder().toolCalls(listOf(toolCall)).build())))
            )
        }).build()
        val prompt = createPrompt(Message.User("Search something", requestMeta()))
        val frames = client.executeStreaming(prompt, testModel, emptyList()).toList()

        val toolFrames = frames.filterIsInstance<StreamFrame.ToolCallDelta>()
        assertEquals(1, toolFrames.size)
        // Bug check: name and id must not be swapped
        assertEquals("tc-1", toolFrames[0].id)
        assertEquals("search", toolFrames[0].name)
        assertEquals("""{"q":"test"}""", toolFrames[0].content)
        assertTrue(frames.last() is StreamFrame.End)
    }

    @Test
    fun `executeStreaming buffers tool call chunks for unverified providers`() = runBlocking {
        val firstChunk = AssistantMessage.ToolCall("tc-1", "function", "search", """{"q":""")
        val secondChunk = AssistantMessage.ToolCall("tc-1", "function", "search", """"test"}""")
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt) = Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage.builder().toolCalls(listOf(firstChunk)).build()))),
                ChatResponse(listOf(Generation(AssistantMessage.builder().toolCalls(listOf(secondChunk)).build())))
            )
        }).provider(LLMProvider.Ollama).build()
        val prompt = createPrompt(Message.User("Search something", requestMeta()))
        val frames = client.executeStreaming(prompt, testModel, emptyList()).toList()

        val toolFrames = frames.filterIsInstance<StreamFrame.ToolCallDelta>()
        assertEquals(1, toolFrames.size)
        assertEquals("tc-1", toolFrames[0].id)
        assertEquals("search", toolFrames[0].name)
        assertEquals("""{"q":"test"}""", toolFrames[0].content)

        val completeFrames = frames.filterIsInstance<StreamFrame.ToolCallComplete>()
        assertEquals(1, completeFrames.size)
        assertEquals("""{"q":"test"}""", completeFrames[0].content)
        assertTrue(frames.last() is StreamFrame.End)
    }

    @Test
    fun `executeStreaming always emits End frame`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt) = Flux.empty<ChatResponse>()
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        val frames = client.executeStreaming(prompt, testModel, emptyList()).toList()

        assertEquals(1, frames.size)
        assertTrue(frames[0] is StreamFrame.End)
    }

    @Test
    fun `executeStreaming skips empty text chunks`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt) = Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage("")))),
                ChatResponse(listOf(Generation(AssistantMessage("Hi"))))
            )
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        val frames = client.executeStreaming(prompt, testModel, emptyList()).toList()

        val textFrames = frames.filterIsInstance<StreamFrame.TextDelta>()
        assertEquals(1, textFrames.size)
        assertEquals("Hi", textFrames[0].text)
    }

    // ---- moderate ----

    @Test
    fun `moderate throws UnsupportedOperationException`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        try {
            client.moderate(prompt, testModel)
            assertTrue(false, "Should have thrown UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // expected
        }
    }

    @Test
    fun `moderate throws UnsupportedOperationException for multimodal prompt`() = runBlocking {
        val moderationModel = object : org.springframework.ai.moderation.ModerationModel {
            override fun call(request: org.springframework.ai.moderation.ModerationPrompt): org.springframework.ai.moderation.ModerationResponse =
                throw AssertionError("Should not be called for multimodal prompts")
        }
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt) = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).moderationModel(moderationModel).build()

        val multimodalMessage = Message.User(
            listOf(
                ContentPart.Text("Describe this image"),
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)),
                    format = "png"
                )
            ),
            requestMeta()
        )
        val prompt = createPrompt(multimodalMessage)
        val exception = assertThrows<UnsupportedOperationException> {
            client.moderate(prompt, testModel)
        }
        assertTrue(exception.message!!.contains("non-text content"))
    }

    // ---- exception translation ----

    @Test
    fun `execute wraps ChatModel exceptions in LLMClientException`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse = throw RuntimeException("Connection refused")
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).build()
        val prompt = createPrompt(Message.User("Hi", requestMeta()))
        val exception = assertThrows<LLMClientException> {
            client.execute(prompt, testModel, emptyList())
        }
        assertTrue(exception.message!!.contains("Connection refused"))
        assertTrue(exception.cause is RuntimeException)
    }

    @Test
    fun `executeStreaming wraps ChatModel stream exceptions in LLMClientException`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw RuntimeException("Rate limited")
        }).build()
        val prompt = createPrompt(Message.User("Hi", requestMeta()))
        val exception = assertThrows<LLMClientException> {
            client.executeStreaming(prompt, testModel, emptyList()).toList()
        }
        assertTrue(exception.message!!.contains("Rate limited"))
        assertTrue(exception.cause is RuntimeException)
    }

    @Test
    fun `executeStreaming wraps collection errors in LLMClientException`() = runBlocking {
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> =
                Flux.error(RuntimeException("Stream interrupted"))
        }).build()
        val prompt = createPrompt(Message.User("Hi", requestMeta()))
        val exception = assertThrows<LLMClientException> {
            client.executeStreaming(prompt, testModel, emptyList()).toList()
        }
        assertTrue(exception.message!!.contains("Stream interrupted"))
        assertTrue(exception.cause is RuntimeException)
    }

    @Test
    fun `moderate wraps ModerationModel exceptions in LLMClientException`() = runBlocking {
        val moderationModel = object : org.springframework.ai.moderation.ModerationModel {
            override fun call(request: org.springframework.ai.moderation.ModerationPrompt): org.springframework.ai.moderation.ModerationResponse =
                throw RuntimeException("Service unavailable")
        }
        val client = SpringAiLLMClient.builder().chatModel(object : ChatModel {
            override fun call(prompt: SpringPrompt): ChatResponse = throw UnsupportedOperationException()
            override fun stream(prompt: SpringPrompt): Flux<ChatResponse> = throw UnsupportedOperationException()
        }).moderationModel(moderationModel).build()
        val prompt = createPrompt(Message.User("Hello", requestMeta()))
        val exception = assertThrows<LLMClientException> {
            client.moderate(prompt, testModel)
        }
        assertTrue(exception.message!!.contains("ModerationModel.call() failed"))
        assertTrue(exception.cause is RuntimeException)
    }
}
