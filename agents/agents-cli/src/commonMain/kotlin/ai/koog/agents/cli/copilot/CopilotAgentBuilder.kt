package ai.koog.agents.cli.copilot

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Builder for Copilot CLI agent.
 */
public class CopilotAgentBuilder internal constructor(
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
    githubToken: String? = null,
    model: String? = null,
    additionalFlags: List<String> = emptyList(),
) : CopilotAgentBuilderBase<String, CopilotAgentBuilder>(
    transport,
    binaryPath,
    name,
    systemPrompt,
    llModel,
    workspace,
    timeout,
    id,
    clock,
    featureInstallers,
    githubToken,
    model,
    additionalFlags
) {
    override fun self(): CopilotAgentBuilder = this

    /**
     * Configures a custom request generator for the agent.
     *
     * @param Input The type of the input data.
     * @param generateRequest Function to generate the request string from the input.
     * @return Builder for Copilot CLI agent with custom input type.
     */
    public fun <Input> generateRequest(
        generateRequest: CliConfig.GenerateRequest<Input>
    ): CopilotAgentGenericInputBuilder<Input> = CopilotAgentGenericInputBuilder(
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
        githubToken = githubToken,
        model = model,
        additionalFlags = additionalFlags,
        generateRequest = generateRequest
    )

    /**
     * Builds the Copilot CLI agent.
     *
     * @return A configured [CliAIAgent] instance that accepts String input and produces [ai.koog.agents.cli.CliAIAgentResponse].
     */
    public fun build(): CliAIAgent<String, ai.koog.agents.cli.CliAIAgentResponse> {
        return CliAIAgent.copilot(
            transport = transport,
            githubToken = githubToken,
            binaryPath = binaryPath,
            model = model ?: llModel?.id,
            name = name,
            systemPrompt = systemPrompt,
            llModel = llModel,
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
 * Builder for Copilot CLI agent with custom input type.
 */
public class CopilotAgentGenericInputBuilder<Input> internal constructor(
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
    githubToken: String?,
    model: String?,
    additionalFlags: List<String>,
    internal val generateRequest: CliConfig.GenerateRequest<Input>,
) : CopilotAgentBuilderBase<Input, CopilotAgentGenericInputBuilder<Input>>(
    transport,
    binaryPath,
    name,
    systemPrompt,
    llModel,
    workspace,
    timeout,
    id,
    clock,
    featureInstallers,
    githubToken,
    model,
    additionalFlags
) {
    override fun self(): CopilotAgentGenericInputBuilder<Input> = this

    /**
     * Builds the Copilot CLI agent with custom input type.
     *
     * @return A configured [CliAIAgent] instance that accepts custom input and produces [ai.koog.agents.cli.CliAIAgentResponse].
     */
    public fun build(): CliAIAgent<Input, ai.koog.agents.cli.CliAIAgentResponse> {
        return CliAIAgent.copilot(
            transport = transport,
            githubToken = githubToken,
            binaryPath = binaryPath,
            model = model ?: llModel?.id,
            name = name,
            systemPrompt = systemPrompt,
            llModel = llModel,
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
