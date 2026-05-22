package ai.koog.agents.cli

import ai.koog.agents.cli.claude.ClaudeAgentBuilder
import ai.koog.agents.cli.codex.CodexAgentBuilder
import ai.koog.agents.cli.transport.CliTransport

/**
 * Builder for CLI AI agents.
 */
public class CliAgentBuilder internal constructor(transport: CliTransport) : CliAIAgentBuilderBase<CliAgentBuilder>(
    transport
) {
    override fun self(): CliAgentBuilder = this

    /**
     * Configures the agent to use Claude CLI.
     */
    public fun claude(): ClaudeAgentBuilder = ClaudeAgentBuilder(
        transport = transport,
        binaryPath = binaryPath,
        name = name,
        systemPrompt = systemPrompt,
        llModel = llModel,
        workspace = workspace,
        timeout = timeout,
        id = id,
        clock = clock,
        featureInstallers = featureInstallers
    )

    /**
     * Configures the agent to use Codex CLI.
     */
    public fun codex(): CodexAgentBuilder = CodexAgentBuilder(
        transport = transport,
        binaryPath = binaryPath,
        name = name,
        systemPrompt = systemPrompt,
        llModel = llModel,
        workspace = workspace,
        timeout = timeout,
        id = id,
        clock = clock,
        featureInstallers = featureInstallers
    )

    /**
     * Configures the agent with a custom CLI configuration.
     */
    public fun <Input, Output> custom(): CustomCliAgentBuilder<Input, Output> = CustomCliAgentBuilder(
        transport = transport,
        binaryPath = binaryPath,
        name = name,
        systemPrompt = systemPrompt,
        llModel = llModel,
        workspace = workspace,
        timeout = timeout,
        id = id,
        clock = clock,
        featureInstallers = featureInstallers,
    )
}
