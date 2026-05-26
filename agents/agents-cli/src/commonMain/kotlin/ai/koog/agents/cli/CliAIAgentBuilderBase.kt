package ai.koog.agents.cli

import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Base class for CLI AI agent builders.
 */
public abstract class CliAIAgentBuilderBase<Self : CliAIAgentBuilderBase<Self>> internal constructor(
    protected val transport: CliTransport,
    protected var binaryPath: String?,
    protected var name: String?,
    protected var systemPrompt: String?,
    protected var llModel: LLModel?,
    protected var workspace: String,
    protected var timeout: Duration?,
    protected var id: String?,
    protected var clock: KoogClock,
    protected val featureInstallers: MutableList<CliAIAgent.FeatureContext.() -> Unit>,
) {
    internal constructor(transport: CliTransport) : this(transport, null, null, null, null, ".", null, null, KoogClock.System, mutableListOf())

    protected abstract fun self(): Self

    /**
     * Sets the CLI binary path.
     */
    public fun binaryPath(binaryPath: String): Self = self().apply {
        this.binaryPath = binaryPath
    }

    /**
     * Sets the agent name.
     */
    public fun name(name: String): Self = self().apply {
        this.name = name
    }

    /**
     * Adds the system prompt.
     */
    public fun systemPrompt(systemPrompt: String): Self = self().apply {
        this.systemPrompt = systemPrompt
    }

    /**
     * Sets the LLM model.
     */
    public fun llModel(llModel: LLModel): Self = self().apply {
        this.llModel = llModel
    }

    /**
     * Sets the workspace directory.
     */
    public fun workspace(workspace: String): Self = self().apply {
        this.workspace = workspace
    }

    /**
     * Sets the execution timeout.
     */
    public fun timeout(timeout: Duration): Self = self().apply {
        this.timeout = timeout
    }

    /**
     * Sets the execution timeout in minutes.
     */
    public fun timeoutMin(timeoutMin: Long): Self = timeout(timeoutMin.minutes)

    /**
     * Sets the agent ID.
     */
    public fun id(id: String?): Self = self().apply {
        this.id = id
    }

    /**
     * Sets the clock.
     */
    public fun clock(clock: KoogClock): Self = self().apply {
        this.clock = clock
    }

    /**
     * Installs a feature.
     */
    public fun install(installer: CliAIAgent.FeatureContext.() -> Unit): Self = self().apply {
        this.featureInstallers.add(installer)
    }
}
