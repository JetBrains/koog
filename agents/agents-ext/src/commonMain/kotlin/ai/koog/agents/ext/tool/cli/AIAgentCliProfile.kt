package ai.koog.agents.ext.tool.cli

/**
 * Builds an executable invocation for an external AI agent CLI.
 */
public fun interface AIAgentCliArgumentBuilder {
    /**
     * Creates process arguments and stdin for [task].
     *
     * @param task Rendered task text to delegate.
     * @param args Original tool arguments supplied by the agent.
     * @return Process invocation details, excluding executable and timeout.
     */
    public fun build(task: String, args: AIAgentCliTool.Args): AIAgentCliInvocation
}

/**
 * Process invocation details produced by [AIAgentCliArgumentBuilder].
 *
 * @property arguments Command arguments in order.
 * @property stdin Optional text to write to standard input.
 * @property workingDirectory Optional working directory override.
 * @property environment Additional environment variables.
 */
public data class AIAgentCliInvocation(
    val arguments: List<String>,
    val stdin: String? = null,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
)

/**
 * Extracts the assistant-facing text from a CLI execution result.
 */
public fun interface AIAgentCliOutputExtractor {
    /**
     * Converts raw process output to the tool result output.
     *
     * @param result Raw CLI process result.
     * @return Output exposed to the Koog agent.
     */
    public fun extract(result: AIAgentCliExecutionResult): String
}

/**
 * Describes how Koog delegates a task to an external AI agent CLI.
 *
 * A profile owns the command shape for one CLI family. It keeps command construction in trusted application code,
 * while tool calls only provide the task text, working directory, and timeout.
 *
 * @property id Stable profile identifier.
 * @property displayName Human-readable CLI name.
 * @property executable Executable name or absolute path.
 * @property defaultTimeoutSeconds Default maximum process lifetime.
 * @property argumentBuilder Builds process arguments and stdin for each task.
 * @property outputExtractor Extracts assistant-facing output from the process result.
 */
public data class AIAgentCliProfile(
    val id: String,
    val displayName: String,
    val executable: String,
    val defaultTimeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val argumentBuilder: AIAgentCliArgumentBuilder,
    val outputExtractor: AIAgentCliOutputExtractor = AIAgentCliOutputExtractor { it.output },
) {
    init {
        require(id.isNotBlank()) { "Profile id must not be blank" }
        require(displayName.isNotBlank()) { "Profile displayName must not be blank" }
        require(executable.isNotBlank()) { "Profile executable must not be blank" }
        require(defaultTimeoutSeconds > 0) { "Profile defaultTimeoutSeconds must be positive" }
    }

    /**
     * Builds a full execution request for [args].
     *
     * @param task Rendered task text.
     * @param args Tool arguments supplied by the agent.
     * @return Full process execution request.
     */
    public fun buildRequest(task: String, args: AIAgentCliTool.Args): AIAgentCliExecutionRequest {
        val invocation = argumentBuilder.build(task, args)
        val timeoutSeconds = args.timeoutSeconds ?: defaultTimeoutSeconds
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }

        return AIAgentCliExecutionRequest(
            profileId = id,
            executable = executable,
            arguments = invocation.arguments,
            stdin = invocation.stdin,
            workingDirectory = args.workingDirectory ?: invocation.workingDirectory,
            environment = invocation.environment,
            timeoutSeconds = timeoutSeconds,
        )
    }

    public companion object {
        /**
         * Default timeout for delegated CLI tasks.
         */
        public const val DEFAULT_TIMEOUT_SECONDS: Int = 900
    }
}
