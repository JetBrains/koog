package ai.koog.integration.tests

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.AfterTest
import kotlin.test.assertTrue

@ExtendWith(OllamaTestFixtureExtension::class)
class SimpleAgentIntegrationTest {
    val systemPrompt = """
            You are a helpful assistant. 
            You MUST use tools to communicate to the user.
            You MUST NOT communicate to the user without tools.
        """.trimIndent()

    companion object {
        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Models.openAIModels()
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Models.anthropicModels()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
        }
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onBeforeAgentStarted = { strategy, agent ->
            println("Agent started: strategy=${strategy.javaClass.simpleName}, agent=${agent.javaClass.simpleName}")
        }

        onAgentFinished = { strategyName, result ->
            println("Agent finished: strategy=$strategyName, result=$result")
            results.add(result)
        }

        onAgentRunError = { strategyName, throwable ->
            println("Agent error: strategy=$strategyName, error=${throwable.message}")
            errors.add(throwable)
        }

        onStrategyStarted = { strategy ->
            println("Strategy started: ${strategy.javaClass.simpleName}")
        }

        onStrategyFinished = { strategyName, result ->
            println("Strategy finished: strategy=$strategyName, result=$result")
        }

        onBeforeNode = { node, context, input ->
            println("Before node: node=${node.javaClass.simpleName}, input=$input")
        }

        onAfterNode = { node, context, input, output ->
            println("After node: node=${node.javaClass.simpleName}, input=$input, output=$output")
        }

        onBeforeLLMCall = { prompt: Prompt, tools: List<ToolDescriptor> ->
            println("Before LLM call with tools: prompt=$prompt, tools=${tools.map { it.name }}")
        }

        onAfterLLMCall = { response ->
            println("After LLM call with tools: response=${response.map { it.content.take(50) }}")
        }

        onToolCall = { tool, args ->
            println("Tool called: tool=${tool.name}, args=$args")
            actualToolCalls.add(tool.name)
        }

        onToolValidationError = { tool, args, value ->
            println("Tool validation error: tool=${tool.name}, args=$args, value=$value")
        }

        onToolCallFailure = { tool, args, throwable ->
            println("Tool call failure: tool=${tool.name}, args=$args, error=${throwable.message}")
        }

        onToolCallResult = { tool, args, result ->
            println("Tool call result: tool=${tool.name}, args=$args, result=$result")
        }
    }

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<String?>()

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_simpleSingleRunAgentShouldNotCallToolsByDefault(model: LLModel) = runBlocking {
        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleSingleRunAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        try {
            agent.run("Repeat what I say: hello, I'm good.")
        } catch (e: Exception) {
            if (e.message?.contains("Error from GoogleAI API: 500 Internal Server Error") == true) {
                assumeTrue(false, "Skipping test due to GoogleAI API 500 Internal Server Error")
            } else {
                throw e
            }
        }

        // by default, simpleSingleRunAgent has no tools underneath
        assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")

    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_simpleSingleRunAgentShouldCallCustomTool(model: LLModel) = runBlocking {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model != OpenAIModels.Reasoning.O1, "JBAI-13980")
        assumeTrue(!model.id.contains("flash"), "JBAI-14094")

        val toolRegistry = ToolRegistry.Companion {
            tool(SayToUser)
        }

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleSingleRunAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Write a Kotlin function to calculate factorial.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
        assertTrue(
            actualToolCalls.contains(SayToUser.name),
            "The ${SayToUser.name} tool was not called for model $model"
        )
    }
}
