package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.executor.clients.LLMClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

/**
 * RetroLLMFit — annotation-driven [LLMClient] factory.
 *
 * Inspired by Retrofit: annotate your request and response shapes,
 * call [create], and get a fully wired [LLMClient] with zero boilerplate.
 *
 * ### Minimal example (Jaika):
 * ```kotlin
 * @LLMEndpoint(
 *     url = "https://my-server.com/api/prompt",
 *     authHeaderName = "X-User-Id",
 *     authHeaderValue = "my-user-id"
 * )
 * @Serializable
 * data class MyRequest(@PromptField val prompt: String, val stream: Boolean = false)
 *
 * @Serializable
 * data class MyResponse(@ResponseTextField val text: String)
 *
 * val client = RetroLLMFit.create<MyRequest, MyResponse>()
 * val result  = client.execute(prompt, RetroLLMFitModel)
 * ```
 *
 * ### Rules
 * - The **request** class must be annotated with [@LLMEndpoint] and have exactly one field with [@PromptField].
 * - The **response** class must have exactly one field with [@ResponseTextField].
 * - Both classes must be `@Serializable`.
 * - All non-prompt fields in the request class must have default values.
 */
public object RetroLLMFit {

    /**
     * Creates an [LLMClient] from annotated [Req] and [Res] data classes.
     *
     * @param httpClient Optional Ktor [HttpClient] — useful for testing (inject a MockEngine).
     *                   Defaults to a CIO client with JSON content negotiation.
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
        val endpoint = reqClass.findAnnotation<LLMEndpoint>()
            ?: error(
                "RetroLLMFit: ${reqClass.simpleName} must be annotated with @LLMEndpoint. " +
                    "Add @LLMEndpoint(url = \"https://...\") to the class."
            )

        val ctor = reqClass.primaryConstructor
            ?: error("RetroLLMFit: ${reqClass.simpleName} must have a primary constructor.")

        val promptParam = ctor.parameters.firstOrNull { it.hasAnnotation<PromptField>() }
            ?: error(
                "RetroLLMFit: ${reqClass.simpleName} has no parameter annotated with @PromptField. " +
                    "Mark the prompt String parameter with @PromptField."
            )

        require(promptParam.type.classifier == String::class) {
            "RetroLLMFit: @PromptField on '${promptParam.name}' must be a String."
        }

        // Kotlin @Target(PROPERTY) annotations live in class metadata, not as JVM runtime annotations.
        // We find the field by checking the backing Java field or the constructor parameter name instead.
        val textProperty = resClass.memberProperties.firstOrNull { prop ->
            prop.javaField?.isAnnotationPresent(ResponseTextField::class.java) == true
        } ?: run {
            // Fallback: find via constructor parameter annotation
            val ctor = resClass.primaryConstructor
            val paramName = ctor?.parameters?.firstOrNull { it.hasAnnotation<ResponseTextField>() }?.name
            paramName?.let { name -> resClass.memberProperties.firstOrNull { it.name == name } }
        } ?: error(
            "RetroLLMFit: ${resClass.simpleName} has no property annotated with @ResponseTextField. " +
                "Mark the reply String property with @ResponseTextField."
        )

        require(textProperty.returnType.classifier == String::class) {
            "RetroLLMFit: @ResponseTextField on '${textProperty.name}' must be a String property."
        }

        val authHeader: Pair<String, String>? =
            if (endpoint.authHeaderName.isNotBlank())
                endpoint.authHeaderName to endpoint.authHeaderValue
            else null

        val buildRequest: (String) -> Req = { promptText ->
            ctor.callBy(mapOf(promptParam to promptText))
        }

        @Suppress("UNCHECKED_CAST")
        val extractText: (Res) -> String = { res ->
            textProperty.getter.call(res) as String
        }

        @Suppress("UNCHECKED_CAST")
        return RetroLLMFitClient(
            endpointUrl = endpoint.url,
            authHeader = authHeader,
            buildRequest = buildRequest,
            extractText = extractText,
            requestSerializer = serializer(reqClass.java) as kotlinx.serialization.KSerializer<Req>,
            responseSerializer = serializer(resClass.java) as kotlinx.serialization.KSerializer<Res>,
            httpClient = httpClient,
        )
    }

    /** Default Ktor HttpClient with JSON content negotiation. */
    public fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}
