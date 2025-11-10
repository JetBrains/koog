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
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class AIAgentMultipleLLMIntegrationTest : AIAgentTestBase() {
    companion object {
        @JvmStatic
        fun getLatestModels(): Stream<LLModel> = AIAgentTestBase.getLatestModels()
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

        agent.run(
            "Generate me a simple kotlin method. Write a test"
        ) shouldNotBe null
        eventsChannel.close()

        fs.fileCount() shouldBeGreaterThan 0

        val messages = mutableListOf<Event.Message>()
        for (msg in eventsChannel) {
            if (msg is Event.Message) {
                messages.add(msg)
            } else {
                break
            }
        }

        with(messages) {
            any { it.llmClient == "AnthropicLLMClient" }
                .shouldBeTrue()

            any { it.llmClient == "OpenAILLMClient" }
                .shouldBeTrue()

            filter { it.llmClient == "AnthropicLLMClient" }
                .all { it.model.provider == LLMProvider.Anthropic }
                .shouldBeTrue()

            filter { it.llmClient == "OpenAILLMClient" }
                .all { it.model.provider == LLMProvider.OpenAI }.shouldBeTrue()
        }
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
                (e.message ?: "").shouldContain("Tool \"create_file\" is not defined")
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

            agent.run(
                "Create a simple file called 'test.txt' with content 'Hello from subgraph tools!' and then read it back to verify it was created correctly."
            ).shouldNotBeBlank()

            fs.fileCount() shouldBeGreaterThan 0

            when (val readResult = fs.read("test.txt")) {
                is OperationResult.Success -> {
                    readResult.result shouldContain ("Hello from subgraph tools!")
                }

                is OperationResult.Failure -> {
                    fail("Failed to read file: ${readResult.error}")
                }
            }

            calledTools.shouldForAny { it == "create_file" }
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
            agent.run(
                "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
            ) shouldBe null
        } catch (e: AIAgentException) {
            errorMessage = e.message
        } finally {
            errorMessage shouldBe "AI Agent has run into a problem: Agent couldn't finish in given number of steps ($steps). " +
                "Please, consider increasing `maxAgentIterations` value in agent's configuration"
        }
    }

    @Test
    fun integration_testAnthropicAgentEnumSerialization() {
        runTest(timeout = 10.minutes) {
            val llmModel = AnthropicModels.Sonnet_4_5
            Models.assumeAvailable(llmModel.provider)

            AIAgent(
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
            ).run("calculate 10 plus 15, and then subtract 8") shouldNotBeNull { shouldContain("17") }
        }
    }

    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapability(model: LLModel) = runTest(timeout = 10.minutes) {
        Models.assumeAvailable(model.provider)
        val fs = MockFileSystem()

        val imageFile = File(testResourcesDir.toFile(), "test.png")
        imageFile.exists().shouldBeTrue()

        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)

        withRetry {
            val agent = createTestMultiLLMAgent(
                fs,
                { },
                maxAgentIterations = 20,
            )

            with(
                agent.run(
                    """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
            """
                )
            ) {
                shouldNotBeBlank()
                length shouldBeGreaterThan 20
                lowercase()
                    .shouldNotContain("error processing")
                    .shouldNotContain("unable to process")
                    .shouldNotContain("cannot process")
            }
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
        imageFile.exists().shouldBeTrue()

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

            with(agent.run("Hi! Please analyse my image.")) {
                shouldNotBeBlank()
                length shouldBeGreaterThan 20
                lowercase()
                    .shouldNotContain("error processing")
                    .shouldNotContain("unable to process")
                    .shouldNotContain("cannot process")
            }
        }
    }
}
