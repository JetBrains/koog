package ai.koog.integration.tests.features

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.chathistory.aws.AgentcoreChatHistoryProvider
import ai.koog.integration.tests.utils.TestCredentials.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSessionTokenFromEnv
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration test for AgentCore Short-Term Memory (STM) using ChatMemory feature.
 *
 * Required environment variables:
 * - AWS_ACCESS_KEY_ID: AWS access key
 * - AWS_SECRET_ACCESS_KEY: AWS secret key
 * - AWS_AGENTCORE_MEMORY_ID: AgentCore memory identifier
 * - KOOG_HEAVY_TESTS: Set to "true" to enable this test
 *
 * Optional:
 * - AWS_SESSION_TOKEN: AWS session token (for temporary credentials)
 * - AWS_REGION: AWS region (defaults to us-east-1)
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_AGENTCORE_MEMORY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "KOOG_HEAVY_TESTS", matches = "true")
class AgentCoreSTMIntegrationTest {

    private lateinit var agentCoreClient: BedrockAgentCoreClient
    private lateinit var llmExecutor: MultiLLMPromptExecutor

    private val region = System.getenv("AWS_REGION") ?: "us-east-1"
    private val memoryId = System.getenv("AWS_AGENTCORE_MEMORY_ID")
        ?: error("Environment variable AWS_AGENTCORE_MEMORY_ID is not set")
    private val sessionId = "stm-test-${UUID.randomUUID()}"

    private val credentialsProvider = StaticCredentialsProvider {
        accessKeyId = readAwsAccessKeyIdFromEnv()
        secretAccessKey = readAwsSecretAccessKeyFromEnv()
        readAwsSessionTokenFromEnv()?.let { sessionToken = it }
    }

    @BeforeEach
    fun setup() {
        agentCoreClient = BedrockAgentCoreClient {
            this.region = this@AgentCoreSTMIntegrationTest.region
            this.credentialsProvider = this@AgentCoreSTMIntegrationTest.credentialsProvider
        }

        llmExecutor = MultiLLMPromptExecutor(
            BedrockLLMClient(identityProvider = credentialsProvider)
        )
    }

    @AfterEach
    fun teardown() {
        runBlocking { agentCoreClient.close() }
    }

    private fun createAgent(actorId: String): AIAgent<String, String> {
        val chatHistoryProvider = AgentcoreChatHistoryProvider(
            client = agentCoreClient,
            memoryId = memoryId,
            defaultSession = sessionId
        )

        return AIAgent(
            promptExecutor = llmExecutor,
            llmModel = BedrockModels.AmazonNovaMicro,
            toolRegistry = ToolRegistry.EMPTY,
            systemPrompt = "You are a helpful assistant. Remember information the user tells you."
        ) {
            install(ChatMemory) {
                this.chatHistoryProvider = chatHistoryProvider
            }
        }
    }

    @Test
    fun `agent remembers user name across conversation turns`() = runTest {
        val userName = "Alice"
        val actorId = "actor-${UUID.randomUUID()}"
        val conversationId = "$actorId:$sessionId"

        // Turn 1: Introduce name
        val agent1 = createAgent(actorId)
        val response1 = agent1.run("Hello! My name is $userName. Please remember it.", conversationId)
        println("Turn 1 response: $response1")

        // Turn 2: Ask for name (new agent instance, same session)
        val agent2 = createAgent(actorId)
        val response2 = agent2.run("What is my name?", conversationId)
        println("Turn 2 response: $response2")

        assertTrue(
            response2.contains(userName, ignoreCase = true),
            "Agent should remember the name '$userName'. Got: $response2"
        )
    }

    @Test
    fun `different actors do not share conversation history`() = runTest {
        val userName = "Bob"
        val actor1 = "actor1-${UUID.randomUUID()}"
        val actor2 = "actor2-${UUID.randomUUID()}"

        // Actor 1: Introduce name
        val agent1 = createAgent(actor1)
        val response1 = agent1.run("My name is $userName.", "$actor1:$sessionId")
        println("Actor 1 response: $response1")

        // Actor 2: Ask for name (different actor, should NOT know the name)
        val agent2 = createAgent(actor2)
        val response2 = agent2.run("What is my name?", "$actor2:$sessionId")
        println("Actor 2 response: $response2")

        assertFalse(
            response2.contains(userName, ignoreCase = true),
            "Actor 2 should NOT know Actor 1's name '$userName'. Got: $response2"
        )
    }

    @Test
    fun `different sessions of same actor do not share conversation history`() = runTest {
        val userName = "Charlie"
        val actorId = "actor-${UUID.randomUUID()}"
        val session1 = "session1-${UUID.randomUUID()}"
        val session2 = "session2-${UUID.randomUUID()}"

        // Session 1: Introduce name
        val agent1 = createAgent(actorId)
        val response1 = agent1.run("My name is $userName.", "$actorId:$session1")
        println("Session 1 response: $response1")

        // Session 2: Ask for name (different session, should NOT know the name)
        val agent2 = createAgent(actorId)
        val response2 = agent2.run("What is my name?", "$actorId:$session2")
        println("Session 2 response: $response2")

        assertFalse(
            response2.contains(userName, ignoreCase = true),
            "Session 2 should NOT know Session 1's name '$userName'. Got: $response2"
        )
    }
}
