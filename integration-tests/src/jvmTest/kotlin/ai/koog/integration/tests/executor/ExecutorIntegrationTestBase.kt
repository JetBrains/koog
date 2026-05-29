package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkImageAnalysisResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsReasoning
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsReasoningWithEncryption
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsTextAndToolCall
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsToolCall
import ai.koog.integration.tests.utils.TestUtils.assertResponseDoesNotLeakRequestAttachments
import ai.koog.integration.tests.utils.TestUtils.assertToolResultCorrelatesWithCall
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.integration.tests.utils.structuredOutput.Country
import ai.koog.integration.tests.utils.structuredOutput.checkWeatherStructuredOutputResponse
import ai.koog.integration.tests.utils.structuredOutput.countryStructuredOutputPrompt
import ai.koog.integration.tests.utils.structuredOutput.getFixingParser
import ai.koog.integration.tests.utils.structuredOutput.getManualConfig
import ai.koog.integration.tests.utils.structuredOutput.getNativeConfig
import ai.koog.integration.tests.utils.structuredOutput.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.structuredOutput.weatherStructuredOutputPrompt
import ai.koog.integration.tests.utils.tools.CalculatorTool
import ai.koog.integration.tests.utils.tools.LotteryTool
import ai.koog.integration.tests.utils.tools.PickColorFromListTool
import ai.koog.integration.tests.utils.tools.PickColorTool
import ai.koog.integration.tests.utils.tools.PriceCalculatorTool
import ai.koog.integration.tests.utils.tools.SimpleCalculatorTool
import ai.koog.integration.tests.utils.tools.SimplePriceCalculatorTool
import ai.koog.integration.tests.utils.tools.calculatorPrompt
import ai.koog.integration.tests.utils.tools.calculatorPromptNotRequiredOptionalParams
import ai.koog.integration.tests.utils.tools.calculatorToolDescriptorOptionalParams
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.AnthropicLLMProvider
import ai.koog.prompt.llm.GoogleLLMProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaLLMProvider
import ai.koog.prompt.llm.OpenAILLMProvider
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.prompt.streaming.StreamFrame
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAll
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldNotBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

abstract class ExecutorIntegrationTestBase {
    private val testScope = TestScope()
    private val basicLimit = 256
    private val extendedLimit = 512
    private val reasoningLimit = 10000

    @AfterEach
    fun cleanup() {
        testScope.cancel()
    }

    companion object {
        protected lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setupTestResourcesBase() {
            testResourcesDir =
                Paths.get(ExecutorIntegrationTestBase::class.java.getResource("/media")!!.toURI())
        }
    }

    abstract fun getExecutor(model: LLModel): PromptExecutor

    open fun getLLMClient(model: LLModel): LLMClient = getLLMClientForProvider(model.provider)

    open fun createReasoningParams(model: LLModel): LLMParams {
        return when (model.provider) {
            is AnthropicLLMProvider -> AnthropicParams(
                thinking = AnthropicThinking.Enabled(budgetTokens = 1024)
            )

            is OpenAILLMProvider -> OpenAIResponsesParams(
                reasoning = ReasoningConfig(
                    effort = ReasoningEffort.MEDIUM,
                    summary = ReasoningSummary.AUTO
                ),
                include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
                maxTokens = reasoningLimit
            )

            is GoogleLLMProvider -> {
                val thinkingConfig = GoogleThinkingConfig(
                    includeThoughts = true,
                    thinkingBudget = reasoningLimit
                )
                GoogleParams(
                    thinkingConfig = thinkingConfig,
                    // Slightly higher limit to avoid truncation in multi-step reasoning tests
                    maxTokens = reasoningLimit
                )
            }

            else -> LLMParams(maxTokens = reasoningLimit)
        }
    }

    private fun createNoReasoningParams(model: LLModel): LLMParams = when (model.provider) {
        is AnthropicLLMProvider -> AnthropicParams(
            thinking = AnthropicThinking.Disabled()
        )

        is OpenAILLMProvider ->
            if (model.supports(LLMCapability.OpenAIEndpoint.Responses)) {
                OpenAIResponsesParams(
                    maxTokens = basicLimit
                )
            } else {
                OpenAIChatParams(
                    maxTokens = basicLimit
                )
            }

        is GoogleLLMProvider ->
            GoogleParams(
                thinkingConfig = GoogleThinkingConfig(
                    includeThoughts = false,
                ),
                // Slightly higher limit to avoid truncation in multi-step reasoning tests
                maxTokens = extendedLimit
            )

        else -> LLMParams(maxTokens = basicLimit)
    }

    private fun toolRoundTripParams(model: LLModel): LLMParams = when (model.provider) {
        is OpenAILLMProvider ->
            if (model.supports(LLMCapability.OpenAIEndpoint.Responses)) {
                OpenAIResponsesParams(toolChoice = ToolChoice.Required, maxTokens = extendedLimit)
            } else {
                OpenAIChatParams(toolChoice = ToolChoice.Required, maxTokens = extendedLimit)
            }

        else -> LLMParams(toolChoice = ToolChoice.Required, maxTokens = extendedLimit)
    }

