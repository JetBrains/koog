package ai.koog.agents.features.opentelemetry.metric

/**
 * Strategy for controlling cardinality of tool names in OpenTelemetry metrics.
 *
 * Tool names are used as attribute values in metrics. Since metrics with high cardinality
 * can impact performance and storage, this strategy allows transforming or filtering tool names
 * to a limited set of values.
 *
 * Use companion object factory methods to create common strategies:
 * - [passthrough] - no transformation (default)
 * - [allowlist] - only allow specific tool names, map others to fallback
 * - [denylist] - block specific tool names, map them to fallback
 * - [custom] - provide custom transformation logic
 *
 * Example:
 * ```kotlin
 * // Allow only specific tools
 * ToolNameCardinalityStrategy.allowlist(
 *     allowed = setOf("calculator", "weather"),
 *     fallback = "other"
 * )
 *
 * // Custom transformation
 * ToolNameCardinalityStrategy.custom { toolName ->
 *     toolName.take(10).lowercase()
 * }
 * ```
 */
fun interface ToolNameCardinalityStrategy {
    /**
     * Applies the cardinality control strategy to the given tool name.
     *
     * @param toolName The original tool name to transform
     * @return The transformed tool name suitable for use in metrics
     */
    fun apply(toolName: String): String

    companion object {
        /**
         * Creates a passthrough strategy that returns tool names unchanged.
         * This is the default strategy with no cardinality control.
         */
        fun passthrough(): ToolNameCardinalityStrategy =
            ToolNameCardinalityStrategy { it }

        /**
         * Creates an allowlist strategy that only permits specific tool names.
         * Tool names not in the allowed set are replaced with the fallback value.
         *
         * @param allowed Set of permitted tool names
         * @param fallback Value to use for tool names not in the allowed set
         */
        fun allowlist(allowed: Set<String>, fallback: String): ToolNameCardinalityStrategy =
            ToolNameCardinalityStrategy { toolName ->
                if (toolName in allowed) toolName else fallback
            }

        /**
         * Creates a denylist strategy that blocks specific tool names.
         * Tool names in the denied set are replaced with the fallback value.
         *
         * @param denied Set of blocked tool names
         * @param fallback Value to use for tool names in the denied set
         */
        fun denylist(denied: Set<String>, fallback: String): ToolNameCardinalityStrategy =
            ToolNameCardinalityStrategy { toolName ->
                if (toolName !in denied) toolName else fallback
            }

        /**
         * Creates a custom strategy using the provided transformation function.
         *
         * @param transform Function that transforms a tool name
         */
        fun custom(transform: (String) -> String): ToolNameCardinalityStrategy =
            ToolNameCardinalityStrategy(transform)
    }
}
