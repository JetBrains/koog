package ai.koog.integration.tests.agent

import ai.koog.agents.cli.CliAIAgent
import ai.koog.agents.cli.CliAIAgentResponse
import ai.koog.agents.cli.asNode
import ai.koog.agents.cli.codex.CodexSandboxMode
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.agents.cli.transport.DockerCliTransport
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.integration.tests.utils.TestCredentials.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CliAIAgentIntegrationTest : AIAgentTestBase() {
    companion object {
        private const val IMAGE_NAME = "cli-agents"
        private val dockerTransport = DockerCliTransport(IMAGE_NAME)

        private suspend fun testAgent(agent: AIAgent<String, CliAIAgentResponse>) {
            assertResponse(agent.run("echo 'hi'"))
        }

        private fun assertResponse(response: CliAIAgentResponse) {
            assertFalse(response.isError, "Run should be successful")
            assertContains(response.content, "hi", ignoreCase = true, "Response should contain 'hi'")

            val metaInfo = response.metaInfo

            assertNotNull(metaInfo.inputTokensCount, "Usage should contain input tokens")
            assertNotNull(metaInfo.outputTokensCount, "Usage should contain output tokens")
        }
    }

    @Serializable
    data class TestInput(val request: String)

    @Serializable
    data class StructuredResult(val message: String)

    private val cliSystemPrompt = "please follow the instructions of the user without asking for confirmations. do not call any tools"

    private val timeout = 180.seconds

    @Test
    fun integration_testCodex() = runTest(timeout = timeout) {
        val agent = CliAIAgent.codex(
            systemPrompt = cliSystemPrompt,
            apiKey = readTestOpenAIKeyFromEnv(),
            transport = CliTransport.default()
        )

        testAgent(agent)
    }

    @Test
    @ExtendWith(DockerAvailableCondition::class)
    fun integration_testCodexDocker() = runTest(timeout = timeout) {
        val agent = CliAIAgent.codex(
            systemPrompt = cliSystemPrompt,
            apiKey = readTestOpenAIKeyFromEnv(),
            transport = dockerTransport
        )

        testAgent(agent)
    }

    // it could fail locally if you are logged in to codex
    @Test
    fun integration_testCodexNoKey() = runTest(timeout = timeout) {
        val agent = CliAIAgent.codex(transport = CliTransport.default())

        assertTrue(agent.run("Hi!").isError, "Response should be an error")
    }

    @Test
    fun integration_testClaude() = runTest(timeout = timeout) {
        val agent = CliAIAgent.claude(
            systemPrompt = cliSystemPrompt,
            apiKey = readTestAnthropicKeyFromEnv(),
            transport = CliTransport.default()
        )

        testAgent(agent)
    }

    @Test
    @ExtendWith(DockerAvailableCondition::class)
    fun integration_testClaudeDocker() = runTest(timeout = timeout) {
        val agent = CliAIAgent.claude(
            systemPrompt = cliSystemPrompt,
            apiKey = readTestAnthropicKeyFromEnv(),
            transport = dockerTransport
        )

        testAgent(agent)
    }

    // it could fail locally if you are logged in to claude
    @Test
    fun integration_testClaudeNoKey() = runTest(timeout = timeout) {
        val agent = CliAIAgent.claude(
            transport = CliTransport.default(),
            apiKey = "invalid-key"
        )

        assertTrue(agent.run("Hi!").isError, "Response should be an error")
    }

    @Test
    @Retry // this test is flaky
    @DisabledOnOs(
        OS.WINDOWS,
        disabledReason = "Structured output fails on Windows. Probably due to the json schema broken by cmd. KG-779"
    )
    fun integration_testClaudeStructuredOutput() = runTest(timeout = timeout) {
        val agent = CliAIAgent.claude<String, StructuredResult>(
            transport = CliTransport.default(),
            apiKey = readTestAnthropicKeyFromEnv(),
            systemPrompt = cliSystemPrompt,
        )

        val result = agent.run("echo 'hi'")
        assertResponse(result.response)
        assertNotNull(result.structuredResult)
    }

    @Test
    fun integration_testClaudeCustomInput() = runTest(timeout = timeout) {
        val agent = CliAIAgent.claude<TestInput>(
            transport = CliTransport.default(),
            apiKey = readTestAnthropicKeyFromEnv(),
            systemPrompt = cliSystemPrompt,
            generateRequest = { it.request }
        )

        val response = agent.run(TestInput("echo 'hi'"))
        assertResponse(response)
    }

    @Test
    fun integration_testCodexCustomInput() = runTest(timeout = timeout) {
        val agent = CliAIAgent.codex<TestInput>(
            transport = CliTransport.default(),
            apiKey = readTestOpenAIKeyFromEnv(),
            systemPrompt = cliSystemPrompt,
            generateRequest = { it.request }
        )

        val response = agent.run(TestInput("echo 'hi'"))
        assertResponse(response)
    }

    @Test
    @Retry
    fun integration_testCliAgentInGraphs() = runTest(timeout = timeout) {
        val claudeApiKey = readTestAnthropicKeyFromEnv()
        val codexApiKey = readTestOpenAIKeyFromEnv()

        val claude = CliAIAgent.claude(
            transport = CliTransport.default(),
            apiKey = claudeApiKey,
            systemPrompt = cliSystemPrompt,
        )

        val codex = CliAIAgent.codex<CliAIAgentResponse>(
            transport = CliTransport.default(),
            apiKey = codexApiKey,
            systemPrompt = cliSystemPrompt,
            sandbox = CodexSandboxMode.WorkspaceWrite,
            generateRequest = { "echo '${it.content}'" }
        )

        val strategy = strategy("test-strategy") {
            val generatePlan by claude.asNode()
            val solveTask by codex.asNode()

            nodeStart then generatePlan then solveTask then nodeFinish
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            agentConfig = AIAgentConfig(
                prompt = Prompt.Empty,
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10,
            ),
            strategy = strategy,
        )

        assertResponse(agent.run("echo 'hi'"))
    }
}
