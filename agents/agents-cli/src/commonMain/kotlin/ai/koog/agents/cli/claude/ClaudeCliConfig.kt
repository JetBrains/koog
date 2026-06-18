package ai.koog.agents.cli.claude

import ai.koog.agents.cli.CliAIAgentResponse
import ai.koog.agents.cli.CliAgentResponseMetaInfo
import ai.koog.agents.cli.CliAgentStructuredResponse
import ai.koog.agents.cli.CliConfig
import ai.koog.agents.cli.boolVal
import ai.koog.agents.cli.doubleVal
import ai.koog.agents.cli.intVal
import ai.koog.agents.cli.stringVal
import ai.koog.agents.cli.toJsonStdoutEvents
import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliException
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.Structure
import ai.koog.prompt.structure.json.generator.JsonSchemaConsts
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration

/**
 * Claude Code permission mode.
 */
public enum class ClaudePermissionMode(public val value: String) {
    /**
     * Default permission mode.
     */
    Default("default"),

    /**
     * Automatically accept all edits.
     */
    AcceptEdits("acceptEdits"),

    /**
     * Plan mode: only show planned actions without executing them.
     */
    Plan("plan"),

    /**
     * Auto mode: automatically accept all edits and plan actions.
     */
    Auto("auto"),

    /**
     * Do not ask for permissions.
     */
    DontAsk("dontAsk"),

    /**
     * Bypass all permission checks.
     */
    BypassPermissions("bypassPermissions"),
}

/**
 * Helper functions for Claude CLI agent
 */
public object ClaudeCliHelper {

    /**
     * Generates flags for Claude cli in autonomous mode.
     */
    public fun flags(
        model: LLModel,
        systemMessages: List<Message.System>,
        permissionMode: ClaudePermissionMode?,
        additionalFlags: List<String>
    ): List<String> =
        buildList {
            add("-p") // non-interactive mode to disallow the cli to ask for approval
            add("--output-format")
            add("stream-json")
            add("--verbose")

            if (model.provider == LLMProvider.Anthropic) {
                add("--model")
                add(model.id)
            }

            systemMessages.forEach { systemMessage ->
                add("--append-system-prompt")
                add(systemMessage.parts.joinToString("\n"))
            }

            permissionMode?.let {
                add("--permission-mode")
                add(it.value)
            }

            addAll(additionalFlags)
        }

    /**
     * Generates flags for Claude cli in structured mode.
     */
    public fun structuredFlags(
        model: LLModel,
        systemMessages: List<Message.System>,
        permissionMode: ClaudePermissionMode?,
        additionalFlags: List<String>,
        structure: Structure<*, LLMParams.Schema.JSON>
    ): List<String> =
        flags(model, systemMessages, permissionMode, additionalFlags) + listOf(
            "--json-schema",
            extractClaudeSchema(structure.schema)
        )

    /**
     * Generates environment with provided API key.
     */
    public fun env(apiKey: String?): Map<String, String> = buildMap {
        apiKey?.let { put("ANTHROPIC_API_KEY", it) }
    }

    /**
     * Extracts output from Claude CLI events.
     */
    public fun extractOutput(events: List<CliEvent>, logger: KLogger): CliAIAgentResponse {
        val failedEvent = events.filterIsInstance<CliEvent.Failed>().firstOrNull()
        if (failedEvent != null) {
            return CliAIAgentResponse(
                content = "Cli failed: ${failedEvent.message}",
                isError = true,
                metaInfo = CliAgentResponseMetaInfo()
            )
        }

        val jsonEvents = toJsonStdoutEvents(events, logger)

        val resultEvent = jsonEvents
            .lastOrNull { it["type"]?.stringVal == "result" }
            ?: throw CliException("No result event found")

        val content = resultEvent["result"]
            ?.stringVal
            ?: throw CliException("No result found in result event")

        val isError = resultEvent["is_error"]?.boolVal ?: false

        val usageObject = resultEvent["usage"]?.jsonObject

        val metaInfo = CliAgentResponseMetaInfo(
            inputTokensCount = usageObject?.get("input_tokens")?.intVal,
            outputTokensCount = usageObject?.get("output_tokens")?.intVal,
            buildJsonObject {
                put("cacheCreationInputTokens", usageObject?.get("cache_creation_input_tokens")?.intVal)
                put("cacheReadInputTokens", usageObject?.get("cache_read_input_tokens")?.intVal)
                put("totalCostUsd", resultEvent["total_cost_usd"]?.doubleVal)
            }
        )

        return CliAIAgentResponse(
            content = content,
            isError = isError,
            metaInfo = metaInfo
        )
    }

    /**
     * Extracts structured output from Claude CLI events.
     */
    public fun <T> extractStructuredOutput(
        events: List<CliEvent>,
        structure: Structure<T, *>,
        logger: KLogger
    ): CliAgentStructuredResponse<T> {
        val failedEvent = events.filterIsInstance<CliEvent.Failed>().firstOrNull()
        if (failedEvent != null) {
            return CliAgentStructuredResponse(
                structuredResult = null,
                response = CliAIAgentResponse(
                    content = "Cli failed: ${failedEvent.message}",
                    isError = true,
                    metaInfo = CliAgentResponseMetaInfo()
                )
            )
        }

        val response = extractOutput(events, logger)
        val jsonEvents = toJsonStdoutEvents(events, logger)
        val structuredResult = jsonEvents
            .lastOrNull { it["type"]?.stringVal == "result" }
            ?.get("structured_output")
            ?.toString()
            ?.let { structure.parse(it) }

        return CliAgentStructuredResponse(
            structuredResult = structuredResult,
            response = response.copy(isError = response.isError || structuredResult == null)
        )
    }