    private fun noToolFollowUpParams(model: LLModel): LLMParams = when (model.provider) {
        is OpenAILLMProvider ->
            if (model.supports(LLMCapability.OpenAIEndpoint.Responses)) {
                OpenAIResponsesParams(toolChoice = ToolChoice.None, maxTokens = extendedLimit)
            } else {
                OpenAIChatParams(toolChoice = ToolChoice.None, maxTokens = extendedLimit)
            }

        else -> LLMParams(toolChoice = ToolChoice.None, maxTokens = extendedLimit)
    }

    private fun calculatorToolResultFor(toolCall: MessagePart.Tool.Call): MessagePart.Tool.Result =
        MessagePart.Tool.Result(
            id = toolCall.id,
            tool = toolCall.tool,
            output = "579"
        )

    open fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = Prompt.build("test-prompt", createNoReasoningParams(model)) {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            getExecutor(model).execute(prompt, model) shouldNotBeNull {
                parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                    text.lowercase().shouldContain("paris")
                }
                with(metaInfo) {
                    inputTokensCount.shouldNotBeNull()
                    outputTokensCount.shouldNotBeNull()
                    totalTokensCount.shouldNotBeNull()
                }
            }
        }
    }

    open fun integration_testExecuteStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(
            model != GoogleModels.Gemini3_1Pro_Preview,
            "KG-768 GoogleLLMClient.executeStreaming() may hang because the stream never completes with End frame"
        )

        val executor = getExecutor(model)
        val params = createNoReasoningParams(model)

        val prompt = Prompt.build("test-streaming", params = params) {
            system("You are a helpful assistant.")
            user("Count from 1 to 5. Like 1, 2, 3 ...")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            val endFrames = mutableListOf<StreamFrame.End>()
            val textDeltaFrames = mutableListOf<StreamFrame.TextDelta>()
            val toolDeltaFrames = mutableListOf<StreamFrame.ToolCallDelta>()
            val toolCompleteFrames = mutableListOf<StreamFrame.ToolCallComplete>()

            executor.executeStreamAndCollect(
                prompt = prompt,
                model = model,
                textDeltaFrames = textDeltaFrames,
                toolDeltaFrames = toolDeltaFrames,
                toolCompleteFrames = toolCompleteFrames,
                endFrame = endFrames,
            )

            toolDeltaFrames.shouldBeEmpty()
            toolCompleteFrames.shouldBeEmpty()
            when (model.provider) {
                is OllamaLLMProvider -> endFrames.size shouldBe 0

                else -> {
                    endFrames.size shouldBe 1
                    endFrames.first() should { end ->
                        end.metaInfo should { meta ->
                            withClue("ResponseMetaInfo should contain at least some non-nullable token count info") {
                                listOf(meta.inputTokensCount, meta.outputTokensCount, meta.totalTokensCount)
                                    .shouldForAny { it != null }
                            }
                        }
                    }
                }
            }

            textDeltaFrames.joinToString { it.text } shouldNotBeNull {
                shouldContain("1")
                shouldContain("2")
                shouldContain("3")
                shouldContain("4")
                shouldContain("5")
            }
        }
    }

    open fun integration_testToolWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithRequiredParams[${model.id}]") {
            with(getExecutor(model).execute(calculatorPrompt, model, listOf(CalculatorTool.descriptor))) {
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithNotRequiredOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithNotRequiredOptionalParams[${model.id}]") {
            with(
                getExecutor(model).execute(
                    calculatorPromptNotRequiredOptionalParams,
                    model,
                    listOf(calculatorToolDescriptorOptionalParams)
                )
            ) {
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithOptionalParams[${model.id}]") {
            with(getExecutor(model).execute(calculatorPrompt, model, listOf(calculatorToolDescriptorOptionalParams))) {
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Picker random color for me!")
        }

        withRetry(times = 3, testName = "integration_testToolWithNoParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PickColorTool.descriptor))) {
                assertResponseContainsToolCall(this, PickColorTool.name)
            }
        }
    }

    open fun integration_testToolWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Pick me a color from red, green, orange!")
        }

        withRetry(times = 3, testName = "integration_testToolWithListEnumParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PickColorFromListTool.descriptor))) {
                assertResponseContainsToolCall(this, PickColorFromListTool.name)
            }
        }
    }

    open fun integration_testToolWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with lottery tool. You MUST always call tools!!!"
            }
            user("Select winners from lottery tickets [10, 42, 43, 51, 22] and [34, 12, 4, 53, 99]")
        }

        withRetry(times = 3, testName = "integration_testToolWithNestedListParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(LotteryTool.descriptor))) {
                assertResponseContainsToolCall(this, LotteryTool.name)
            }
        }
    }

    open fun integration_testToolsWithNullParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(
            model.provider != LLMProvider.MistralAI,
            "MistralAI returns json array which we are failing to parse. Remove after KG-535 fix"
        )

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with tokens price calculator tool."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Calculate price of 10 tokens if I pay 0.003 euro. Discount is not provided to set null.")
        }

        withRetry(times = 3, testName = "integration_testToolsWithNullParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(SimplePriceCalculatorTool.descriptor))) {
                parts.any { it is MessagePart.Tool.Call && it.args.contains("null") }
            }
        }
    }

    open fun integration_testToolsWithAnyOfParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools", LLMParams(toolChoice = ToolChoice.Required)) {
            system {
                +"You are a helpful assistant with tokens price calculator tool."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Calculate price of 10 tokens if I pay 0.003 euro for token with 10% discount.")
        }

        withRetry(testName = "integration_testToolsWithAnyOfParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PriceCalculatorTool.descriptor))) {
                assertResponseContainsToolCall(this, PriceCalculatorTool.name)
            }
        }
    }

    open fun integration_testAssistantMultiPartRoundTrip(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model.supports(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val firstPrompt = Prompt.build("assistant-multipart-roundtrip-1", toolRoundTripParams(model)) {
            system(
                "You are a tool-calling assistant. Before calling a tool, include a brief text note. " +
                    "Then call the calculator tool. Do not answer the calculation yourself."
            )
            user("Say that you will calculate it, then call the calculator tool for 123 + 456.")
        }

        val firstResponse =
            withRetry(times = 3, testName = "integration_testAssistantMultiPartRoundTrip_Turn1[${model.id}]") {
                getExecutor(model).execute(firstPrompt, model, listOf(CalculatorTool.descriptor))
            }

        val toolCall = assertResponseContainsTextAndToolCall(firstResponse, CalculatorTool.name)
        val toolResult = calculatorToolResultFor(toolCall)
        assertToolResultCorrelatesWithCall(toolCall, toolResult)

        val secondPrompt = Prompt(
            id = "assistant-multipart-roundtrip-2",
            messages = firstPrompt.messages + firstResponse + Message.User(toolResult, RequestMetaInfo.Empty),
            params = noToolFollowUpParams(model)
        )

        withRetry(times = 3, testName = "integration_testAssistantMultiPartRoundTrip_Turn2[${model.id}]") {
            val secondResponse = getExecutor(model).execute(secondPrompt, model, listOf(CalculatorTool.descriptor))
            secondResponse.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.shouldContain("579")
            }
        }
    }

    open fun integration_testToolCallResultCorrelationById(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model.supports(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val firstPrompt = Prompt.build("tool-result-correlation-1", toolRoundTripParams(model)) {
            system("You are a helpful assistant. Call the calculator tool for arithmetic requests.")
            user("Use the calculator tool to compute 123 + 456.")
        }

        val firstResponse =
            withRetry(times = 3, testName = "integration_testToolCallResultCorrelationById_Turn1[${model.id}]") {
                getExecutor(model).execute(firstPrompt, model, listOf(CalculatorTool.descriptor))
            }

        val toolCall = firstResponse.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull().shouldNotBeNull()
        val toolResult = calculatorToolResultFor(toolCall)
        assertToolResultCorrelatesWithCall(toolCall, toolResult)

        val secondPrompt = Prompt(
            id = "tool-result-correlation-2",
            messages = firstPrompt.messages + firstResponse + Message.User(toolResult, RequestMetaInfo.Empty),
            params = noToolFollowUpParams(model)
        )

        withRetry(times = 3, testName = "integration_testToolCallResultCorrelationById_Turn2[${model.id}]") {
            val secondResponse = getExecutor(model).execute(secondPrompt, model, listOf(CalculatorTool.descriptor))
            secondResponse.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.shouldContain("579")
            }
        }
    }

    open fun integration_testMarkdownStructuredDataStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model != OpenAIModels.Chat.GPT4_1Nano, "Model $model is too small for structured streaming")

        withRetry(times = 3, testName = "integration_testStructuredDataStreaming[${model.id}]") {
            val markdownStream = getLLMClient(model).executeStreaming(countryStructuredOutputPrompt, model)
            with(mutableListOf<Country>()) {
                parseMarkdownStreamToCountries(markdownStream).collect { country ->
                    add(country)
                }

                shouldNotBeEmpty()
            }
        }
    }

    open fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) =
        runTest(timeout = 10.minutes) {
            Models.assumeAvailable(model.provider)

            val file = MediaTestUtils.createMarkdownFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("markdown-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze markdown files.")

                user {
                    markdown {
                        +"I'm sending you a markdown file with different markdown elements. "
                        +"Please list all the markdown elements used in it and describe its structure clearly."
                    }

                    if (model.supports(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                        textFile(KtPath(file.pathString), "text/plain")
                    } else {
                        markdown {
                            +file.readText()
                        }
                    }
                }
            }

            withRetry {
                try {
                    with(
                        getExecutor(model).execute(prompt, model)
                    ) {
                        when (scenario) {
                            MarkdownTestScenario.MALFORMED_SYNTAX, MarkdownTestScenario.BROKEN_LINKS -> {
                                checkResponseBasic(this)
                            }

                            else -> {
                                checkExecutorMediaResponse(this)
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw e
                }
            }
        }

    open fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.supports(LLMCapability.Vision.Image),
                "Model must support vision capability"
            )

            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please analyze it and identify the image format if possible."
                    }

                    image(KtPath(imageFile.pathString))
                }
            }

            withRetry {
                try {
                    checkExecutorMediaResponse(getExecutor(model).execute(prompt, model))
                } catch (e: LLMClientException) {
                    // For some edge cases, exceptions are expected
                    when (scenario) {
                        ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                            val message = e.message.shouldNotBeNull()

                            listOf(
                                "Status code: 400",
                                "Could not process image",
                                "You uploaded an unsupported image. Please make sure your image is valid.",
                            ).any { it in message }
                                .shouldBe(true, "Must contain error message from the list")
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)

            val file = MediaTestUtils.createTextFileForScenario(scenario, testResourcesDir)

            val prompt =
                if (model.supports(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                            }

                            textFile(KtPath(file.pathString), "text/plain")
                        }
                    }
                } else {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user(
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                                newline()
                                +file.readText()
                            }
                        )
                    }
                }

            withRetry {
                try {
                    val response = getExecutor(model).execute(prompt, model)
                    checkExecutorMediaResponse(response)
                } catch (e: LLMClientException) {
                    when (scenario) {
                        TextTestScenario.EMPTY_TEXT -> {
                            if (model.provider == LLMProvider.Google) {
                                val message = e.message.shouldNotBeNull()
                                message.shouldContain("Status code: 400")
                                message.shouldContain("Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again.")
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.supports(LLMCapability.Audio),
                "Model must support audio capability"
            )

            val audioFile = MediaTestUtils.createAudioFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("audio-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant.")

                user {
                    text("I'm sending you an audio file. Please tell me a couple of words about it.")
                    audio(KtPath(audioFile.pathString))
                }
            }

            withRetry(times = 3, testName = "integration_testAudioProcessingBasic[${model.id}]") {
                try {
                    checkExecutorMediaResponse(getExecutor(model).execute(prompt, model))
                } catch (e: LLMClientException) {
                    if (scenario == AudioTestScenario.CORRUPTED_AUDIO) {
                        val message = e.message.shouldNotBeNull()

                        message.shouldContain("Status code: 400")
                        if (model.provider == LLMProvider.OpenAI) {
                            message.shouldContain("This model does not support the format you provided.")
                        } else if (model.provider == LLMProvider.Google) {
                            message.shouldContain("Request contains an invalid argument.")
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

    open fun integration_testBase64EncodedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(
            model.supports(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageFile = MediaTestUtils.getImageFileForScenario(ImageTestScenario.BASIC_PNG, testResourcesDir)
        val prompt = prompt("base64-encoded-attachments-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you an image. Please analyze them and tell me about their content."
                }

                image(KtPath(imageFile.pathString))
            }
        }

        withRetry {
            with(
                getExecutor(model).execute(prompt, model)
            ) {
                checkImageAnalysisResponse(this)
            }
        }
    }

    open fun integration_testUrlBasedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider !== LLMProvider.Google, "Google models do not support URL attachments")

        assumeTrue(
            model.supports(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageUrl =
            "https://raw.githubusercontent.com/JetBrains/koog/1e7014eae7dca603cfceaece27c135ecdc45e2a2/integration-tests/src/jvmTest/resources/media/test.png"

        RetryUtils.ensureUrlAccessible(imageUrl, testName = "remote image preflight")

        val prompt = prompt("url-based-attachments-test") {
            system("You are a helpful assistant that can analyze images.")

            user {
                markdown {
                    +"I'm sending you an image from a URL. Please analyze it and tell me about its content."
                }

                image(imageUrl)
            }
        }

        withRetry {
            with(getExecutor(model).execute(prompt, model)) {
                checkImageAnalysisResponse(this)
            }
        }
    }

    open fun integration_testStructuredOutputNative(model: LLModel) = runTest {
        assumeTrue(
            model.supports(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            val executor = getExecutor(model)

            with(
                getExecutor(model).executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getNativeConfig(executor.getStandardJsonSchemaGenerator(model))
                )
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) = runTest {
        assumeTrue(
            model.supports(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            val executor = getExecutor(model)
            with(
                executor.executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getNativeConfig(executor.getStandardJsonSchemaGenerator(model)),
                    fixingParser = getFixingParser(model),
                ),
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testStructuredOutputManual(model: LLModel) = runTest {
        assumeTrue(
            model.provider !== LLMProvider.Google,
            "Google models fail to return manually requested structured output without fixing"
        )
        if (model.provider == LLMProvider.OpenRouter) {
            assumeTrue(
                model.id.contains("gemini"),
                "Google models fail to return manually requested structured output without fixing"
            )
        }

        withRetry {
            val executor = getExecutor(model)
            with(
                executor.executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getManualConfig(executor.getStandardJsonSchemaGenerator(model))
                )
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testStructuredOutputManualWithFixingParser(model: LLModel) = runTest {
        assumeFalse(
            (model.id.contains("flash-lite")),
            "Gemini Flash Lite models fail to return manually requested structured output"
        )

        withRetry(6) {
            val executor = getExecutor(model)
            with(
                executor.executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getManualConfig(executor.getStandardJsonSchemaGenerator(model)),
                    fixingParser = getFixingParser(model)
                )
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testToolChoiceRequired(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val prompt = calculatorPrompt

        /* tool choice auto is default and thus is tested by [integration_testToolWithRequiredParams] */

        withRetry(times = 3, testName = "integration_testToolChoiceRequired[${model.id}]") {
            with(
                getLLMClient(model).execute(
                    prompt.withParams(
                        prompt.params.copy(
                            toolChoice = ToolChoice.Required
                        )
                    ),
                    model,
                    listOf(CalculatorTool.descriptor)
                )
            ) {
                assertResponseContainsToolCall(this, CalculatorTool.descriptor.name)
            }
        }
    }

    open fun integration_testToolChoiceNone(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.provider != LLMProvider.Bedrock, "Bedrock API doesn't support 'none' tool choice.")
        assumeTrue(model.supports(LLMCapability.ToolChoice), "Model $model does not support tool choice")
        assumeTrue(
            model.provider != LLMProvider.MistralAI,
            "MistralAI returns json array which we are failing to parse. Remove after KG-535 fix"
        )

        val prompt = Prompt.build("test-calculator-tool") {
            system("You are a helpful assistant.")
            user("What is 2 + 2?")
        }

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            with(
                getLLMClient(model).execute(
                    prompt.withParams(
                        prompt.params.copy(
                            toolChoice = ToolChoice.None
                        )
                    ),
                    model,
                    listOf(CalculatorTool.descriptor)
                )
            ) {
                parts.shouldNotContainAnyOf(MessagePart.Tool.Call)
            }
        }
    }

    open fun integration_testToolChoiceNamed(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(
            model.supports(LLMCapability.ToolChoice),
            "Model $model does not support tool choice"
        )

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        val prompt = calculatorPrompt

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            with(
                getLLMClient(model).execute(
                    prompt.withParams(
                        prompt.params.copy(
                            toolChoice = ToolChoice.Named(nothingTool.name)
                        )
                    ),
                    model,
                    listOf(CalculatorTool.descriptor, nothingTool)
                )
            ) {
                assertResponseContainsToolCall(this, nothingTool.name)
            }
        }
    }

    open fun integration_testEmbed(model: LLModel) = runTest {
        val client = getLLMClient(model)
        val testText = "integration test embedding"
        client.embed(testText, model) shouldNotBeNull {
            shouldNotBeEmpty()
            size shouldBeGreaterThan 100
            shouldForAll {
                it.isFinite()
            }
        }
    }

    open fun integration_testEmbedBatch(model: LLModel) = runTest {
        val client = getLLMClient(model)
        val inputs = listOf(
            "integration test batch embedding first",
            "integration test batch embedding second",
            "integration test batch embedding third",
        )
        val embeddings = client.embed(inputs, model)
        embeddings shouldNotBeNull {
            size shouldBe inputs.size
            shouldForAll { embedding ->
                embedding.shouldNotBeEmpty()
                embedding.size shouldBeGreaterThan 100
                embedding.shouldForAll { it.isFinite() }
            }
        }
    }

    open fun integration_testMultipleSystemMessages(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = prompt("multiple-system-messages-test", createNoReasoningParams(model)) {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }
        with(getLLMClient(model).execute(prompt, model)) {
            parts.shouldForAny { it is MessagePart.Text }
        }
    }

    open fun integration_testSingleMessageModeration(model: LLModel) = runTest(timeout = 300.seconds) {
        // For Bedrock, moderation is done via guardrails at the client level, not model capabilities
        assumeTrue(
            model.provider == LLMProvider.Bedrock || model.supports(LLMCapability.Moderation),
            "Model $model does not support moderation"
        )

        // KG-560 Bedrock models have guardrail configuration issues
        assumeTrue(
            model.id != "us.anthropic.claude-haiku-4-5-20251001-v1:0",
            "Bedrock Claude Haiku model has guardrail configuration issues"
        )
        val client = getLLMClient(model)

        val prompt = prompt("test-harmful-content") {
            user(String(Base64.getDecoder().decode("SG93IGNhbiBJIGhhcm0gc29tZW9uZT8="))) // For testing purposes
        }

        withClue("Violence or crime must be detected!") {
            with(client.moderate(prompt = prompt, model = model)) {
                isHarmful.shouldBeTrue()
                violatesOneOf(
                    ModerationCategory.Illicit,
                    ModerationCategory.IllicitViolent,
                    ModerationCategory.Violence
                ).shouldBeTrue()
            }
        }
    }

    open fun integration_testMultipleMessagesModeration(model: LLModel) = runTest(timeout = 300.seconds) {
        // For Bedrock, moderation is done via guardrails at the client level, not model capabilities
        assumeTrue(
            model.provider == LLMProvider.Bedrock || model.supports(LLMCapability.Moderation),
            "Model $model does not support moderation"
        )

        // KG-560 Bedrock models have guardrail configuration issues
        assumeTrue(
            model.id != "us.anthropic.claude-haiku-4-5-20251001-v1:0",
            "Bedrock Claude Haiku model has guardrail configuration issues"
        )
        val client = getLLMClient(model)

        // Not harmful (without the answer)
        val questionOnly = prompt("test-legit-question") {
            user(
                String(
                    Base64.getDecoder()
                        .decode(
                            "RG8ndCB5b3UgdGhpbmsgcGVvcGxlIGZyb20gdGhlIHBsYW5ldCBNYXJzIGFsc28gZGVzZXJ2ZSB0byBiZSBhbGl2ZSBhbmQgYmUgcmVzcGVjdGVkPw=="
                        )
                )
            ) // for testing purposes
        }
        // Not harmful (without the question in the context)
        val answerOnly = prompt("test-legit-answer") {
            assistant(
                String(
                    Base64.getDecoder().decode("SSB0aGluayB0aGV5IGRvbid0IGRlc2VydmUgaXQgYXQgYWxsIQ==")
                )
            ) // for testing only
        }

        // Harmful and offensive (question + answer together in the same context)
        val promptWithMultipleMessages = prompt("test") {
            user(
                String(
                    Base64.getDecoder()
                        .decode(
                            "RG8ndCB5b3UgdGhpbmsgcGVvcGxlIGZyb20gdGhlIHBsYW5ldCBNYXJzIGFsc28gZGVzZXJ2ZSB0byBiZSBhbGl2ZSBhbmQgYmUgcmVzcGVjdGVkPw=="
                        )
                )
            ) // for testing purposes
            assistant(
                String(
                    Base64.getDecoder().decode("SSB0aGluayB0aGV5IGRvbid0IGRlc2VydmUgaXQgYXQgYWxsIQ==")
                )
            ) // for testing only
        }

        withClue("Question only should not be detected as harmful!") {
            client.moderate(
                prompt = questionOnly,
                model = model
            ).isHarmful.shouldNotBeTrue()
        }

        withClue("Answer only should not be detected as harmful!") {
            client.moderate(prompt = answerOnly, model = model).isHarmful.shouldNotBeTrue()
        }

        withClue("Question + answer should be detected as harmful!") {
            client.moderate(
                prompt = promptWithMultipleMessages,
                model = model
            ).isHarmful.shouldBeTrue()
        }
    }

    open fun integration_testGetModels(provider: LLMProvider): Unit = runBlocking {
        withClue("Models list should not be empty") {
            getLLMClientForProvider(provider).models().shouldNotBeEmpty()
        }
    }

    open fun integration_testReasoningCapability(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val params = createReasoningParams(model)
        val prompt = Prompt.build("reasoning-test", params = params) {
            system("You are a helpful assistant.")
            user("Think about this step by step: What is 15 * 23 + 8?")
        }

        withRetry(times = 3, testName = "integration_testReasoningCapability[${model.id}]") {
            getLLMClient(model).execute(prompt, model) shouldNotBeNull {
                withClue("No reasoning messages found") { parts.shouldForAny { it is MessagePart.Reasoning } }
                // Some Google models aren't providing meta info
                assertResponseContainsReasoning(this, model.provider != LLMProvider.Google)
            }
        }
    }

    open fun integration_testReasoningWithEncryption(model: LLModel) = runTest(timeout = 300.seconds) {
        with(model.provider) {
            Models.assumeAvailable(this)
            assumeTrue(
                this != LLMProvider.Bedrock,
                "Bedrock API doesn't support thinking budget parameters required for reasoning encryption"
            )
            assumeTrue(
                this != LLMProvider.Google,
                "Google API doesn't consistently return encrypted thoughtSignature values"
            )
        }

        val params = createReasoningParams(model)
        val prompt = Prompt.build("reasoning-encryption-test", params = params) {
            system("You are a helpful assistant. Think carefully about the problem.")
            user("Solve this problem step by step: A train travels at 60 mph for 2 hours, then 80 mph for 1.5 hours. What is the total distance?")
        }

        withRetry(times = 3, testName = "integration_testReasoningWithEncryption[${model.id}]") {
            getLLMClient(model).execute(prompt, model) shouldNotBeNull {
                withClue("No reasoning messages found") { parts.shouldForAny { it is MessagePart.Reasoning } }
                assertResponseContainsReasoningWithEncryption(this)
            }
        }
    }

    // This test targets models that support/require passing reasoning back (Google Gemini 3)
    open fun integration_testReasoningMultiStep(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val params = createReasoningParams(model)
        val prompt1 = Prompt.build("reasoning-multistep-1", params = params) {
            system("You are a helpful assistant.")
            user("What is 5 + 5? Think step by step.")
        }

        val client = getLLMClient(model)

        val response1 = withRetry(times = 3, testName = "integration_testReasoningMultiStep_Turn1[${model.id}]") {
            client.execute(prompt1, model)
        }

        response1.parts.shouldForAny { it is MessagePart.Reasoning }

        val prompt2 = Prompt(
            id = "reasoning-multistep-2",
            messages = prompt1.messages + response1 + Message.User(
                MessagePart.Text("Multiply the result by 2."),
                metaInfo = RequestMetaInfo.Empty
            ),
            params = params
        )

        withRetry(times = 3, testName = "integration_testReasoningMultiStep_Turn2[${model.id}]") {
            val response2 = client.execute(prompt2, model)
            val answer = response2.parts.map {
                when (it) {
                    is MessagePart.Text -> it.text
                    is MessagePart.Reasoning -> it.content
                    else -> ""
                }
            }.joinToString("")
            answer.shouldContain("20")
        }
    }

    open fun integration_testReasoningPreservationRoundTrip(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val params = createReasoningParams(model)
        val prompt1 = Prompt.build("reasoning-preservation-roundtrip-1", params = params) {
            system("You are a helpful assistant. Think step by step.")
            user("What is 11 + 9? Think step by step, then answer.")
        }

        val response1 =
            withRetry(times = 3, testName = "integration_testReasoningPreservationRoundTrip_Turn1[${model.id}]") {
                getLLMClient(model).execute(prompt1, model)
            }

        assertResponseContainsReasoning(response1, model.provider != LLMProvider.Google)

        val prompt2 = Prompt(
            id = "reasoning-preservation-roundtrip-2",
            messages = prompt1.messages + response1 + Message.User(
                MessagePart.Text("Now multiply that result by 2. Answer with the number."),
                metaInfo = RequestMetaInfo.Empty
            ),
            params = params
        )

        withRetry(times = 3, testName = "integration_testReasoningPreservationRoundTrip_Turn2[${model.id}]") {
            val response2 = getLLMClient(model).execute(prompt2, model)
            response2.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.shouldContain("40")
            }
        }
    }

    open fun integration_testEncryptedReasoningRoundTrip(model: LLModel) = runTest(timeout = 300.seconds) {
        with(model.provider) {
            Models.assumeAvailable(this)
            assumeTrue(
                this != LLMProvider.Bedrock,
                "Bedrock API doesn't support thinking budget parameters required for reasoning encryption"
            )
            assumeTrue(
                this != LLMProvider.Google,
                "Google API doesn't consistently return encrypted thoughtSignature values"
            )
        }

        val params = createReasoningParams(model)
        val prompt1 = Prompt.build("encrypted-reasoning-roundtrip-1", params = params) {
            system("You are a helpful assistant. Think carefully about the problem.")
            user("What is 8 * 9? Think step by step, then answer.")
        }

        val response1 =
            withRetry(times = 3, testName = "integration_testEncryptedReasoningRoundTrip_Turn1[${model.id}]") {
                getLLMClient(model).execute(prompt1, model)
            }

        assertResponseContainsReasoningWithEncryption(response1)

        val prompt2 = Prompt(
            id = "encrypted-reasoning-roundtrip-2",
            messages = prompt1.messages + response1 + Message.User(
                MessagePart.Text("Now add 1 to that result. Answer with the number."),
                metaInfo = RequestMetaInfo.Empty
            ),
            params = params
        )

        withRetry(times = 3, testName = "integration_testEncryptedReasoningRoundTrip_Turn2[${model.id}]") {
            val response2 = getLLMClient(model).execute(prompt2, model)
            response2.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.shouldContain("73")
            }
        }
    }

    open fun integration_testAttachmentTextRoundTrip(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(
            model.supports(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageFile = MediaTestUtils.getImageFileForScenario(ImageTestScenario.BASIC_PNG, testResourcesDir)
        val prompt1 = prompt("attachment-text-roundtrip-1", params = createNoReasoningParams(model)) {
            system("You are a helpful assistant that can analyze images.")
            user {
                text("Look at this image. Identify its visible content in one short sentence.")
                image(KtPath(imageFile.pathString))
            }
        }

        val response1 = withRetry(times = 3, testName = "integration_testAttachmentTextRoundTrip_Turn1[${model.id}]") {
            getExecutor(model).execute(prompt1, model)
        }

        checkExecutorMediaResponse(response1)
        assertResponseDoesNotLeakRequestAttachments(response1)

        val prompt2 = Prompt(
            id = "attachment-text-roundtrip-2",
            messages = prompt1.messages + response1 + Message.User(
                MessagePart.Text("Based on the image you just saw, name one color visible in it."),
                metaInfo = RequestMetaInfo.Empty
            ),
            params = createNoReasoningParams(model)
        )

        withRetry(times = 3, testName = "integration_testAttachmentTextRoundTrip_Turn2[${model.id}]") {
            val response2 = getExecutor(model).execute(prompt2, model)
            response2.parts.filterIsInstance<MessagePart.Text>().firstOrNull().shouldNotBeNull {
                text.shouldNotBeBlank()
            }
            assertResponseDoesNotLeakRequestAttachments(response2)
        }
    }

    open fun integration_testReasoningStreamingSummaryDeltas(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val params = createReasoningParams(model)

        val prompt = Prompt.build("reasoning-streaming-test", params = params) {
            system("You are a helpful assistant.")
            user("Reason about what is 8 * 9?. Include summary.")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testReasoningStreamingSummaryDeltas[${model.id}]") {
            val reasoningDeltaFrames = mutableListOf<StreamFrame.ReasoningDelta>()
            val reasoningCompleteFrames = mutableListOf<StreamFrame.ReasoningComplete>()
            val textDeltaFrames = mutableListOf<StreamFrame.TextDelta>()
            val endFrames = mutableListOf<StreamFrame.End>()

            executor.executeStreamAndCollect(
                prompt = prompt,
                model = model,
                reasoningDeltaFrames = reasoningDeltaFrames,
                reasoningCompleteFrames = reasoningCompleteFrames,
                textDeltaFrames = textDeltaFrames,
                endFrame = endFrames
            )

            reasoningDeltaFrames.shouldNotBeEmpty()

            val reasoningText = reasoningDeltaFrames.mapNotNull { it.text }.joinToString("")
            val reasoningSummary = reasoningDeltaFrames.mapNotNull { it.summary }.joinToString("")
            (reasoningText + reasoningSummary).length shouldBeGreaterThan 0

            val finalAnswer = textDeltaFrames.joinToString("") { it.text }
            finalAnswer.shouldContain("72")
        }
    }

    open fun integration_testReasoningStreamingWithEncryptedContent(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(
            model.provider == LLMProvider.OpenAI,
            "This test is specific to OpenAI Responses API encrypted reasoning in stateless mode"
        )

        val params = createReasoningParams(model)
        val prompt = Prompt.build("reasoning-streaming-encryption-test", params = params) {
            system("You are a helpful assistant.")
            user("Think about this step by step: What is 8 * 9?")
        }

        val executor = getExecutor(model)

        withRetry(times = 3, testName = "integration_testReasoningStreamingWithEncryptedContent[${model.id}]") {
            val reasoningCompleteFrames = mutableListOf<StreamFrame.ReasoningComplete>()
            val textDeltaFrames = mutableListOf<StreamFrame.TextDelta>()

            executor.executeStreamAndCollect(
                prompt = prompt,
                model = model,
                reasoningCompleteFrames = reasoningCompleteFrames,
                textDeltaFrames = textDeltaFrames
            )

            reasoningCompleteFrames.shouldNotBeEmpty()
            val reasoningComplete = reasoningCompleteFrames.first()
            reasoningComplete.encrypted.shouldNotBeNull()
            reasoningComplete.encrypted!!.length shouldBeGreaterThan 0

            val finalAnswer = textDeltaFrames.joinToString("") { it.text }
            finalAnswer.shouldContain("72")
        }
    }

    open fun integration_testExecuteStreamingWithTools(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(
            model != GoogleModels.Gemini3_1Pro_Preview,
            "KG-768 GoogleLLMClient.executeStreaming() may hang because the stream never completes with End frame"
        )

        val executor = getExecutor(model)
        val params = when (model.provider) {
            LLMProvider.OpenAI ->
                if (model.supports(LLMCapability.OpenAIEndpoint.Responses)) {
                    OpenAIResponsesParams(toolChoice = ToolChoice.Required)
                } else {
                    OpenAIChatParams(toolChoice = ToolChoice.Required)
                }

            else -> LLMParams(toolChoice = ToolChoice.Required)
        }

        val prompt = Prompt.build("test-streaming", params) {
            system("You are a helpful assistant.")
            user("Count three times five")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreamingWithTools[${model.id}]") {
            val textDeltaFrames = mutableListOf<StreamFrame.TextDelta>()
            val toolDeltaFrames = mutableListOf<StreamFrame.ToolCallDelta>()
            val toolCompleteFrames = mutableListOf<StreamFrame.ToolCallComplete>()

            executor.executeStreamAndCollect(
                prompt = prompt,
                model = model,
                tools = listOf(SimpleCalculatorTool.descriptor),
                textDeltaFrames = textDeltaFrames,
                toolDeltaFrames = toolDeltaFrames,
                toolCompleteFrames = toolCompleteFrames,
            )

            toolDeltaFrames.shouldNotBeEmpty()

            withClue("Expected calculator tool call but got: [$toolCompleteFrames]") {
                toolCompleteFrames.any {
                    it.name == SimpleCalculatorTool.name
                } shouldBe true
            }
        }
    }
}

private suspend fun PromptExecutor.executeStreamAndCollect(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor> = emptyList(),
    textDeltaFrames: MutableList<StreamFrame.TextDelta> = mutableListOf(),
    textCompleteFrames: MutableList<StreamFrame.TextComplete> = mutableListOf(),
    toolDeltaFrames: MutableList<StreamFrame.ToolCallDelta> = mutableListOf(),
    toolCompleteFrames: MutableList<StreamFrame.ToolCallComplete> = mutableListOf(),
    reasoningDeltaFrames: MutableList<StreamFrame.ReasoningDelta> = mutableListOf(),
    reasoningCompleteFrames: MutableList<StreamFrame.ReasoningComplete> = mutableListOf(),
    endFrame: MutableList<StreamFrame.End> = mutableListOf(),
) {
    this.executeStreaming(prompt, model, tools).collect { frame ->
        when (frame) {
            is StreamFrame.DeltaFrame -> {
                when (val delta: StreamFrame.DeltaFrame = frame) {
                    is StreamFrame.TextDelta -> textDeltaFrames.add(delta)
                    is StreamFrame.ToolCallDelta -> toolDeltaFrames.add(delta)
                    is StreamFrame.ReasoningDelta -> reasoningDeltaFrames.add(delta)
                }
            }

            is StreamFrame.CompleteFrame -> {
                when (val complete: StreamFrame.CompleteFrame = frame) {
                    is StreamFrame.TextComplete -> textCompleteFrames.add(complete)
                    is StreamFrame.ToolCallComplete -> toolCompleteFrames.add(complete)
                    is StreamFrame.ReasoningComplete -> reasoningCompleteFrames.add(complete)
                }
            }

            is StreamFrame.End -> {
                endFrame.add(frame)
            }
        }
    }
}
