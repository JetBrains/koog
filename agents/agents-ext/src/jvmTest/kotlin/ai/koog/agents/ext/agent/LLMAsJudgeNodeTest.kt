package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.FinishNode
import ai.koog.agents.core.agent.entity.StartNode
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LLMAsJudgeNodeTest {
    private val defaultName = "re_act"

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    val CRITIC_TASK = "Find all numbers produced by LLM and check that they are not divided by 3"

    @OptIn(InternalAgentsApi::class, DetachedPromptExecutorAPI::class)
    @Test
    fun testChatStrategyDefaultName() = runTest {
        val initialPrompt = prompt("id") {
            system("System message")
            user("User question")
            assistant("Assistant question")
            user("User answer")
            tool {
                call(id = "tool-id-1", tool = "tool1", content = "{x=1}")
                result(id = "tool-id-1", tool = "tool1", content = "{result=2}")
            }
            tool {
                call(id = "tool-id-2", tool = "tool2", content = "{x=100}")
                result(id = "tool-id-2", tool = "tool2", content = "{result=-200}")
            }
        }

        val mockPromptExecutor = mockk<PromptExecutor>()

        val mockEnv = mockk<AIAgentEnvironment>()

        val initialModel = OllamaModels.Meta.LLAMA_3_2

        val mockLLM = AIAgentLLMContext(
            tools = emptyList(),
            toolRegistry = ToolRegistry {},
            prompt = initialPrompt,
            model = initialModel,
            promptExecutor = mockPromptExecutor,
            environment = mockEnv,
            config = AIAgentConfig(prompt = prompt("id") {}, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 10),
            clock = testClock
        )

        val context = AIAgentGraphContext(
            environment = mockEnv,
            agentInputType = typeOf<String>(),
            agentInput = "Hello",
            config = mockk(),
            llm = mockLLM,
            stateManager = mockk(),
            storage = mockk(),
            runId = "run-1",
            strategyName = "test-strategy",
            pipeline = AIAgentGraphPipeline(),
            agent = mockk()
        )

        val subgraphContext = object : AIAgentSubgraphBuilderBase<String, String>() {
            override val nodeStart: StartNode<String> = mockk()
            override val nodeFinish: FinishNode<String> = mockk()
        }

        val anotherModel = OllamaModels.Meta.LLAMA_4_SCOUT

        val llmJudgeNode by subgraphContext.llmAsAJudge<Int>(
            llmModel = anotherModel,
            task = CRITIC_TASK
        )

        coEvery { mockPromptExecutor.execute(any(), any(), any()) } returns listOf(
            Message.Assistant(
                content = Json.encodeToString(
                    CriticResultFromLLM.serializer(),
                    CriticResultFromLLM(isCorrect = true, feedback = "All good")
                ),
                metaInfo = ResponseMetaInfo.create(testClock),
            )
        )

        llmJudgeNode.execute(context, input = -200)

        val expectedXMLHistory = """
            <previous_conversation>
            <user>
            System message
            </user>
            <user>
            User question
            </user>
            <assistant>
            Assistant question
            </assistant>
            <user>
            User answer
            </user>
            <tool_call tool=tool1>
            {x=1}
            </tool_call>
            <tool_result tool=tool1>
            {result=2}
            </tool_result>
            <tool_call tool=tool2>
            {x=100}
            </tool_call>
            <tool_result tool=tool2>
            {result=-200}
            </tool_result>
            </previous_conversation>
        """.trimIndent()

        coVerify {
            mockPromptExecutor.execute(
                prompt = match {
                    (it.messages.size == 2) &&
                        (it.messages.first().role == Message.Role.System && it.messages.first().content == CRITIC_TASK) &&
                        (it.messages.last().role == Message.Role.User && it.messages.last().content.trimIndent() == expectedXMLHistory) &&
                        (it.id == "critic")
                },
                model = match {
                    it == anotherModel
                },
                tools = any()
            )
        }

        assertEquals(initialPrompt, context.llm.prompt)

        assertEquals(initialModel, context.llm.model)
        assertNotEquals(anotherModel, context.llm.model)
    }
}
