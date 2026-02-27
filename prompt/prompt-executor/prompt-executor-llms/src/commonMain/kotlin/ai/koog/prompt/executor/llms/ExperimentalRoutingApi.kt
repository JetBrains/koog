package ai.koog.prompt.executor.llms

/**
 * Marks an API as part of the experimental routing and load balancing layer for LLM clients.
 *
 * APIs annotated with [ExperimentalRoutingApi] are not considered stable. Routing strategies,
 * client selection interfaces, and related executor behaviour may change, be redesigned, or be
 * removed in future releases without prior notice or a deprecation cycle.
 *
 * To use an annotated API, opt in explicitly at the call site or on the enclosing declaration:
 * ```kotlin
 * @OptIn(ExperimentalRoutingApi::class)
 * fun myCode() { ... }
 * ```
 */
@RequiresOptIn("This API is experimental and is likely to change in the future.")
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalRoutingApi
