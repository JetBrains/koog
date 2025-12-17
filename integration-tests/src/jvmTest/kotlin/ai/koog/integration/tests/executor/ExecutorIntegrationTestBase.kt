package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsReasoning
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsReasoningWithEncryption
import ai.koog.integration.tests.utils.TestUtils.assertResponseContainsToolCall
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.integration.tests.utils.structuredOutput.Country
import ai.koog.integration.tests.utils.structuredOutput.checkWeatherStructuredOutputResponse
import ai.koog.integration.tests.utils.structuredOutput.countryStructuredOutputPrompt
import ai.koog.integration.tests.utils.structuredOutput.getConfigFixingParserManual
import ai.koog.integration.tests.utils.structuredOutput.getConfigFixingParserNative
import ai.koog.integration.tests.utils.structuredOutput.getConfigNoFixingParserManual
import ai.koog.integration.tests.utils.structuredOutput.getConfigNoFixingParserNative
import ai.koog.integration.tests.utils.structuredOutput.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.structuredOutput.weatherStructuredOutputPrompt
import ai.koog.integration.tests.utils.tools.CalculatorOperation
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
import ai.koog.prompt.dsl.ModerationCategory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.executeStructured
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
import java.util.*
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

abstract class ExecutorIntegrationTestBase {
    private val testScope = TestScope()

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

    private fun createReasoningParams(model: LLModel): LLMParams {
        return when (model.provider) {
            is LLMProvider.Anthropic -> AnthropicParams(
                thinking = AnthropicThinking.Enabled(budgetTokens = 1024)
            )

            is LLMProvider.OpenAI -> OpenAIResponsesParams(
                reasoning = ReasoningConfig(
                    effort = ReasoningEffort.MEDIUM,
                    summary = ReasoningSummary.DETAILED
                ),
                include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
                maxTokens = 256
            )

            is LLMProvider.Google -> {
                val thinkingConfig = if (model.id == GoogleModels.Gemini3_Pro_Preview.id) {
                    GoogleThinkingConfig(
                        includeThoughts = true,
                        thinkingLevel = GoogleThinkingLevel.LOW // with HIGH thoughts often exceed maxTokens causing test failures
                    )
                } else {
                    GoogleThinkingConfig(
                        includeThoughts = true,
                        thinkingBudget = 256
                    )
                }
                GoogleParams(
                    thinkingConfig = thinkingConfig,
                    maxTokens = 256
                )
            }

            else -> LLMParams(maxTokens = 256)
        }
    }

