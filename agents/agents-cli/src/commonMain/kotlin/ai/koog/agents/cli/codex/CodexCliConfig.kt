package ai.koog.agents.cli.codex

import ai.koog.agents.cli.CliAIAgentResponse
import ai.koog.agents.cli.CliAgentResponseMetaInfo
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.intVal
import ai.koog.agents.cli.stringVal
import ai.koog.agents.cli.toJsonStdoutEvents
import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration

/**
 * Codex sandbox mode.
 */
public enum class CodexSandboxMode(public val value: String) {
    /**
     * Read-only access to the filesystem.
     */
    ReadOnly("read-only"),

    /**
     * Write access to the workspace directory.
     */
    WorkspaceWrite("workspace-write"),

    /**
     * Full access to the system.
     */
    DangerFullAccess("danger-full-access")
}

/**
 * Codex approval policy.
 */
public enum class CodexApprovalPolicy(public val value: String) {
    /**
     * Ask for approval for all untrusted commands.
     */
    Untrusted("untrusted"),

    /**
     * Ask for approval only if a command fails.
     */
    OnFailure("on-failure"),

    /**
     * The model decides when to ask for approval.
     */
    OnRequest("on-request"),

    /**
     * Never ask for approval.
     */
    Never("never")
}

/**
 * Configuration for Codex CLI agent.
 *
 * @param Input The type of input the agent accepts.
 * @property transport The CLI transport used to execute commands.
 * @property apiKey The Codex API key, or null to use the CODEX_API_KEY environment variable.
 * @property sandbox The sandbox mode for Codex CLI execution.
 * @property askForApproval The approval policy for command execution.
 * @property additionalFlags Additional command-line flags to pass to Codex CLI.
 * @property workspace The working directory for command execution.
 * @property timeout The execution timeout duration.
 */
public class CodexCliConfig<Input>(
    override val transport: CliTransport,
    public val apiKey: String? = null,
    binaryPath: String? = null,
    name: String? = null,
    public val sandbox: CodexSandboxMode? = null,
    public val askForApproval: CodexApprovalPolicy? = null,
    public val additionalFlags: List<String> = emptyList(),
    override val workspace: String = ".",
    override val timeout: Duration? = null,
    private val generateRequest: CliConfig.GenerateRequest<Input>
) : CliConfig<Input, CliAIAgentResponse> {
    override val binaryPath: String = binaryPath ?: "codex"
    override val name: String = name ?: "codex"

    override val env: Map<String, String> = buildMap {
        apiKey?.let { put("CODEX_API_KEY", it) }
    }

    override fun flags(model: LLModel, systemMessages: List<Message.System>): List<String> =
        buildList {
            add("exec") // non-interactive mode to disallow the cli to ask for approval
            add("--json")
            add("--skip-git-repo-check")

            if (model.provider == LLMProvider.OpenAI) {
                add("--model")
                add(model.id)
            }

            // note: codex does not support system messages, so we are not using them here

            sandbox?.let {
                add("--sandbox")
                add(it.value)
            }

            askForApproval?.let {
                add("--ask-for-approval")
                add(it.value)
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

        val jsonEvents = toJsonStdoutEvents(events, logger)

        val errorEvent = jsonEvents.lastOrNull { it["type"]?.stringVal == "turn.failed" }
        val resultIsError = errorEvent != null

        val content = if (resultIsError) {
            errorEvent["error"]?.jsonObject?.get("message")?.stringVal
        } else {
            // we're interested in the last event of type {"type": "item.completed", "item": {"id": "item_123", "text": "some text"}}"...}
            jsonEvents
                .filter { it["type"]?.stringVal == "item.completed" }
                .mapNotNull { it["item"] as? JsonObject }
                .maxByOrNull { it["id"]?.stringVal?.substringAfterLast("_")?.toIntOrNull() ?: -1 }
                ?.get("text")?.stringVal
        }

        val usageObject = jsonEvents
            .lastOrNull { it["type"]?.stringVal == "turn.completed" }
            ?.get("usage")
            ?.jsonObject

        val metaInfo = CliAgentResponseMetaInfo(
            inputTokensCount = usageObject?.get("input_tokens")?.intVal,
            outputTokensCount = usageObject?.get("output_tokens")?.intVal,
            metadata = buildJsonObject {
                put("cachedInputTokens", usageObject?.get("cached_input_tokens")?.intVal)
            }
        )

        return CliAIAgentResponse(
            content = content ?: "Failed to extract message content",
            isError = resultIsError || content == null,
            metaInfo = metaInfo,
        )
    }
}
