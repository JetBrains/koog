package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.agent.AIAgentTestBase.ReportingLLMClient.Event
import ai.koog.integration.tests.utils.APIKeys.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.APIKeys.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.integration.tests.utils.tools.files.MockFileSystem
import ai.koog.integration.tests.utils.tools.files.OperationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class AIAgentMultipleLLMIntegrationTest : AIAgentTestBase() {
    companion object {
        @JvmStatic
        fun getLatestModels(): Stream<LLModel> = AIAgentTestBase.getLatestModels()

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> = AIAgentTestBase.modelsWithVisionCapability()
    }

    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    @Test
    @Retry(5)
    fun integration_testOpenAIAnthropicAgent() = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)

        val fs = MockFileSystem()
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onAgentCompleted { _ ->
                eventsChannel.send(Event.Termination)
            }
        }

        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)
        val reportingExecutor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val agent = createTestMultiLLMAgent(
            fs,
            eventHandlerConfig,
            maxAgentIterations = 50,
            initialExecutor = reportingExecutor,
        )

        val result = agent.run(
            "Generate me a simple kotlin method. Write a test"
        )
        eventsChannel.close()

        assertNotNull(result)

        assertTrue(
            fs.fileCount() > 0,
            "Agent must have created at least one file"
        )

        val messages = mutableListOf<Event.Message>()
        for (msg in eventsChannel) {
            if (msg is Event.Message) {
                messages.add(msg)
            } else {
                break
            }
        }

        assertTrue(
            messages.any { it.llmClient == "AnthropicLLMClient" },
            "At least one message must be delegated to Anthropic client"
        )

        assertTrue(
            messages.any { it.llmClient == "OpenAILLMClient" },
            "At least one message must be delegated to OpenAI client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "AnthropicLLMClient" }
                .all { it.model.provider == LLMProvider.Anthropic },
            "All prompts with Anthropic model must be delegated to Anthropic client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "OpenAILLMClient" }
                .all { it.model.provider == LLMProvider.OpenAI },
            "All prompts with OpenAI model must be delegated to OpenAI client"
        )
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    @Disabled("See KG-520 Agent with an empty tool registry is stuck into a loop if a subgraph has tools")
    fun `integration_test agent with not registered subgraph tool result fails`(model: LLModel) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(LLMProvider.OpenAI)
            Models.assumeAvailable(LLMProvider.Anthropic)

            val fs = MockFileSystem()
            val agent = createTestAgentWithToolsInSubgraph(fs = fs, model = model)

            try {
                val result = agent.run(
                    "Create a simple file called 'test.txt' with content 'Hello from subgraph tools!' and then read it back to verify it was created correctly."
                )
                fail("Expected AIAgentException but got result: $result")
            } catch (e: IllegalArgumentException) {
                assertContains(e.message ?: "", "Tool \"create_file\" is not defined")
            }
        }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun `integration_test agent with registered subgraph tool result runs`(model: LLModel) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(LLMProvider.OpenAI)
            Models.assumeAvailable(LLMProvider.Anthropic)

            val fs = MockFileSystem()
            val calledTools = mutableListOf<String>()
            val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
                onToolCallStarting { eventContext ->
                    calledTools.add(eventContext.tool.name)
                }
            }

            val agent = createTestAgentWithToolsInSubgraph(fs, eventHandlerConfig, model, false)

            val result = agent.run(
                "Create a simple file called 'test.txt' with content 'Hello from subgraph tools!' and then read it back to verify it was created correctly."
            )

            assertNotNull(result)
            assertTrue(result.isNotEmpty(), "Agent result should not be empty")

            assertTrue(
                fs.fileCount() > 0,
                "Agent must have created at least one file using subgraph tools"
            )

            when (val readResult = fs.read("test.txt")) {
                is OperationResult.Success -> {
                    assertTrue(
                        readResult.result.contains("Hello from subgraph tools!"),
                        "File should contain the expected content"
                    )
                }

                is OperationResult.Failure -> {
                    fail("Failed to read file: ${readResult.error}")
                }
            }

            assertTrue(
                calledTools.any { it == "create_file" },
                "At least one LLM call must have tools available"
            )
        }

    @Test
    fun integration_testTerminationOnIterationsLimitExhaustion() = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(LLMProvider.OpenAI)
        Models.assumeAvailable(LLMProvider.Anthropic)

        val fs = MockFileSystem()
        var errorMessage: String? = null
        val steps = 10
        val agent = createTestMultiLLMAgent(
            fs,
            { },
            maxAgentIterations = steps,
        )

        try {
            val result = agent.run(
                "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
            )
            assertNull(result)
        } catch (e: AIAgentException) {
            errorMessage = e.message
        } finally {
            assertEquals(
                "AI Agent has run into a problem: Agent couldn't finish in given number of steps ($steps). " +
                    "Please, consider increasing `maxAgentIterations` value in agent's configuration",
                errorMessage
            )
        }
    }

    @Test
    fun integration_testAnthropicAgentEnumSerialization() {
        runTest(timeout = 10.minutes) {
            val llmModel = AnthropicModels.Sonnet_4_5
            Models.assumeAvailable(llmModel.provider)
            val agent = AIAgent(
                promptExecutor = simpleAnthropicExecutor(anthropicApiKey),
                llmModel = llmModel,
                systemPrompt = "You are a calculator with access to the calculator tools. You MUST call tools!!!",
                toolRegistry = ToolRegistry {
                    tool(CalculatorTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentExecutionFailed { eventContext ->
                            println(
                                "error: ${eventContext.throwable.javaClass.simpleName}(${eventContext.throwable.message})\n${eventContext.throwable.stackTraceToString()}"
                            )
                        }
                        onToolCallStarting { eventContext ->
                            println(
                                "Calling tool ${eventContext.tool.name} with arguments ${
                                    eventContext.toolArgs.toString().lines().first().take(100)
                                }"
                            )
                        }
                    }
                }
            )

            val result = agent.run("calculate 10 plus 15, and then subtract 8")
            println("result = $result")
            assertNotNull(result)
            assertContains(result, "17")
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapability(model: LLModel) = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(model.provider)
        val fs = MockFileSystem()

        val imageFile = File(testResourcesDir.toFile(), "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        withRetry {
            val agent = createTestMultiLLMAgent(
                fs,
                { },
                maxAgentIterations = 20,
            )

            val result = agent.run(
                """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
            """
            )

            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(
                resultLowerCase.contains("error processing"),
                "Result should not contain error messages"
            )
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(
                resultLowerCase.contains("cannot process"),
                "Result should not indicate inability to process"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapabilityUrl(model: LLModel) = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(model.provider)

        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCallStarting { eventContext ->
                println(
                    "Calling tool ${eventContext.tool.name} with arguments ${
                        eventContext.toolArgs.toString().lines().first().take(100)
                    }"
                )
            }
        }

        val imageFile = File(testResourcesDir.toFile(), "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val prompt = prompt("example-prompt") {
            system("You are a professional helpful assistant.")

            user {
                markdown {
                    +"I'm sending you an image."
                    br()
                    +"Please analyze this image and identify the image format if possible."
                }
                image("https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg")
            }
        }

        withRetry(3) {
            val agent = createTestMultiLLMAgent(
                fs,
                eventHandlerConfig,
                maxAgentIterations = 50,
                prompt = prompt,
            )

            val result = agent.run("Hi! Please analyse my image.")
            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(resultLowerCase.contains("error processing"), "Result should not contain error messages")
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(
                resultLowerCase.contains("cannot process"),
                "Result should not indicate inability to process"
            )
        }
    }
}
