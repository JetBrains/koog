package ai.koog.agents.cli.copilot

import ai.koog.agents.cli.CliAIAgentResponse
import ai.koog.agents.cli.CliAgentResponseMetaInfo
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliException
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration

/**
 * Configuration for GitHub Copilot CLI agent.
 *
 * @param Input The type of input the agent accepts.
 * @property transport The CLI transport used to execute commands.
 * @property githubToken The GitHub token used for Copilot CLI authentication.
 * @property binaryPath The path to the Copilot CLI executable, or null to use the default ("copilot").
 * @property name The name of the cli strategy, or null to use the default.
 * @property model The model passed to Copilot CLI.
 * @property additionalFlags Additional command-line flags to pass to Copilot CLI.
 * @property workspace The working directory for command execution.
 * @property timeout The execution timeout duration.
 */
public class CopilotCliConfig<Input>(
    override val transport: CliTransport,
    public val githubToken: String? = null,
    binaryPath: String? = null,
    name: String? = null,
    public val model: String? = null,
    public val additionalFlags: List<String> = emptyList(),
    override val workspace: String = ".",
    override val timeout: Duration? = null,
    private val generateRequest: CliConfig.GenerateRequest<Input>,
) : CliConfig<Input, CliAIAgentResponse> {
    override val binaryPath: String = binaryPath ?: "copilot"
    override val name: String = name ?: "copilot"
    override val env: Map<String, String> = buildMap {
        githubToken?.let { put("COPILOT_GITHUB_TOKEN", it) }
    }

    override fun flags(model: LLModel, systemMessages: List<Message.System>): List<String> =
        buildList {
            add("-p")
            add("-s")
            this@CopilotCliConfig.model?.let {
                add("--model")
                add(it)
            }
            addAll(additionalFlags)
        }

    override fun generateRequest(input: Input): String =
        generateRequest.generateRequest(input)

    override fun extractOutput(events: List<CliEvent>, logger: KLogger): CliAIAgentResponse {
        val failedEvent = events.filterIsInstance<CliEvent.Failed>().firstOrNull()
        if (failedEvent != null) {
            return CliAIAgentResponse(
                content = "Cli failed: ${failedEvent.message}",
                isError = true,
                metaInfo = CliAgentResponseMetaInfo()
            )
        }

        val output = events
            .filterIsInstance<CliEvent.Line>()
            .joinToString(separator = "\n") { it.content }
            .trim()

        if (output.isBlank()) {
            throw CliException("No response content returned from Copilot CLI")
        }

        return CliAIAgentResponse(
            content = output,
            isError = false,
            metaInfo = CliAgentResponseMetaInfo()
        )
    }
}

