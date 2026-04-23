package ai.koog.prompt.executor.clients.retrollmfit

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

/**
 * Marks a data class as an LLM endpoint descriptor.
 * Place this on your request class to configure the HTTP endpoint, authentication, and routing.
 *
 * Example:
 * ```kotlin
 * @LLMEndpoint(
 *     url = "https://my-model-server.com/api/prompt",
 *     authHeaderName = "X-Api-Key",
 *     authHeaderValue = "my-secret-key"
 * )
 * @Serializable
 * data class MyRequest(@PromptField val prompt: String, val stream: Boolean = false)
 * ```
 */
@Target(CLASS)
@Retention(RUNTIME)
public annotation class LLMEndpoint(
    val url: String,
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
)

/**
 * Marks the constructor parameter / property that should receive the flattened prompt text.
 * Must be on a `String` field. Exactly one field per request class must carry this annotation.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class PromptField

/**
 * Marks the constructor parameter / property in the response class that contains the model's reply text.
 * Must be on a `String` field. Exactly one field per response class must carry this annotation.
 */
@Target(PROPERTY, VALUE_PARAMETER, FIELD)
@Retention(RUNTIME)
public annotation class ResponseTextField
