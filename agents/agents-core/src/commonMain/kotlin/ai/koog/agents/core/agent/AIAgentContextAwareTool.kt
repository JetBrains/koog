package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.defaultJsonSchemaConfig
import ai.koog.serialization.TypeToken
import kotlinx.schema.generator.json.JsonSchemaConfig

/**
 * Base class for [Tool]s that need access to the current [AIAgentContext] during execution.
 *
 * Subclasses implement only [execute] with `(args, context)` — the parameter-only [Tool.execute]
 * is sealed off with a default that throws, since the framework always dispatches a context-aware
 * tool through the context-aware overload whenever an [AIAgentContext] is attached to the
 * environment (which is the case for any normal agent run).
 *
 * Use this when a tool legitimately needs:
 *  - the agent / run identifiers (e.g. for parent-child linkage of nested agents);
 *  - shared in-run state via [AIAgentContext.storage];
 *  - the agent's [AIAgentContext.llm] for nested LLM calls;
 *  - the [AIAgentContext.pipeline] for emitting custom feature events.
 *
 * Tools that only need their typed arguments to do their job should keep extending [Tool]
 * directly.
 *
 * Constructors mirror those of [Tool]; pick whichever fits.
 *
 * @param TArgs The type of the tool's arguments.
 * @param TResult The type of the tool's result.
 */
public abstract class AIAgentContextAwareTool<TArgs, TResult> : Tool<TArgs, TResult> {

    public constructor(
        argsType: TypeToken,
        resultType: TypeToken,
        descriptor: ToolDescriptor,
        metadata: Map<String, String> = emptyMap(),
    ) : super(argsType, resultType, descriptor, metadata)

    @OptIn(InternalAgentToolsApi::class)
    public constructor(
        argsType: TypeToken,
        resultType: TypeToken,
        name: String,
        description: String,
        jsonSchemaConfig: JsonSchemaConfig = defaultJsonSchemaConfig,
    ) : super(argsType, resultType, name, description, jsonSchemaConfig)

    /**
     * Default no-context fallback. The framework dispatches context-aware tools through
     * [execute] with `(args, context)` whenever an [AIAgentContext] is attached to the
     * environment — which is the case for any normal agent run. This method only fires when
     * the tool is invoked outside of an agent run (e.g. direct unit-test invocation against
     * a bare environment), and by default fails fast with a descriptive error.
     *
     * Most subclasses do not need to override this; do so only if you want a meaningful
     * non-context fallback for tests or out-of-band invocation.
     */
    override suspend fun execute(args: TArgs): TResult =
        error(
            "Tool '$name' is an AIAgentContextAwareTool and must be executed with an " +
                "AIAgentContext. This usually means it was invoked outside of an agent run, " +
                "or against an environment without an attached context."
        )

    /**
     * Executes the tool with both its typed [args] and the current [context].
     */
    public abstract suspend fun execute(args: TArgs, context: AIAgentContext): TResult
}