    open fun integration_testExecute(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            getExecutor(model).execute(prompt, model) shouldNotBeNull {
                shouldNotBeEmpty()
                with(shouldForAny { it is Message.Assistant }.first()) {
                    content.lowercase().shouldContain("paris")
                    with(metaInfo) {
                        inputTokensCount.shouldNotBeNull()
                        outputTokensCount.shouldNotBeNull()
                        totalTokensCount.shouldNotBeNull()
                    }
                }
            }
        }
    }

    open fun integration_testExecuteStreaming(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val executor = getExecutor(model)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            with(StringBuilder()) {
                val endMessages = mutableListOf<StreamFrame.End>()
                val toolMessages = mutableListOf<StreamFrame.ToolCall>()

                executor.executeStreamAndCollect(
                    prompt = prompt,
                    model = model,
                    appendable = this,
                    endMessages = endMessages,
                    toolMessages = toolMessages
                )

                length shouldNotBe (0)
                toolMessages.shouldBeEmpty()
                when (model.provider) {
                    is LLMProvider.Ollama -> endMessages.size shouldBe 0
                    else -> endMessages.size shouldBe 1
                }

                toString() shouldNotBeNull {
                    shouldContain("1")
                    shouldContain("2")
                    shouldContain("3")
                    shouldContain("4")
                    shouldContain("5")
                }
            }
        }
    }

    open fun integration_testExecuteStreamingWithTools(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = getExecutor(model)

        val prompt = Prompt.build("test-streaming", LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant.")
            user("Count three times five")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreamingWithTools[${model.id}]") {
            with(StringBuilder()) {
                val endMessages = mutableListOf<StreamFrame.End>()
                val toolMessages = mutableListOf<StreamFrame.ToolCall>()

                executor.executeStreamAndCollect(
                    prompt = prompt,
                    model = model,
                    tools = listOf(SimpleCalculatorTool.descriptor),
                    appendable = this,
                    endMessages = endMessages,
                    toolMessages = toolMessages
                )

                toolMessages.shouldNotBeEmpty()
                withClue("Expected calculator tool call but got: [$toolMessages]") {
                    toolMessages.any {
                        it.name == SimpleCalculatorTool.name &&
                            it.content.contains(CalculatorOperation.MULTIPLY.name, ignoreCase = true)
                    } shouldBe true
                }
            }
        }
    }

    open fun integration_testToolWithRequiredParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithRequiredParams[${model.id}]") {
            with(getExecutor(model).execute(calculatorPrompt, model, listOf(CalculatorTool.descriptor))) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithNotRequiredOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithNotRequiredOptionalParams[${model.id}]") {
            with(
                getExecutor(model).execute(
                    calculatorPromptNotRequiredOptionalParams,
                    model,
                    listOf(calculatorToolDescriptorOptionalParams)
                )
            ) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithOptionalParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        withRetry(times = 3, testName = "integration_testToolWithOptionalParams[${model.id}]") {
            with(getExecutor(model).execute(calculatorPrompt, model, listOf(calculatorToolDescriptorOptionalParams))) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, CalculatorTool.name)
            }
        }
    }

    open fun integration_testToolWithNoParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Picker random color for me!")
        }

        withRetry(times = 3, testName = "integration_testToolWithNoParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PickColorTool.descriptor))) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, PickColorTool.name)
            }
        }
    }

    open fun integration_testToolWithListEnumParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with access to a color picker tool. "
                +"ALWAYS CALL TOOL!!!"
            }
            user("Pick me a color from red, green, orange!")
        }

        withRetry(times = 3, testName = "integration_testToolWithListEnumParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PickColorFromListTool.descriptor))) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, PickColorFromListTool.name)
            }
        }
    }

    open fun integration_testToolWithNestedListParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools") {
            system {
                +"You are a helpful assistant with lottery tool. You MUST always call tools!!!"
            }
            user("Select winners from lottery tickets [10, 42, 43, 51, 22] and [34, 12, 4, 53, 99]")
        }

        withRetry(times = 3, testName = "integration_testToolWithNestedListParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(LotteryTool.descriptor))) {
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, LotteryTool.name)
            }
        }
    }

    open fun integration_testToolsWithNullParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")
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
                shouldNotBeEmpty()
                first { it is Message.Tool.Call }.content.shouldContain("null")
            }
        }
    }

    open fun integration_testToolsWithAnyOfParams(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider != LLMProvider.Anthropic, "Anthropic does not support anyOf")
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = Prompt.build("test-tools", LLMParams(toolChoice = ToolChoice.Required)) {
            system {
                +"You are a helpful assistant with tokens price calculator tool."
                +"JUST CALL TOOLS. NO QUESTIONS ASKED."
            }
            user("Calculate price of 10 tokens if I pay 0.003 euro for token with 10% discount.")
        }

        withRetry(testName = "integration_testToolsWithAnyOfParams[${model.id}]") {
            with(getExecutor(model).execute(prompt, model, listOf(PriceCalculatorTool.descriptor))) {
                shouldNotBeEmpty()
                shouldForAny { it is Message.Tool.Call }
                assertResponseContainsToolCall(this, PriceCalculatorTool.name)
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

                    if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
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
                            .filterIsInstance<Message.Assistant>()
                            .toSingleMessage()
                    ) {
                        when (scenario) {
                            MarkdownTestScenario.MALFORMED_SYNTAX, MarkdownTestScenario.MATH_NOTATION, MarkdownTestScenario.BROKEN_LINKS, MarkdownTestScenario.IRREGULAR_TABLES -> {
                                checkResponseBasic(this)
                            }

                            else -> {
                                checkExecutorMediaResponse(this)
                            }
                        }
                    }
                } catch (e: Exception) {
                    when (scenario) {
                        MarkdownTestScenario.EMPTY_MARKDOWN -> {
                            when (model.provider) {
                                LLMProvider.Google -> {
                                    println("Expected exception for ${scenario.name.lowercase()} image: ${e.message}")
                                }
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    open fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.capabilities.contains(LLMCapability.Vision.Image),
                "Model must support vision capability"
            )

            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please analyze it and identify the image format if possible."
                    }

                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE, ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                            image(
                                ContentPart.Image(
                                    content = AttachmentContent.Binary.Bytes(imageFile.readBytes()),
                                    format = "jpg",
                                    mimeType = "image/jpeg"
                                )
                            )
                        }

                        else -> {
                            image(KtPath(imageFile.pathString))
                        }
                    }
                }
            }

            withRetry {
                try {
                    checkExecutorMediaResponse(getExecutor(model).execute(prompt, model).single())
                } catch (e: LLMClientException) {
                    // For some edge cases, exceptions are expected
                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE_ANTHROPIC, ImageTestScenario.LARGE_IMAGE -> {
                            val message = e.message.shouldNotBeNull()

                            message.shouldContain("Status code: 400")
                            message.shouldContain("image exceeds")
                        }

                        ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                            val message = e.message.shouldNotBeNull()

                            message.shouldContain("Status code: 400")
                            if (model.provider == LLMProvider.Anthropic) {
                                message.shouldContain("Could not process image")
                            } else if (model.provider == LLMProvider.OpenAI) {
                                message.shouldContain("You uploaded an unsupported image. Please make sure your image is valid.")
                            }
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
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
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
                    val response = getExecutor(model).execute(prompt, model).single()
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

                        TextTestScenario.LONG_TEXT_5_MB -> {
                            if (model.provider == LLMProvider.Anthropic) {
                                val message = e.message.shouldNotBeNull()
                                message.shouldContain("Status code: 400")
                                message.shouldContain("prompt is too long")
                            } else if (model.provider == LLMProvider.Google) {
                                throw e
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
                model.capabilities.contains(LLMCapability.Audio),
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
                    checkExecutorMediaResponse(getExecutor(model).execute(prompt, model).single())
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
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageFile = MediaTestUtils.getImageFileForScenario(ImageTestScenario.BASIC_PNG, testResourcesDir)
        val imageBytes = imageFile.readBytes()

        val tempImageFile = testResourcesDir.resolve("small.png")

        tempImageFile.writeBytes(imageBytes)
        val prompt = prompt("base64-encoded-attachments-test") {
            system("You are a helpful assistant that can analyze different types of media files.")

            user {
                markdown {
                    +"I'm sending you an image. Please analyze them and tell me about their content."
                }

                image(KtPath(tempImageFile.pathString))
            }
        }

        withRetry {
            with(getExecutor(model).execute(prompt, model).single()) {
                checkExecutorMediaResponse(this)
                content.shouldContain("image")
            }
        }
    }

    open fun integration_testUrlBasedAttachment(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider !== LLMProvider.Google, "Google models do not support URL attachments")

        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        val imageUrl =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Python-logo-notext.svg/1200px-Python-logo-notext.svg.png"

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
            with(getExecutor(model).execute(prompt, model).single()) {
                checkExecutorMediaResponse(this)
                content.lowercase()
                    .shouldContain("python")
                    .shouldContain("logo")
            }
        }
    }

    open fun integration_testStructuredOutputNative(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            with(
                getExecutor(model).executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getConfigNoFixingParserNative(model)
                )
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )

        withRetry {
            with(
                getExecutor(model).executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getConfigFixingParserNative(model)
                )
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
            with(
                getExecutor(model).executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getConfigNoFixingParserManual(model)
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
            with(
                getExecutor(model).executeStructured(
                    prompt = weatherStructuredOutputPrompt,
                    model = model,
                    config = getConfigFixingParserManual(model)
                )
            ) {
                isSuccess.shouldBeTrue()
                checkWeatherStructuredOutputResponse(this)
            }
        }
    }

    open fun integration_testToolChoiceRequired(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")

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
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, CalculatorTool.descriptor.name)
            }
        }
    }

    open fun integration_testToolChoiceNone(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(model.provider != LLMProvider.Bedrock, "Bedrock API doesn't support 'none' tool choice.")
        assumeTrue(LLMCapability.ToolChoice in model.capabilities, "Model $model does not support tool choice")
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
                shouldNotBeEmpty()
                shouldNotContainAnyOf(Message.Tool.Call)
            }
        }
    }

    open fun integration_testToolChoiceNamed(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        assumeTrue(
            model.capabilities.contains(LLMCapability.ToolChoice),
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
                shouldNotBeEmpty()
                assertResponseContainsToolCall(this, nothingTool.name)
            }
        }
    }

    open fun integration_testEmbed(model: LLModel) = runTest {
        val client = getLLMClient(model)
        if (client !is LLMEmbeddingProvider) {
            return@runTest
        }
        val testText = "integration test embedding"
        client.embed(testText, model) shouldNotBeNull {
            shouldNotBeEmpty()
            size shouldBeGreaterThan 100
            shouldForAll {
                it.isFinite()
            }
        }
    }

    open fun integration_testMultipleSystemMessages(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val prompt = prompt("multiple-system-messages-test") {
            system("You are a helpful assistant.")
            user("Hi")
            system("You can handle multiple system messages.")
            user("Respond with a short message.")
        }
        getLLMClient(model).execute(prompt, model).single().role shouldBe Message.Role.Assistant
    }

    open fun integration_testSingleMessageModeration(model: LLModel) = runTest(timeout = 300.seconds) {
        // For Bedrock, moderation is done via guardrails at the client level, not model capabilities
        assumeTrue(
            model.provider == LLMProvider.Bedrock || model.capabilities.contains(LLMCapability.Moderation),
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
            model.provider == LLMProvider.Bedrock || model.capabilities.contains(LLMCapability.Moderation),
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

    private fun List<Message.Assistant>.toSingleMessage(): Message.Assistant =
        Message.Assistant(parts = flatMap { it.parts }, metaInfo = ResponseMetaInfo.Empty)

    open fun integration_testReasoningCapability(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)

        val params = createReasoningParams(model)
        val prompt = Prompt.build("reasoning-test", params = params) {
            system("You are a helpful assistant.")
            user("Think about this step by step: What is 15 * 23 + 8?")
        }

        withRetry(times = 3, testName = "integration_testReasoningCapability[${model.id}]") {
            getLLMClient(model).execute(prompt, model) shouldNotBeNull {
                shouldNotBeEmpty()
                withClue("No reasoning messages found") { shouldForAny { it is Message.Reasoning } }
                assertResponseContainsReasoning(this)
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
                shouldNotBeEmpty()
                withClue("No reasoning messages found") { shouldForAny { it is Message.Reasoning } }
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

        response1.shouldForAny { it is Message.Reasoning }

        val prompt2 = Prompt(
            id = "reasoning-multistep-2",
            messages = prompt1.messages + response1 + Message.User(
                ContentPart.Text("Multiply the result by 2."),
                metaInfo = RequestMetaInfo.Empty
            ),
            params = params
        )

        withRetry(times = 3, testName = "integration_testReasoningMultiStep_Turn2[${model.id}]") {
            val response2 = client.execute(prompt2, model)
            response2.shouldNotBeEmpty()
            val answer = response2.filterIsInstance<Message.Assistant>().first().content
            answer.shouldContain("20")
        }
    }

    open fun integration_testExecuteStreamingWithTools(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = getExecutor(model)

        val prompt = Prompt.build("test-streaming", LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant.")
            user("Count three times five")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreamingWithTools[${model.id}]") {
            with(StringBuilder()) {
                val endMessages = mutableListOf<StreamFrame.End>()
                val toolMessages = mutableListOf<StreamFrame.ToolCall>()

                executor.executeStreamAndCollect(
                    prompt = prompt,
                    model = model,
                    tools = listOf(SimpleCalculatorTool.descriptor),
                    appendable = this,
                    endMessages = endMessages,
                    toolMessages = toolMessages
                )

                toolMessages.shouldNotBeEmpty()
                withClue("Expected calculator tool call but got: [$toolMessages]") {
                    toolMessages.any {
                        it.name == SimpleCalculatorTool.name &&
                            it.content.contains(CalculatorOperation.MULTIPLY.name, ignoreCase = true)
                    } shouldBe true
                }
            }
        }
    }

    private suspend fun PromptExecutor.executeStreamAndCollect(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        appendable: StringBuilder,
        endMessages: MutableList<StreamFrame.End>,
        toolMessages: MutableList<StreamFrame.ToolCall>
    ) {
        executeStreaming(prompt, model, tools).collect { frame ->
            when (frame) {
                is StreamFrame.Append -> appendable.append(frame.text)
                is StreamFrame.ToolCall -> toolMessages.add(frame)
                is StreamFrame.End -> endMessages.add(frame)
            }
        }
    }
}

private suspend fun PromptExecutor.executeStreamAndCollect(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor> = emptyList(),
    appendable: Appendable,
    endMessages: MutableList<StreamFrame.End>,
    toolMessages: MutableList<StreamFrame.ToolCall>
) {
    this.executeStreaming(prompt, model, tools).collect { frame ->
        when (frame) {
            is StreamFrame.Append -> appendable.append(frame.text)
            is StreamFrame.End -> endMessages.add(frame)
            is StreamFrame.ToolCall -> toolMessages.add(frame)
        }
    }
}
