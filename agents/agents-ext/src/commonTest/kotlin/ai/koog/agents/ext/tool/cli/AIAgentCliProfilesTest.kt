package ai.koog.agents.ext.tool.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AIAgentCliProfilesTest {
    @Test
    fun testCodexProfileBuildsExecCommand() {
        val profile = AIAgentCliProfiles.codex(
            extraArguments = listOf("--full-auto"),
            defaultTimeoutSeconds = 42
        )

        val request = profile.buildRequest("fix failing tests", AIAgentCliTool.Args(task = "fix failing tests"))

        assertEquals("codex", profile.id)
        assertEquals(42, request.timeoutSeconds)
        assertEquals(
            listOf("codex", "exec", "--color", "never", "--ephemeral", "--full-auto", "fix failing tests"),
            request.command
        )
    }

    @Test
    fun testClaudeCodeProfileBuildsPrintCommand() {
        val profile = AIAgentCliProfiles.claudeCode(extraArguments = listOf("--model", "sonnet"))

        val request = profile.buildRequest("summarize the module", AIAgentCliTool.Args(task = "summarize the module"))

        assertEquals("claude-code", profile.id)
        assertEquals(
            listOf("claude", "-p", "--output-format", "text", "--model", "sonnet", "summarize the module"),
            request.command
        )
    }

    @Test
    fun testGithubCopilotProfileBuildsProgrammaticPromptCommand() {
        val profile = AIAgentCliProfiles.githubCopilot(extraArguments = listOf("--allow-all-tools"))

        val request = profile.buildRequest("write unit tests", AIAgentCliTool.Args(task = "write unit tests"))

        assertEquals("github-copilot", profile.id)
        assertEquals(
            listOf("copilot", "-s", "-p", "write unit tests", "--allow-all-tools"),
            request.command
        )
    }

    @Test
    fun testCustomProfileSupportsStdinAndEnvironment() {
        val profile = AIAgentCliProfiles.custom(
            id = "custom-cli",
            displayName = "Custom CLI",
            executable = "agent",
            argumentBuilder = AIAgentCliArgumentBuilder { task, _ ->
                AIAgentCliInvocation(
                    arguments = listOf("run", "--stdin"),
                    stdin = task,
                    environment = mapOf("AGENT_MODE" to "test")
                )
            }
        )

        val request = profile.buildRequest("inspect code", AIAgentCliTool.Args(task = "inspect code"))

        assertEquals(listOf("agent", "run", "--stdin"), request.command)
        assertEquals("inspect code", request.stdin)
        assertEquals(mapOf("AGENT_MODE" to "test"), request.environment)
    }

    @Test
    fun testProfileRejectsInvalidConfiguration() {
        assertFailsWith<IllegalArgumentException> {
            AIAgentCliProfiles.custom(
                id = "",
                displayName = "Custom CLI",
                executable = "agent",
                argumentBuilder = AIAgentCliArgumentBuilder { _, _ -> AIAgentCliInvocation(emptyList()) }
            )
        }

        assertFailsWith<IllegalArgumentException> {
            AIAgentCliProfiles.codex(defaultTimeoutSeconds = 0)
        }
    }
}
