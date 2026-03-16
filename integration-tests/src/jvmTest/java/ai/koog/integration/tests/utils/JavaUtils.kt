package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow.Publisher
import java.util.concurrent.Flow.Subscriber
import java.util.concurrent.Flow.Subscription
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

object JavaUtils {
    @JvmStatic
    fun <T : Any> requestLLMStructuredBlocking(
        context: AIAgentFunctionalContext,
        message: String,
        outputType: Class<T>
    ): T = runBlocking {
        context.requestLLMStructured(message, outputType.kotlin, emptyList(), null).getOrThrow().data
    }

    // Storage helpers for Java interop
    @JvmStatic
    fun <T : Any> storageSet(storage: AIAgentStorage, key: AIAgentStorageKey<T>, value: T): Unit = runBlocking {
        storage.set(key, value)
    }

    @JvmStatic
    fun <T : Any> storageGet(storage: AIAgentStorage, key: AIAgentStorageKey<T>): T? = runBlocking {
        storage.get(key)
    }

    @JvmStatic
    fun historyCompressionStrategiesForJava(): List<HistoryCompressionStrategy> = listOf(
        HistoryCompressionStrategy.WholeHistory,
        HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages,
        HistoryCompressionStrategy.FromLastNMessages(1),
        HistoryCompressionStrategy.FromTimestamp(Clock.System.now().minus(1.seconds)),
        HistoryCompressionStrategy.Chunked(2)
    )

    @JvmStatic
    fun getCheckpointsBlocking(
        storageProvider: PersistenceStorageProvider<*>,
        sessionId: String
    ): List<AgentCheckpointData> = runBlocking {
        storageProvider.getCheckpoints(sessionId)
    }

    @JvmStatic
    fun createOpenAIResponsesParams(
        toolChoice: LLMParams.ToolChoice?,
        numberOfChoices: Int?,
        include: List<OpenAIInclude>?,
        reasoning: ReasoningConfig?,
        maxTokens: Int?
    ): OpenAIResponsesParams = createOpenAIResponsesParams(
        toolChoice,
        numberOfChoices,
        include,
        reasoning,
        maxTokens,
        null
    )

    @JvmStatic
    fun createOpenAIResponsesParams(
        toolChoice: LLMParams.ToolChoice?,
        numberOfChoices: Int?,
        include: List<OpenAIInclude>?,
        reasoning: ReasoningConfig?,
        maxTokens: Int?,
        schema: LLMParams.Schema?
    ): OpenAIResponsesParams = OpenAIResponsesParams(
        null,
        maxTokens,
        numberOfChoices,
        null,
        schema,
        toolChoice,
        null,
        null,
        null,
        include,
        null,
        null,
        reasoning,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    )

    @JvmStatic
    fun createParams(
        provider: LLMProvider,
        toolChoice: LLMParams.ToolChoice?,
        numberOfChoices: Int?,
        include: List<OpenAIInclude>?,
        reasoning: ReasoningConfig?,
        maxTokens: Int?
    ): LLMParams = createParams(
        provider = provider,
        toolChoice = toolChoice,
        numberOfChoices = numberOfChoices,
        include = include,
        reasoning = reasoning,
        maxTokens = maxTokens,
        schema = null
    )

    @JvmStatic
    fun createParams(
        provider: LLMProvider,
        toolChoice: LLMParams.ToolChoice?,
        numberOfChoices: Int?,
        include: List<OpenAIInclude>?,
        reasoning: ReasoningConfig?,
        maxTokens: Int?,
        schema: LLMParams.Schema?
    ): LLMParams = when (provider) {
        LLMProvider.OpenAI -> createOpenAIResponsesParams(
            toolChoice = toolChoice,
            numberOfChoices = numberOfChoices,
            include = include,
            reasoning = reasoning,
            maxTokens = maxTokens,
            schema = schema
        )

        LLMProvider.Anthropic -> AnthropicParams(
            maxTokens = maxTokens,
            numberOfChoices = numberOfChoices,
            schema = schema,
            toolChoice = toolChoice
        )

        LLMProvider.Google -> GoogleParams(
            maxTokens = maxTokens,
            numberOfChoices = numberOfChoices,
            schema = schema,
            toolChoice = toolChoice
        )

        else -> LLMParams(
            maxTokens = maxTokens,
            numberOfChoices = numberOfChoices,
            schema = schema,
            toolChoice = toolChoice
        )
    }

    @JvmStatic
    fun createReasoningStreamingParams(provider: LLMProvider, maxTokens: Int): LLMParams = when (provider) {
        LLMProvider.OpenAI -> createOpenAIResponsesParams(
            toolChoice = null,
            numberOfChoices = null,
            include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
            reasoning = ReasoningConfig(effort = ReasoningEffort.LOW, summary = ReasoningSummary.CONCISE),
            maxTokens = maxTokens
        )

        LLMProvider.Anthropic -> AnthropicParams(
            maxTokens = maxTokens,
            thinking = AnthropicThinking.Enabled(budgetTokens = 1024)
        )

        LLMProvider.Google -> GoogleParams(
            maxTokens = maxTokens,
            thinkingConfig = GoogleThinkingConfig(includeThoughts = true)
        )

        else -> throw IllegalArgumentException("Unsupported reasoning/thinking provider: $provider")
    }

    @JvmStatic
    @Throws(InterruptedException::class)
    fun collectFrames(publisher: Publisher<StreamFrame>): StreamCollectionResult {
        val done = CountDownLatch(1)
        val frames = mutableListOf<StreamFrame>()
        var error: Throwable? = null

        publisher.subscribe(object : Subscriber<StreamFrame> {
            override fun onSubscribe(subscription: Subscription) {
                subscription.request(Long.MAX_VALUE)
            }

            override fun onNext(streamFrame: StreamFrame) {
                frames.add(streamFrame)
            }

            override fun onError(throwable: Throwable) {
                error = throwable
                done.countDown()
            }

            override fun onComplete() {
                done.countDown()
            }
        })

        val completed = done.await(90, TimeUnit.SECONDS)
        if (!completed) {
            return StreamCollectionResult(frames, RuntimeException("Timed out while collecting streaming frames"))
        }

        return StreamCollectionResult(frames, error)
    }

    @JvmStatic
    fun mergeAssistantAndReasoningContent(responses: List<Message.Response>): String = responses
        .asSequence()
        .filter { it is Message.Assistant || it is Message.Reasoning }.joinToString("") { it.content }

    @JvmStatic
    fun firstAssistantContent(responses: List<Message.Response>): String = responses
        .firstOrNull { it is Message.Assistant }
        ?.content
        .orEmpty()

    @JvmStatic
    fun weatherSchemaJson(): JsonObject {
        val schemaJson = """
            {
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "city":{"type":"string"},
                "temperature":{"type":"integer"},
                "description":{"type":"string"},
                "humidity":{"type":"integer"}
              },
              "required":["city","temperature","description","humidity"]
            }
        """.trimIndent()
        return Json.parseToJsonElement(schemaJson) as JsonObject
    }

    class StreamCollectionResult(
        val frames: List<StreamFrame>,
        val error: Throwable?
    )
}
