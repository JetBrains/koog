package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorTool
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SimpleAgentIntegrationTest {
    val systemPrompt = "You are a helpful assistant."

    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setup() {
            testResourcesDir =
                Paths.get(SimpleAgentIntegrationTest::class.java.getResource("/media")!!.toURI())
        }

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

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> {
            return Models.modelsWithVisionCapability()
        }
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onBeforeAgentStarted { strategy, agent ->
            println("Agent started: strategy=${strategy.javaClass.simpleName}, agent=${agent.javaClass.simpleName}")
        }

        onAgentFinished { strategyName, result ->
            println("Agent finished: strategy=$strategyName, result=$result")
            results.add(result)
        }

        onAgentRunError { strategyName, sessionUuid, throwable ->
            println("Agent error: strategy=$strategyName, error=${throwable.message}")
            errors.add(throwable)
        }

        onStrategyStarted { strategy ->
            println("Strategy started: ${strategy.javaClass.simpleName}")
        }

        onStrategyFinished { strategyName, result ->
            println("Strategy finished: strategy=$strategyName, result=$result")
        }

        onBeforeNode { node, context, input ->
            println("Before node: node=${node.javaClass.simpleName}, input=$input")
        }

        onAfterNode { node, context, input, output ->
            println("After node: node=${node.javaClass.simpleName}, input=$input, output=$output")
        }

        onBeforeLLMCall { prompt, tools, model, sessionUuid ->
            println("Before LLM call with tools: prompt=$prompt, tools=${tools.map { it.name }}")
        }

        onAfterLLMCall { prompt, tools, model, responses, sessionUuid ->
            println("After LLM call with tools: response=${responses.map { it.content.take(50) }}")
        }

        onToolCall { tool, args ->
            println("Tool called: tool=${tool.name}, args=$args")
            actualToolCalls.add(tool.name)
        }

        onToolValidationError { tool, args, value ->
            println("Tool validation error: tool=${tool.name}, args=$args, value=$value")
        }

        onToolCallFailure { tool, args, throwable ->
            println("Tool call failure: tool=${tool.name}, args=$args, error=${throwable.message}")
        }

        onToolCallResult { tool, args, result ->
            println("Tool call result: tool=${tool.name}, args=$args, result=$result")
        }
    }

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<Any?>()

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentShouldNotCallToolsByDefault(model: LLModel) = runBlocking {
        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
                systemPrompt = systemPrompt,
                llmModel = model,
                temperature = 1.0,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )
            agent.run("Repeat what I say: hello, I'm good.")
            // by default, AIAgent has no tools underneath
            assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentShouldCallCustomTool(model: LLModel) = runBlocking {
        val systemPromptForSmallLLM = systemPrompt + "You MUST use tools."
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
        // ToDo remove after fixes
        assumeTrue(model != OpenAIModels.Reasoning.O1, "JBAI-13980")
        assumeTrue(model != GoogleModels.Gemini2_5ProPreview0506, "JBAI-14481")
        assumeTrue(!model.id.contains("flash"), "JBAI-14094")

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
                systemPrompt = if (model.id == OpenAIModels.CostOptimized.O4Mini.id) systemPromptForSmallLLM else systemPrompt,
                llmModel = model,
                temperature = 1.0,
                toolRegistry = toolRegistry,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
            )

            agent.run("How much is 3 times 5?")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
            assertTrue(
                actualToolCalls.contains(CalculatorTool.name),
                "The ${CalculatorTool.name} tool was not called for model $model"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_AIAgentWithImageCapability(model: LLModel) = runTest(timeout = 120.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val imageFile = testResourcesDir.resolve("test.png")

        val imageBytes = imageFile.readBytes()
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes)

        val promptWithImage = """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
        """.trimIndent()

        withRetry {
            val executor = when (model.provider) {
                is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
                is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
                else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            }

            val agent = AIAgent(
                executor = executor,
                systemPrompt = "You are a helpful assistant that can analyze images.",
                llmModel = model,
                temperature = 0.7,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
            )

            agent.run(promptWithImage)

            assertTrue(errors.isEmpty(), "There should be no errors")
            assertTrue(results.isNotEmpty(), "There should be results")

            val result = results.first() as String
            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(resultLowerCase.contains("error processing"), "Result should not contain error messages")
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(resultLowerCase.contains("cannot process"), "Result should not indicate inability to process")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testRequestLLMWithoutTools(model: LLModel) = runTest(timeout = 300.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        val customStrategy = strategy("test-without-tools") {
            val callLLM by nodeLLMRequest(name = "callLLM", allowToolCalls = false)
            edge(nodeStart forwardTo callLLM)
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = customStrategy,
            agentConfig = AIAgentConfig(
                prompt("test-without-tools") {},
                model,
                maxAgentIterations = 10
            ),
            toolRegistry = toolRegistry
        )

        withRetry(times = 3, testName = "integration_testRequestLLMWithoutTools[${model.id}]") {
            val result = agent.run("What is 123 + 456?")

            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotEmpty(), "Result should not be empty")
            assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")

            assertTrue(
                result.contains("579"),
                "Result should contain the correct answer (579)"
            )
        }
    }
}
