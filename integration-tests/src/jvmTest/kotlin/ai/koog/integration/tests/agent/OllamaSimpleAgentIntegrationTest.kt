package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.InjectOllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixtureExtension
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.annotations.RetryExtension
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.serialization.typeToken
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.seconds

@ExtendWith(OllamaTestFixtureExtension::class)
@ExtendWith(RetryExtension::class)
class OllamaSimpleAgentIntegrationTest : AIAgentTestBase() {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val ollamaSimpleExecutor get() = fixture.executor
        private val ollamaModel get() = fixture.toolChoiceModel
        private const val WORD = "Wiedergabegeschwindigkeitsmesserverwendungserlaubnis"
    }

    class GiveMeWordTool(name: String) : SimpleTool<GiveMeWordTool.Args>(
        argsType = typeToken<Args>(),
        name = name,
        description = "A tool that returns a very specific German word.",
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("The question to ask")
            val question: String
        )

        override suspend fun execute(args: Args): String = WORD
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCallStarting { eventContext ->
            actualToolCalls.add(eventContext.toolName)
            actualToolCallArgs.add(eventContext.toolArgs.toString())
        }
    }

    val actualToolCalls = mutableListOf<String>()
    val actualToolCallArgs = mutableListOf<String>()

    @AfterEach
    fun teardown() {
        actualToolCalls.clear()
        actualToolCallArgs.clear()
    }

    @Retry
    @Test
    fun ollama_simpleTest() = runTest(timeout = 600.seconds) {
        val giveMeWordTool = GiveMeWordTool("give_me_word_tool")
        val toolRegistry = ToolRegistry {
            tool(giveMeWordTool)
        }

        val result = AIAgent(
            promptExecutor = ollamaSimpleExecutor,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "ollama-simple-agent",
                    params = LLMParams(
                        temperature = 0.0,
                        toolChoice = ToolChoice.Named(giveMeWordTool.name)
                    )
                ) {},
                model = ollamaModel,
                maxAgentIterations = 10,
            ),
            toolRegistry = toolRegistry,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        ).run(
            "Give me any word."
        )

        actualToolCalls.shouldNotBeEmpty().shouldContain(giveMeWordTool.name)
        actualToolCallArgs.shouldNotBeEmpty()
        result.shouldNotBeBlank().shouldContain(WORD)
    }
}
