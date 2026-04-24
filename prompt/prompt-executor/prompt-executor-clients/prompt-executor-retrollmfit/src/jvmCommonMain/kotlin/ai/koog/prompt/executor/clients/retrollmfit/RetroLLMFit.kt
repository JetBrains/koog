package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.executor.clients.LLMClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

/**
 * RetroLLMFit — annotation-driven [LLMClient] factory.
 *
 * Inspired by Retrofit: annotate your request and response shapes, call [create], and get a
 * fully wired [LLMClient] with zero boilerplate.
 *
 * ### Supported capabilities
 *
 * | Annotation on request field  | What it does                                                  |
 * |------------------------------|---------------------------------------------------------------|
 * | `@PromptField`               | Injects the flat prompt string (all messages joined)          |
 * | `@MessagesField(format)`     | Injects the messages array ([MessageFormat.OPENAI_CHAT], …)   |
 * | `@ModelField`                | Injects `LLModel.id`                                          |
 * | `@ToolsField(format)`        | Injects tool definitions; enables tool calling                |
 * | `@StreamField`               | Marks the streaming boolean; enables `executeStreaming()`     |
 *
 * | Annotation on response class | What it does                                                  |
 * |------------------------------|---------------------------------------------------------------|
 * | `@ResponseTextField`         | Extracts text from a flat field                               |
 * | `@ResponseText(path)`        | Extracts text via dot-path, e.g. `"choices[0].message.content"` |
 * | `@ResponseFinishReason(path)`| Path to `finish_reason`; detects tool-call vs text responses  |
 * | `@ResponseToolCalls(path)`   | Path to the tool-calls array                                  |
 * | `@ResponseStreamDelta(path)` | Path to the text delta inside each streaming chunk            |
 *
 * ### Flat-API example (Jaika):
 * ```kotlin
 * @LLMEndpoint(url = "https://…/api/prompt", authHeaderName = "X-User-Id", authHeaderValue = "…")
 * @Serializable
 * data class JaikaRequest(@PromptField val prompt: String, val stream: Boolean = false)
 *
 * @Serializable
 * data class JaikaResponse(@ResponseTextField val text: String)
 *
 * val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>()
 * ```
 *
 * ### Chat + tool-calling + streaming example (OpenAI-compatible):
 * ```kotlin
 * @LLMEndpoint(url = "https://api.openai.com/v1/chat/completions",
 *              authHeaderName = "Authorization", authHeaderValue = "Bearer $KEY")
 * @Serializable
 * data class ChatReq(
 *     @ModelField   val model: String = "gpt-4o",
 *     @MessagesField val messages: List<JsonObject> = emptyList(),
 *     @ToolsField    val tools: List<JsonObject>? = null,
 *     @StreamField   val stream: Boolean = false,
 * )
 *
 * @ResponseText("choices[0].message.content")
 * @ResponseFinishReason("choices[0].finish_reason")
 * @ResponseToolCalls("choices[0].message.tool_calls")
 * @ResponseStreamDelta("choices[0].delta.content")
 * @Serializable
 * data class ChatRes(val choices: List<JsonObject> = emptyList())
 *
 * val client = RetroLLMFit.create<ChatReq, ChatRes>()
 * // client.execute(), client.executeStreaming(), client.execute(…, tools) all work.
 * ```
 */
public object RetroLLMFit {

    /**
     * Creates a fully wired [LLMClient] from annotated [Req] and [Res] classes.
     *
     * @param httpClient Optional Ktor [HttpClient] (useful for testing with MockEngine).
     *                   Defaults to a CIO client with JSON + SSE support.
     */
    public inline fun <reified Req : Any, reified Res : Any> create(
        httpClient: HttpClient = defaultHttpClient(),
    ): LLMClient = createInternal(Req::class, Res::class, httpClient)

