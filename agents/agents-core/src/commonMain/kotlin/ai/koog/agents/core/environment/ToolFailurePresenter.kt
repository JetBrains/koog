package ai.koog.agents.core.environment

/**
 * Identifies the stage of a tool call at which an exception was caught and turned into a failure result
 * ([ToolResultKind.Failure], or [ToolResultKind.ValidationError] for argument parsing inside
 * [ContextualAgentEnvironment]).
 */
public enum class ToolFailureStage {
    /**
     * Raised while parsing or deserializing the raw tool-call arguments, before [ai.koog.agents.core.tools.ToolBase.execute]
     * is invoked.
     */
    ArgumentParsing,

    /**
     * Raised from within [ai.koog.agents.core.tools.ToolBase.execute], i.e. by the tool implementation itself.
     *
     * This is the stage most likely to carry arbitrary, externally influenced text (DB values, remote
     * responses, stack fragments) in [ToolFailure.error]'s message.
     */
    Execution,

    /**
     * Raised while encoding a successful tool result into the textual/structured representation sent back
     * to the model.
     */
    ResultSerialization,
}

/**
 * Describes a tool-call failure that is about to be surfaced to the LLM.
 *
 * Instances are passed to [ToolFailurePresenter.present] so a host application can decide what text the
 * model is allowed to see for this failure.
 *
 * @property toolName The name of the tool whose call failed.
 * @property stage The [ToolFailureStage] at which the failure was caught.
 * @property error The caught [Throwable].
 */
public class ToolFailure internal constructor(
    public val toolName: String,
    public val stage: ToolFailureStage,
    public val error: Throwable,
    private val defaultMessageOverride: String? = null,
) {
    /**
     * The default, framework-produced message for this failure.
     *
     * It embeds [error]'s raw message verbatim and is what the model sees when no custom
     * [ToolFailurePresenter] is configured. Custom presenters may return this to preserve the legacy
     * behavior for some failures while redacting others.
     *
     * Most failures derive this text from [stage]; a few call sites (e.g. argument parsing inside
     * [ContextualAgentEnvironment]) supply their own historical wording so that the [Default] presenter
     * stays byte-for-byte backward compatible.
     */
    public val defaultMessage: String
        get() = defaultMessageOverride ?: when (stage) {
            ToolFailureStage.ArgumentParsing ->
                "Tool with name '$toolName' failed to parse arguments due to the error: ${error.message}"

            ToolFailureStage.Execution ->
                "Tool with name '$toolName' failed to execute due to the error: ${error.message}!"

            ToolFailureStage.ResultSerialization ->
                "Tool with name '$toolName' failed to serialize result due to the error: ${error.message}!"
        }
}

/**
 * Produces the text that is fed back into the LLM context when a tool call fails with a generic
 * (non-[ai.koog.agents.core.tools.ToolException]) exception.
 *
 * By default the framework re-injects the raw [Throwable.message] into the prompt (see [Default]). That is
 * convenient but has two drawbacks an application may want to control:
 *
 * 1. **Silent degradation** — a thrown exception is converted into a failure result and the agent keeps
 *    going; the failure never propagates out of `agent.run()`. Failures are still observable through the
 *    event-handler feature — execution/serialization failures via `onToolCallFailed` and argument-parsing
 *    failures via `onToolValidationFailed` — both of which receive the original [Throwable], so a presenter
 *    is about *what the model sees*, not about losing visibility.
 * 2. **Prompt injection** — the exception message may contain unsanitized external input (DB values, remote
 *    responses, stack fragments). Returning it verbatim bypasses input sanitization an application performs
 *    on every other path into the prompt.
 *
 * Configure a custom presenter via [ai.koog.agents.core.agent.config.AIAgentConfig] to replace, redact, or
 * fix the text the model receives, e.g.:
 *
 * ```kotlin
 * val config = AIAgentConfig(
 *     prompt = prompt,
 *     model = model,
 *     maxAgentIterations = 10,
 *     toolFailurePresenter = ToolFailurePresenter { failure ->
 *         "The tool '${failure.toolName}' failed. Please try a different approach."
 *     },
 * )
 * ```
 *
 * Note: this hook does **not** affect [ai.koog.agents.core.tools.ToolException] failures. Those messages are
 * authored deliberately by the tool to guide the model and are not treated as untrusted input. The exemption
 * is specific to author-controlled [ai.koog.agents.core.tools.ToolException] text, not to the
 * [ToolResultKind.ValidationError] kind in general: a malformed-arguments failure surfaced as a
 * [ToolResultKind.ValidationError] by [ContextualAgentEnvironment] still carries a generic parser exception
 * and is routed through this presenter ([ToolFailureStage.ArgumentParsing]).
 *
 * Implementations must be thread-safe; [present] may be called concurrently for parallel tool calls.
 */
public fun interface ToolFailurePresenter {

    /**
     * Returns the message that will be re-injected into the LLM context for the given [failure].
     *
     * The returned string fully replaces the framework default; the raw [ToolFailure.error] message is not
     * appended unless the returned value includes it (e.g. via [ToolFailure.defaultMessage]).
     */
    public fun present(failure: ToolFailure): String

    /**
     * Provides the default [ToolFailurePresenter].
     */
    public companion object {
        /**
         * The backward-compatible presenter: returns [ToolFailure.defaultMessage], re-injecting the raw
         * exception message into the prompt exactly as earlier framework versions did.
         */
        public val Default: ToolFailurePresenter = ToolFailurePresenter { it.defaultMessage }
    }
}
