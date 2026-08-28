package ai.koog.agents.cli.copilot

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAIAgentBuilderBase
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Base class for Copilot agent builders.
 */
public abstract class CopilotAgentBuilderBase<Input, Self : CopilotAgentBuilderBase<Input, Self>> internal constructor(
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
    protected var githubToken: String? = null,
    protected var model: String? = null,
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
     * Sets the GitHub token for Copilot CLI authentication.
     *
     * @param githubToken The token used by Copilot CLI (`COPILOT_GITHUB_TOKEN`).
     * @return This builder instance for chaining.
     */
    public fun githubToken(githubToken: String): Self = self().apply {
        this.githubToken = githubToken
    }

    /**
     * Sets the model used by Copilot CLI.
     *
     * @param model The model name to pass to Copilot CLI.
     * @return This builder instance for chaining.
     */
    public fun model(model: String): Self = self().apply {
        this.model = model
    }

    /**
     * Sets additional command-line flags to pass to Copilot CLI.
     *
     * @param flags List of additional flags.
     * @return This builder instance for chaining.
     */
    public fun additionalFlags(flags: List<String>): Self = self().apply {
        this.additionalFlags = flags
    }
}

