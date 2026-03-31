package ai.koog.agents.cli.claude

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAIAgentBuilderBase
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Base class for Claude agent builders.
 */
public abstract class ClaudeAgentBuilderBase<Input, Output, Self : ClaudeAgentBuilderBase<Input, Output, Self>> internal constructor(
    transport: CliTransport,
    binaryPath: String?,
    name: String?,
    systemPrompt: String?,
    llModel: LLModel?,
    workspace: String,
    timeout: Duration?,
    id: String?,
    clock: KoogClock,
    featureInstallers: MutableList<CliAIAgent.FeatureContext.() -> Unit>,
    protected var apiKey: String? = null,
    protected var permissionMode: ClaudePermissionMode? = null,
    protected var additionalFlags: List<String> = emptyList(),
) : CliAIAgentBuilderBase<Self>(
    transport,
    binaryPath,
    name,
    systemPrompt,
    llModel,
    workspace,
    timeout,
    id,
    clock,
    featureInstallers
) {
    /**
     * Sets the API key for Claude.
     *
     * @param apiKey The Anthropic API key.
     * @return This builder instance for chaining.
     */
    public fun apiKey(apiKey: String): Self = self().apply {
        this.apiKey = apiKey
    }

    /**
     * Sets the permission mode for Claude CLI.
     *
     * @param mode The permission mode to use during agent execution.
     * @return This builder instance for chaining.
     */
    public fun permissionMode(mode: ClaudePermissionMode): Self = self().apply {
        this.permissionMode = mode
    }

    /**
     * Sets additional command-line flags to pass to Claude CLI.
     *
     * @param flags List of additional flags.
     * @return This builder instance for chaining.
     */
    public fun additionalFlags(flags: List<String>): Self = self().apply {
        this.additionalFlags = flags
    }
}
