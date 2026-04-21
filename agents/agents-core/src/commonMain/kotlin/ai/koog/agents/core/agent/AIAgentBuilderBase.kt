package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlin.time.Clock

/**
 * Shared fluent configuration for agent builders.
 */
public abstract class AIAgentBuilderBase<Self : AIAgentBuilderBase<Self>> internal constructor(
    promptExecutor: PromptExecutor?,
    toolRegistry: ToolRegistry,
    protected var id: String?,
    config: AIAgentConfig,
    protected var clock: Clock,
) : AIAgentServiceBuilderBase<Self>(
    promptExecutor = promptExecutor,
    toolRegistry = toolRegistry,
    config = config,
) {
    internal constructor(
        serializer: JSONSerializer = KotlinxSerializer(),
    ) : this(
        null,
        ToolRegistry.EMPTY,
        null,
        AIAgentConfig(
            prompt = Prompt.Empty,
            model = ModelNotSet,
            maxAgentIterations = 50,
            serializer = serializer
        ),
        Clock.System,
    )

    /**
     * Sets the identifier for the builder configuration.
     *
     * @param id The identifier string to be set. Can be null.
     * @return The current builder instance for chaining method calls.
     */
    public fun id(id: String?): Self = self().apply {
        this.id = id
    }

    /**
     * Sets the clock for the agent.
     */
    public fun clock(clock: Clock): Self = self().apply {
        this.clock = clock
    }
}
