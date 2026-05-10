package ai.koog.agents.ext.tool.cli

/**
 * Preset profiles for popular AI agent command-line tools.
 */
public object AIAgentCliProfiles {
    /**
     * Creates a profile for OpenAI Codex CLI non-interactive execution.
     *
     * The generated command shape is:
     * `codex exec --color never --ephemeral EXTRA_ARGUMENTS TASK`.
     *
     * @param executable Codex executable name or absolute path.
     * @param extraArguments Additional trusted arguments inserted before the task.
     * @param defaultTimeoutSeconds Default maximum process lifetime.
     * @return Codex CLI profile.
     */
    public fun codex(
        executable: String = "codex",
        extraArguments: List<String> = emptyList(),
        defaultTimeoutSeconds: Int = AIAgentCliProfile.DEFAULT_TIMEOUT_SECONDS,
    ): AIAgentCliProfile = AIAgentCliProfile(
        id = "codex",
        displayName = "Codex CLI",
        executable = executable,
        defaultTimeoutSeconds = defaultTimeoutSeconds,
        argumentBuilder = AIAgentCliArgumentBuilder { task, _ ->
            AIAgentCliInvocation(
                arguments = listOf("exec", "--color", "never", "--ephemeral") + extraArguments + task
            )
        }
    )

    /**
     * Creates a profile for Anthropic Claude Code CLI print mode.
     *
     * The generated command shape is:
     * `claude -p --output-format text EXTRA_ARGUMENTS TASK`.
     *
     * @param executable Claude executable name or absolute path.
     * @param extraArguments Additional trusted arguments inserted before the task.
     * @param defaultTimeoutSeconds Default maximum process lifetime.
     * @return Claude Code CLI profile.
     */
    public fun claudeCode(
        executable: String = "claude",
        extraArguments: List<String> = emptyList(),
        defaultTimeoutSeconds: Int = AIAgentCliProfile.DEFAULT_TIMEOUT_SECONDS,
    ): AIAgentCliProfile = AIAgentCliProfile(
        id = "claude-code",
        displayName = "Claude Code CLI",
        executable = executable,
        defaultTimeoutSeconds = defaultTimeoutSeconds,
        argumentBuilder = AIAgentCliArgumentBuilder { task, _ ->
            AIAgentCliInvocation(
                arguments = listOf("-p", "--output-format", "text") + extraArguments + task
            )
        }
    )

    /**
     * Creates a profile for GitHub Copilot CLI programmatic prompt mode.
     *
     * The generated command shape is:
     * `copilot -s -p TASK EXTRA_ARGUMENTS`.
     *
     * @param executable Copilot executable name or absolute path.
     * @param extraArguments Additional trusted arguments appended after the prompt.
     * @param defaultTimeoutSeconds Default maximum process lifetime.
     * @return GitHub Copilot CLI profile.
     */
    public fun githubCopilot(
        executable: String = "copilot",
        extraArguments: List<String> = emptyList(),
        defaultTimeoutSeconds: Int = AIAgentCliProfile.DEFAULT_TIMEOUT_SECONDS,
    ): AIAgentCliProfile = AIAgentCliProfile(
        id = "github-copilot",
        displayName = "GitHub Copilot CLI",
        executable = executable,
        defaultTimeoutSeconds = defaultTimeoutSeconds,
        argumentBuilder = AIAgentCliArgumentBuilder { task, _ ->
            AIAgentCliInvocation(
                arguments = listOf("-s", "-p", task) + extraArguments
            )
        }
    )

    /**
     * Creates a custom profile for any AI agent CLI.
     *
     * @param id Stable profile identifier.
     * @param displayName Human-readable CLI name.
     * @param executable Executable name or absolute path.
     * @param defaultTimeoutSeconds Default maximum process lifetime.
     * @param argumentBuilder Trusted command builder for this CLI.
     * @param outputExtractor Optional raw-output extractor.
     * @return Custom AI agent CLI profile.
     */
    public fun custom(
        id: String,
        displayName: String,
        executable: String,
        defaultTimeoutSeconds: Int = AIAgentCliProfile.DEFAULT_TIMEOUT_SECONDS,
        argumentBuilder: AIAgentCliArgumentBuilder,
        outputExtractor: AIAgentCliOutputExtractor = AIAgentCliOutputExtractor { it.output },
    ): AIAgentCliProfile = AIAgentCliProfile(
        id = id,
        displayName = displayName,
        executable = executable,
        defaultTimeoutSeconds = defaultTimeoutSeconds,
        argumentBuilder = argumentBuilder,
        outputExtractor = outputExtractor,
    )
}
