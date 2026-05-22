package ai.koog.agents.cli.claude

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Default builder for Claude CLI agent.
 */
public actual class ClaudeAgentBuilder internal actual constructor(
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
    apiKey: String?,
    permissionMode: ClaudePermissionMode?,
    additionalFlags: List<String>,
) : ClaudeAgentBuilderCommon<ClaudeAgentBuilder>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, permissionMode, additionalFlags
) {
    public actual override fun self(): ClaudeAgentBuilder = this
}

/**
 * Builder for Claude CLI agent with custom input type.
 */
public actual class ClaudeAgentGenericInputBuilder<Input> internal actual constructor(
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
    apiKey: String?,
    permissionMode: ClaudePermissionMode?,
    additionalFlags: List<String>,
    generateRequest: CliConfig.GenerateRequest<Input>,
) : ClaudeAgentGenericInputBuilderCommon<Input, ClaudeAgentGenericInputBuilder<Input>>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, permissionMode, additionalFlags, generateRequest
) {
    public actual override fun self(): ClaudeAgentGenericInputBuilder<Input> = this
}