    /**
     * Extracts a JSON schema for Claude Code CLI from the provided [schema].
     */
    private fun extractClaudeSchema(schema: LLMParams.Schema.JSON): String {
        val jsonSchema = schema.schema

        val defs = requireNotNull(jsonSchema[JsonSchemaConsts.Keys.DEFS]) { "DEFS is required in the JSON schema." }

        val rootType = jsonSchema[JsonSchemaConsts.Keys.REF]
            ?.stringVal
            ?.removePrefix(JsonSchemaConsts.Keys.REF_PREFIX)
            ?.let { defs.jsonObject[it] }

        require(rootType is JsonObject) { "Claude Code CLI requires a JSON object as the root type." }

        val updatedSchema = rootType.toMutableMap()
        updatedSchema[JsonSchemaConsts.Keys.DEFS] = defs

        return JsonObject(updatedSchema).toString()
    }
}

/**
 * Configuration for Claude CLI agent with structured output.
 *
 * @param Input The type of input the agent accepts.
 * @param Output The type of structured output the agent produces.
 * @property transport The CLI transport used to execute commands.
 * @property apiKey The Anthropic API key, or null to use the ANTHROPIC_API_KEY environment variable.
 * @property binaryPath The path to the Claude CLI executable, or null to use the default ("claude").
 * @property name The name of the cli strategy, or null to use the default ("claude-code-structured").
 * @property structure The structure definition for parsing the output.
 * @property permissionMode The permission mode for Claude CLI execution.
 * @property additionalFlags Additional command-line flags to pass to Claude CLI.
 * @property workspace The working directory for command execution.
 * @property timeout The execution timeout duration.
 */
public class ClaudeCliStructuredConfig<Input, Output>(
    override val transport: CliTransport,
    public val apiKey: String? = null,
    binaryPath: String? = null,
    name: String? = null,
    public val structure: Structure<Output, LLMParams.Schema.JSON>,
    public val permissionMode: ClaudePermissionMode? = null,
    public val additionalFlags: List<String> = emptyList(),
    override val workspace: String = ".",
    override val timeout: Duration? = null,
    private val generateRequest: CliConfig.GenerateRequest<Input>
) : CliConfig<Input, CliAgentStructuredResponse<Output>> {
    override val binaryPath: String = binaryPath ?: "claude"
    override val name: String = name ?: "claude-code-structured"
    override val env: Map<String, String> = ClaudeCliHelper.env(apiKey)

    override fun flags(model: LLModel, systemMessages: List<Message.System>): List<String> =
        ClaudeCliHelper.structuredFlags(model, systemMessages, permissionMode, additionalFlags, structure)

    override fun generateRequest(input: Input): String =
        generateRequest.generateRequest(input)

    override fun extractOutput(events: List<CliEvent>, logger: KLogger): CliAgentStructuredResponse<Output> =
        ClaudeCliHelper.extractStructuredOutput(events, structure, logger)
}

/**
 * Configuration for Claude CLI agent.
 *
 * @param Input The type of input the agent accepts.
 * @property transport The CLI transport used to execute commands.
 * @property apiKey The Anthropic API key, or null to use the ANTHROPIC_API_KEY environment variable.
 * @property binaryPath The path to the Claude CLI executable, or null to use the default.
 * @property name The name of the cli strategy, or null to use the default.
 * @property permissionMode The permission mode for Claude CLI execution.
 * @property additionalFlags Additional command-line flags to pass to Claude CLI.
 * @property workspace The working directory for command execution.
 * @property timeout The execution timeout duration.
 */
public class ClaudeCliConfig<Input>(
    override val transport: CliTransport,
    public val apiKey: String? = null,
    binaryPath: String? = null,
    name: String? = null,
    public val permissionMode: ClaudePermissionMode? = null,
    public val additionalFlags: List<String> = emptyList(),
    override val workspace: String = ".",
    override val timeout: Duration? = null,
    private val generateRequest: CliConfig.GenerateRequest<Input>,
) : CliConfig<Input, CliAIAgentResponse> {
    override val binaryPath: String = binaryPath ?: "claude"
    override val name: String = name ?: "claude-code"
    override val env: Map<String, String> = ClaudeCliHelper.env(apiKey)

    override fun flags(model: LLModel, systemMessages: List<Message.System>): List<String> =
        ClaudeCliHelper.flags(model, systemMessages, permissionMode, additionalFlags)

    override fun generateRequest(input: Input): String =
        generateRequest.generateRequest(input)

    override fun extractOutput(events: List<CliEvent>, logger: KLogger): CliAIAgentResponse =
        ClaudeCliHelper.extractOutput(events, logger)
}
