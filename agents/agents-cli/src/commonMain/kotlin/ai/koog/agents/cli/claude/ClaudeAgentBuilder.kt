package ai.koog.agents.cli.claude

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAgentStructuredResponse
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.Structure
import ai.koog.utils.time.KoogClock
import kotlin.time.Duration

/**
 * Default builder for Claude CLI agent.
 */
public expect class ClaudeAgentBuilder internal constructor(
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
    permissionMode: ClaudePermissionMode? = null,
    additionalFlags: List<String> = emptyList(),
) : ClaudeAgentBuilderCommon<ClaudeAgentBuilder> {
    override fun self(): ClaudeAgentBuilder
}

/**
 * Builder for Claude CLI agent with custom input type.
 */
public expect class ClaudeAgentGenericInputBuilder<Input> internal constructor(
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
) : ClaudeAgentGenericInputBuilderCommon<Input, ClaudeAgentGenericInputBuilder<Input>> {
    override fun self(): ClaudeAgentGenericInputBuilder<Input>
}

/**
 * Builder for Claude CLI agent with structured output.
 */
public class ClaudeAgentStructuredOutputBuilder<Output> internal constructor(
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
    internal val structure: Structure<Output, LLMParams.Schema.JSON>,
) : ClaudeAgentBuilderBase<String, CliAgentStructuredResponse<Output>, ClaudeAgentStructuredOutputBuilder<Output>>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, permissionMode, additionalFlags
) {
    override fun self(): ClaudeAgentStructuredOutputBuilder<Output> = this

    /**
     * Configures a custom request generator for the agent.
     *
     * @param Input The type of the input data.
     * @param generateRequest Function to generate the request string from the input.
     * @return Builder for Claude CLI agent with custom input type and structured output.
     */
    public fun <Input> generateRequest(
        generateRequest: CliConfig.GenerateRequest<Input>
    ): ClaudeAgentGenericInputStructuredOutputBuilder<Input, Output> = ClaudeAgentGenericInputStructuredOutputBuilder(
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
        permissionMode = permissionMode,
        additionalFlags = additionalFlags,
        generateRequest = generateRequest,
        structure = structure
    )

    /**
     * Builds the Claude CLI agent with structured output.
     *
     * @return A configured [CliAIAgent] instance that accepts String input and produces structured output.
     */
    public fun build(): CliAIAgent<String, CliAgentStructuredResponse<Output>> {
        return CliAIAgent.claude(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            structure = structure,
            systemPrompt = systemPrompt,
            llModel = llModel,
            permissionMode = permissionMode,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            generateRequest = { it },
            installFeatures = { featureInstallers.forEach { it(this) } }
        )
    }
}

/**
 * Builder for Claude CLI agent with custom input type and structured output.
 */
public class ClaudeAgentGenericInputStructuredOutputBuilder<Input, Output> internal constructor(
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
    internal val generateRequest: CliConfig.GenerateRequest<Input>,
    internal val structure: Structure<Output, LLMParams.Schema.JSON>,
) : ClaudeAgentBuilderBase<Input, CliAgentStructuredResponse<Output>, ClaudeAgentGenericInputStructuredOutputBuilder<Input, Output>>(
    transport, binaryPath, name, systemPrompt, llModel, workspace, timeout, id, clock, featureInstallers, apiKey, permissionMode, additionalFlags
) {
    override fun self(): ClaudeAgentGenericInputStructuredOutputBuilder<Input, Output> = this

    /**
     * Builds the Claude CLI agent with custom input type and structured output.
     *
     * @return A configured [CliAIAgent] instance that accepts custom input and produces structured output.
     */
    public fun build(): CliAIAgent<Input, CliAgentStructuredResponse<Output>> {
        return CliAIAgent.claude(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            structure = structure,
            systemPrompt = systemPrompt,
            llModel = llModel,
            permissionMode = permissionMode,
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
