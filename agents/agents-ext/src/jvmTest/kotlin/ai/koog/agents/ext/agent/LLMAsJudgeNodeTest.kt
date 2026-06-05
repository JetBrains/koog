package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import ai.koog.serialization.typeToken
import ai.koog.utils.time.KoogClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class LLMAsJudgeNodeTest {
    private val testClock: KoogClock = KoogClock { Instant.parse("2023-01-01T00:00:00Z") }

    companion object {
        const val CRITIC_TASK = "Find all numbers produced by LLM and check that they are not divided by 3"
    }

    @OptIn(InternalAgentsApi::class, DetachedPromptExecutorAPI::class)
    @Ignore("Doesn't work properly with mockk and @JvmSynethic on PrompExecutor.execute. Rewrite using testing API")
    @Test
    fun testChatStrategyDefaultName() = runTest {
        val initialPrompt = prompt("id") {
            system("System message")
            user("User question")
            assistant("Assistant question")
            user("User answer")
            assistant {
                toolCall(id = "tool-id-1", tool = "tool1", args = "{x=1}")
            }
            user {
                toolResult(id = "tool-id-1", tool = "tool1", output = "{result=2}")
            }
            assistant {
                toolCall(id = "tool-id-2", tool = "tool2", args = "{x=100}")
            }
            user {
                toolResult(id = "tool-id-2", tool = "tool2", output = "{result=-200}")
            }
        }

        val mockPromptExecutor = mockk<PromptExecutor>()

        val mockEnv = mockk<AIAgentEnvironment>()

        val initialModel = OllamaModels.Meta.LLAMA_3_2

        val agentConfig =
            AIAgentConfig(prompt = prompt("id") {}, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 10)

        val mockLLM = AIAgentLLMContext(
            tools = emptyList(),
            toolRegistry = ToolRegistry {},
            prompt = initialPrompt,
            model = initialModel,
            responseProcessor = null,
            promptExecutor = mockPromptExecutor,
            environment = mockEnv,
            config = agentConfig,
            clock = testClock
        )

        val executionInfo = AgentExecutionInfo(null, "test")

        val context = AIAgentGraphContext(
            environment = mockEnv,
            agentId = "test-agent",
            agentInputType = typeToken<String>(),
            agentInput = "Hello",
            config = mockk(),
            llm = mockLLM,
            stateManager = mockk(),
            storage = mockk(),
            runId = "run-1",
            strategyName = "test-strategy",
            pipeline = AIAgentGraphPipeline(agentConfig),
            executionInfo = executionInfo,
            parentContext = null
        )

        val anotherModel = OllamaModels.Meta.LLAMA_4_SCOUT

        val llmJudgeNode by llmAsAJudge<Int>(
            llmModel = anotherModel,
            task = CRITIC_TASK
        )

        coEvery { mockPromptExecutor.execute(any(), any<LLModel>()) } returns
            Message.Assistant(
                content = Json.encodeToString(
                    CriticResultFromLLM.serializer(),
                    CriticResultFromLLM(isCorrect = true, feedback = "All good")
                ),
                metaInfo = ResponseMetaInfo.create(testClock),
            )

        coEvery { mockPromptExecutor.getStandardJsonSchemaGenerator(any()) } returns StandardJsonSchemaGenerator()
        coEvery { mockPromptExecutor.getBasicJsonSchemaGenerator(any()) } returns BasicJsonSchemaGenerator()

        llmJudgeNode.execute(context, input = -200)

        val expectedXMLHistory = """
            <previous_conversation>
            <system>
            System message
            </system>
            <user>
            User question
            </user>
            <assistant>
            Assistant question
            </assistant>
            <user>
            User answer
            </user>
            <tool_call tool="tool1">
            {x=1}
            </tool_call>
            <tool_result tool="tool1">
            {result=2}
            </tool_result>
            <tool_call tool="tool2">
            {x=100}
            </tool_call>
            <tool_result tool="tool2">
            {result=-200}
            </tool_result>
            </previous_conversation>
        """.trimIndent()

        coVerify {
            mockPromptExecutor.execute(
                prompt = match {
                    (it.messages.size == 2) &&
                        (it.messages.first().role == Message.Role.System && (it.messages.first().parts.first() as MessagePart.Text).text == CRITIC_TASK) &&
                        (it.messages.last().role == Message.Role.User && (it.messages.last().parts.first() as MessagePart.Text).text.trimIndent() == expectedXMLHistory) &&
                        (it.id == "critic")
                },
                model = match<LLModel> {
                    it == anotherModel
                }
            )
        }

        assertEquals(initialPrompt, context.llm.prompt)

        assertEquals(initialModel, context.llm.model)
        assertNotEquals(anotherModel, context.llm.model)
    }
}