    @PublishedApi
    internal fun <Req : Any, Res : Any> createInternal(
        reqClass: KClass<Req>,
        resClass: KClass<Res>,
        httpClient: HttpClient,
    ): LLMClient {
        // ── Endpoint ──────────────────────────────────────────────────────────
        val endpoint = reqClass.findAnnotation<LLMEndpoint>()
            ?: error("RetroLLMFit: ${reqClass.simpleName} must be annotated with @LLMEndpoint.")

        val ctor = reqClass.primaryConstructor
            ?: error("RetroLLMFit: ${reqClass.simpleName} must have a primary constructor.")

        // ── Request params ────────────────────────────────────────────────────
        val params = mutableListOf<Pair<kotlin.reflect.KParameter, ParamKind>>()

        for (param in ctor.parameters) {
            when {
                param.hasAnnotation<PromptField>()   -> params += param to ParamKind.PromptFlat
                param.hasAnnotation<ModelField>()    -> params += param to ParamKind.Model
                param.hasAnnotation<StreamField>()   -> params += param to ParamKind.Stream
                param.hasAnnotation<MessagesField>() -> {
                    val ser = param.findAnnotation<MessagesField>()?.serializer?.instantiate() ?: OpenAIChatSerializer
                    params += param to ParamKind.Messages(ser)
                }
                param.hasAnnotation<ToolsField>() -> {
                    val fmt = param.findAnnotation<ToolsField>()?.format ?: ToolFormat.OPENAI
                    params += param to ParamKind.Tools(fmt)
                }
                else -> {
                    // Also check backing field annotations (Kotlin @Target(FIELD))
                    val prop = reqClass.memberProperties.firstOrNull { it.name == param.name }
                    when {
                        prop?.hasJvmAnnotation(PromptField::class.java) == true ->
                            params += param to ParamKind.PromptFlat
                        prop?.hasJvmAnnotation(MessagesField::class.java) == true -> {
                            val ser = prop.javaField?.getAnnotation(MessagesField::class.java)?.serializer?.instantiate() ?: OpenAIChatSerializer
                            params += param to ParamKind.Messages(ser)
                        }
                        prop?.hasJvmAnnotation(ModelField::class.java) == true ->
                            params += param to ParamKind.Model
                        prop?.hasJvmAnnotation(ToolsField::class.java) == true -> {
                            val fmt = prop.javaField?.getAnnotation(ToolsField::class.java)?.format ?: ToolFormat.OPENAI
                            params += param to ParamKind.Tools(fmt)
                        }
                        prop?.hasJvmAnnotation(StreamField::class.java) == true ->
                            params += param to ParamKind.Stream
                    }
                }
            }
        }

        require(params.isNotEmpty()) {
            "RetroLLMFit: ${reqClass.simpleName} has no annotated parameters. " +
                "Add @PromptField, @MessagesField, @ModelField, @ToolsField, or @StreamField."
        }

        // ── Response meta ─────────────────────────────────────────────────────
        // Field-level @ResponseTextField (flat APIs)
        val textProperty = resClass.memberProperties.firstOrNull { prop ->
            prop.hasJvmAnnotation(ResponseTextField::class.java)
        } ?: resClass.primaryConstructor?.parameters
            ?.firstOrNull { it.hasAnnotation<ResponseTextField>() }
            ?.let { p -> resClass.memberProperties.firstOrNull { it.name == p.name } }

        // Class-level JSON-path annotations (chat APIs)
        val responseText         = resClass.findAnnotation<ResponseText>()?.path
        val responseFinishReason = resClass.findAnnotation<ResponseFinishReason>()
        val responseToolCalls    = resClass.findAnnotation<ResponseToolCalls>()
        val responseStreamDelta  = resClass.findAnnotation<ResponseStreamDelta>()?.path

        require(textProperty != null || responseText != null) {
            "RetroLLMFit: ${resClass.simpleName} has no text extraction. " +
                "Add @ResponseTextField on a String field, or @ResponseText(\"…\") on the class."
        }

        textProperty?.let {
            require(it.returnType.classifier == String::class) {
                "RetroLLMFit: @ResponseTextField on '${it.name}' must be a String property."
            }
        }

        val resMeta = ResponseMeta(
            textProperty = textProperty,
            textPath = responseText,
            finishReasonPath = responseFinishReason?.path,
            finishReasonToolCallValue = responseFinishReason?.toolCallValue ?: "tool_calls",
            toolCallsPath = responseToolCalls?.path,
            toolCallsFormat = responseToolCalls?.format ?: ToolFormat.OPENAI,
            streamDeltaPath = responseStreamDelta,
        )

        val authHeader: Pair<String, String>? =
            if (endpoint.authHeaderName.isNotBlank()) endpoint.authHeaderName to endpoint.authHeaderValue
            else null

        val authQueryParam: Pair<String, String>? =
            if (endpoint.authQueryParam.isNotBlank()) endpoint.authQueryParam to endpoint.authQueryValue
            else null

        @Suppress("UNCHECKED_CAST")
        return RetroLLMFitClient(
            endpointUrl = endpoint.url,
            authHeader = authHeader,
            authQueryParam = authQueryParam,
            reqMeta = RequestMeta(ctor, params),
            resMeta = resMeta,
            requestSerializer = serializer(reqClass.java) as kotlinx.serialization.KSerializer<Req>,
            responseSerializer = serializer(resClass.java) as kotlinx.serialization.KSerializer<Res>,
            httpClient = httpClient,
        )
    }

    /** Default Ktor HttpClient with JSON content negotiation and SSE support. */
    public fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

// Uses objectInstance for Kotlin objects (zero allocation), createInstance() for regular classes.
private fun KClass<out MessagesSerializer>.instantiate(): MessagesSerializer =
    objectInstance ?: createInstance()
