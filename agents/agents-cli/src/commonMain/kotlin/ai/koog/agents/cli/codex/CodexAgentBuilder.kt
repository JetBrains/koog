package ai.koog.agents.cli.codex

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAIAgentResponse
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Default builder for Codex CLI agent.
 */
public class CodexAgentBuilder internal constructor(
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
    apiKey: String? = null,
    sandbox: CodexSandboxMode? = null,
    askForApproval: CodexApprovalPolicy? = null,
    additionalFlags: List<String> = emptyList(),
) : CodexAgentBuilderBase<String, CodexAgentBuilder>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, sandbox, askForApproval, additionalFlags
) {
    override fun self(): CodexAgentBuilder = this

    /**
     * Configures a custom request generator for the agent.
     */
    public fun <Input> generateRequest(
        generateRequest: CliConfig.GenerateRequest<Input>
    ): CodexAgentGenericInputBuilder<Input> = CodexAgentGenericInputBuilder(
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
        apiKey = apiKey,
        sandbox = sandbox,
        askForApproval = askForApproval,
        additionalFlags = additionalFlags,
        generateRequest = generateRequest
    )

    /**
     * Builds the Codex CLI agent.
     *
     * @return A configured [CliAIAgent] instance that accepts String input and produces [CliAIAgentResponse].
     */
    public fun build(): CliAIAgent<String, CliAIAgentResponse> {
        return CliAIAgent.codex(
            transport = transport,
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            llModel = llModel,
            sandbox = sandbox,
            askForApproval = askForApproval,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            installFeatures = { featureInstallers.forEach { it(this) } }
        )
    }
}

/**
 * Generic builder for Codex CLI agent with custom input type.
 */
public class CodexAgentGenericInputBuilder<Input> internal constructor(
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
    sandbox: CodexSandboxMode?,
    askForApproval: CodexApprovalPolicy?,
    additionalFlags: List<String>,
    internal val generateRequest: CliConfig.GenerateRequest<Input>,
) : CodexAgentBuilderBase<Input, CodexAgentGenericInputBuilder<Input>>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, sandbox, askForApproval, additionalFlags
) {
    override fun self(): CodexAgentGenericInputBuilder<Input> = this

    /**
     * Builds the Codex CLI agent with custom input type.
     *
     * @return A configured [CliAIAgent] instance that accepts custom input and produces [CliAIAgentResponse].
     */
    public fun build(): CliAIAgent<Input, CliAIAgentResponse> {
        return CliAIAgent.codex(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            systemPrompt = systemPrompt,
            llModel = llModel,
            sandbox = sandbox,
            askForApproval = askForApproval,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            generateRequest = generateRequest,
            installFeatures = { featureInstallers.forEach { it(this) } }
        )
    }
}
