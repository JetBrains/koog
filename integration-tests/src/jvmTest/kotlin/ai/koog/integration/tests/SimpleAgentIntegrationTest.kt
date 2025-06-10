package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorTool
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class SimpleAgentIntegrationTest {
    val systemPrompt = "You are a helpful assistant."

    companion object {
        private lateinit var testResourcesDir: File

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir = File("src/jvmTest/resources/media")
            testResourcesDir.mkdirs()

            val markdownFile = File(testResourcesDir, "test.md")
            markdownFile.writeText(
                """
                # Test Markdown File

                This is a test markdown file for integration testing.

                ## Features
                - Support for markdown files
                - Integration with LLM models
                - Testing capabilities

                ## Usage
                - Run the `integration_test` Gradle task to run the tests.
                - Run the `integrationTest` Maven goal to run the tests.

                ## License
                This project is licensed under the Apache License 2.0.
            """.trimIndent()
            )

            val textFile = File(testResourcesDir, "test.txt")
            textFile.writeText("This is a simple text file for testing document handling.")

            val imageFile = File(testResourcesDir, "test.png")
            imageFile.writeBytes(
                byteArrayOf(
                    -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13,
                    73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6,
                    0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 10, 73, 68, 65,
                    84, 120, -100, 99, 0, 1, 0, 0, 5, 0, 1, 13, 10, 45,
                    -76, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
                )
            )

            val audioFile = File(testResourcesDir, "test.wav")
            audioFile.writeBytes(
                byteArrayOf(
                    82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86, 69, 102, 109, 116, 32,
                    16, 0, 0, 0, 1, 0, 1, 0, 68, -84, 0, 0, -120, 88, 1, 0,
                    2, 0, 16, 0, 100, 97, 116, 97, 0, 0, 0, 0
                )
            )

            val pdfFile = File(testResourcesDir, "test.pdf")
            pdfFile.writeText(
                """%PDF-1.4
                        1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                        2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
                        3 0 obj<</Type/Page/Contents 4 0 R>>endobj
                        4 0 obj<</Length 44>>stream
                        BT/F1 12 Tf 100 700 Td(Test PDF for Koog)Tj ET
                        endstream endobj
                        xref 0 5
                        0000000000 65535 f 
                        0000000010 00000 n 
                        0000000074 00000 n 
                        0000000142 00000 n 
                        0000000210 00000 n 
                        trailer<</Size 5/Root 1 0 R>>startxref 300 %%EOF""".trimIndent()
            )
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
        fun modelWithVisionCapability(): Stream<Arguments> {
            val openAIClient = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClient = AnthropicLLMClient(readTestAnthropicKeyFromEnv())

            return Stream.concat(
                Models.openAIModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image)
                    }
                    .map { model -> Arguments.of(model, openAIClient) },

                Models.anthropicModels()
                    .filter { model ->
                        model.capabilities.contains(LLMCapability.Vision.Image)
                    }
                    .map { model -> Arguments.of(model, anthropicClient) }
            )
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
    val results = mutableListOf<String?>()

    @BeforeEach
    fun setup() {
        assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
    }

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_AIAgentShouldNotCallToolsByDefault(model: LLModel) = runBlocking {
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
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        withRetry(times = 3, testName = "integration_AIAgentShouldNotCallToolsByDefault[${model.id}]") {
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

        withRetry(times = 3, testName = "integration_AIAgentShouldCallCustomTool[${model.id}]") {
            agent.run("How much is 3 times 5?")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
            assertTrue(
                actualToolCalls.contains(CalculatorTool.name),
                "The ${CalculatorTool.name} tool was not called for model $model"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelWithVisionCapability")
    fun integration_AIAgentWithImageCapability(model: LLModel) = runTest(timeout = 120.seconds) {
        assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val imageBytes = imageFile.readBytes()
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes)

        val promptWithImage = """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and describe what you see.
        """.trimIndent()

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

        val result = results.first()
        assertNotNull(result, "Result should not be null")
    }
}
