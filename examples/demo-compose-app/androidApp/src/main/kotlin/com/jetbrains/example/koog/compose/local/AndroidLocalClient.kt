package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import android.content.Context
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.fc.ModelFormatter
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock


class AndroidLocalClientSettings(
    val timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    val maxTokens: Int = 512,
    val maxTopK: Int = 64,
    val maxTopP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val backend: Backend = Backend.CPU,
    val closeSessionDelay: Long = 1000L,
    val generationDelay: Long = 1000L,
    val formatterProvider: (modelId: String) -> ModelFormatter = ::getFormatterByModelId
)

/**
 * https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
 */
open class AndroidLLocalLLMClient(
    private val context: Context,
    private val modelsPath: String,
    private val settings: AndroidLocalClientSettings = AndroidLocalClientSettings(),
    private val clock: Clock = Clock.System,
) : LLMClient {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(
        prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
//        require(model.capabilities.contains(LLMCapability.Tools) || tools.isEmpty()) {
//            "Model ${model.id} does not support tools"
//        }

        val modelPath = modelsPath + "/${model.id}.task"
        val taskOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(settings.maxTokens)
            .setPreferredBackend(settings.backend)
            .build()
        val llmInference = LlmInference.createFromOptions(context, taskOptions)

        val formatter = settings.formatterProvider(model.id)
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions
            .builder()
            .setTemperature(settings.temperature)
            .setTopK(settings.maxTopK)
            .setTopP(settings.maxTopP)
            .build()
        val session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

        val toolsMessage: String = formatter.formatSystemMessage(
            // TODO: empty system message, why they require it?
            Content.getDefaultInstance(),
            tools.map { ToolDescriptorConverter.convertToProto(it) }
        )
        session.addQueryChunk(toolsMessage)

        prompt.messages.forEach {
            val content = MessageConverter.convertToProto(it)
            val formattedContent = formatter.formatContent(content)
            session.addQueryChunk(formattedContent)
        }

        // TODO: find out why it is needed
        delay(settings.generationDelay)
        val response = session.generateResponse()
        println(response)
        val formattedResponse = formatter.parseResponse(response)

        session.close()
        llmInference.close()
        // TODO: find out why it is needed
        delay(settings.closeSessionDelay)

        val messageParser = MessageParser(clock)
        val results = formattedResponse?.candidatesList?.map { candidate ->
            messageParser.parseMessage(candidate)
        } ?: emptyList()

        return results
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }

        logger.debug { "Executing prompt: $prompt with tools: model: $model" }
        require(model.capabilities.contains(LLMCapability.Completion)) {
            "Model ${model.id} does not support chat completions"
        }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        val modelPath = modelsPath + "/${model.id}.task"
        val taskOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(settings.maxTokens)
            .setPreferredBackend(settings.backend)
            .build()
        val llmInference = LlmInference.createFromOptions(context, taskOptions)

        val formatter = settings.formatterProvider(model.id)
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions
            .builder()
            .setTemperature(settings.temperature)
            .setTopK(settings.maxTopK)
            .setTopP(settings.maxTopP)
            .build()
        val session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

        prompt.messages.forEach {
            val content = MessageConverter.convertToProto(it)
            val formattedContent = formatter.formatContent(content)
            session.addQueryChunk(formattedContent)
        }

        var response = ""

        // TODO: implement on flow
        session.generateResponseAsync { partialResult, done ->
            println(partialResult)
            response += partialResult
        }

        val formattedResponse = formatter.parseResponse(response)

        session.close()
        llmInference.close()

        val messageParser = MessageParser(clock)
        val results = formattedResponse?.candidatesList?.map { candidate ->
            messageParser.parseMessage(candidate)
        } ?: emptyList()

        return flow { results }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        TODO()
    }

    override fun llmProvider(): LLMProvider {
        return AndroidLocalLLMProvider
    }

    override fun close() {

    }
}
