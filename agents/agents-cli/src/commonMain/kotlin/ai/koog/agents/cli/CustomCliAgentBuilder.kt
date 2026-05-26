package ai.koog.agents.cli

import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration

/**
 * Builder for custom CLI agent.
 */
public class CustomCliAgentBuilder<Input, Output> internal constructor(
    transport: CliTransport,
    binaryPath: String?,
    name: String?,
    systemPrompt: String?,
    llModel: LLModel?,
    workspace: String,
    timeout: Duration?,
    id: String?,
    clock: KoogClock,
    featureInstallers: MutableList<CliAIAgent.FeatureContext.() -> Unit>
) : CliAIAgentBuilderBase<CustomCliAgentBuilder<Input, Output>>(
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
    private var flags: (LLModel, List<Message.System>) -> List<String> = { _, _ -> emptyList() }
    private var generateRequest: CliConfig.GenerateRequest<Input>? = null
    private var extractOutput: CliConfig.ExtractOutput<Output>? = null
    private var env: Map<String, String> = emptyMap()

    override fun self(): CustomCliAgentBuilder<Input, Output> = this

    /**
     * Sets the function that generates command-line flags.
     */
    public fun flags(flags: (LLModel, List<Message.System>) -> List<String>): CustomCliAgentBuilder<Input, Output> = self().apply {
        this.flags = flags
    }

    /**
     * Sets the function that generates the request string.
     */
    public fun generateRequest(generateRequest: CliConfig.GenerateRequest<Input>): CustomCliAgentBuilder<Input, Output> = self().apply {
        this.generateRequest = generateRequest
    }

    /**
     * Sets the function that extracts the output from CLI event lines.
     */
    public fun extractOutput(extractOutput: CliConfig.ExtractOutput<Output>): CustomCliAgentBuilder<Input, Output> = self().apply {
        this.extractOutput = extractOutput
    }

    /**
     * Sets environment variables.
     */
    public fun env(env: Map<String, String>): CustomCliAgentBuilder<Input, Output> = self().apply {
        this.env = env
    }

    /**
     * Builds the custom CLI agent.
     */
    public fun build(): CliAIAgent<Input, Output> {
        val binaryPath = requireNotNull(binaryPath) { "Binary path is required" }
        val generateRequest = requireNotNull(this.generateRequest) { "Generate request is required" }
        val extractOutput = requireNotNull(this.extractOutput) { "Extract output is required" }

        val customConfig = object : CliConfig<Input, Output> {
            override val transport: CliTransport = this@CustomCliAgentBuilder.transport
            override val binaryPath: String = binaryPath
            override val name: String = this@CustomCliAgentBuilder.name ?: "custom-cli-agent"
            override val workspace: String = this@CustomCliAgentBuilder.workspace
            override val env: Map<String, String> = this@CustomCliAgentBuilder.env
            override val timeout: Duration? = this@CustomCliAgentBuilder.timeout

            override fun flags(model: LLModel, systemMessages: List<Message.System>): List<String> =
                this@CustomCliAgentBuilder.flags(model, systemMessages)

            override fun generateRequest(input: Input): String =
                generateRequest.generateRequest(input)

            override fun extractOutput(events: List<CliEvent>, logger: KLogger): Output =
                extractOutput.extractOutput(events, logger)
        }

        return CliAIAgent.withCliConfig(
            cliConfig = customConfig,
            systemPrompt = systemPrompt,
            llModel = llModel,
            id = id,
            clock = clock,
            installFeatures = {
                featureInstallers.forEach { it(this) }
            }
        )
    }
}
