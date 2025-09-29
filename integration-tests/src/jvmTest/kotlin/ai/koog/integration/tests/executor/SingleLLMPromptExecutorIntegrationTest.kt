package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.MediaTestScenarios
import ai.koog.integration.tests.utils.MediaTestScenarios.AudioTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.ImageTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.MarkdownTestScenario
import ai.koog.integration.tests.utils.MediaTestScenarios.TextTestScenario
import ai.koog.integration.tests.utils.MediaTestUtils
import ai.koog.integration.tests.utils.MediaTestUtils.checkExecutorMediaResponse
import ai.koog.integration.tests.utils.MediaTestUtils.checkResponseBasic
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorOperation
import ai.koog.integration.tests.utils.TestUtils.Colors
import ai.koog.integration.tests.utils.TestUtils.Country
import ai.koog.integration.tests.utils.TestUtils.StructuredTest
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.checkResponse
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.getConfigFixingParserNative
import ai.koog.integration.tests.utils.TestUtils.StructuredTest.getConfigNoFixingParserNative
import ai.koog.integration.tests.utils.TestUtils.markdownCountryDefinition
import ai.koog.integration.tests.utils.TestUtils.parseMarkdownStreamToCountries
import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenRouterKeyFromEnv
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams.ToolChoice
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.files.Path as KtPath

@Execution(ExecutionMode.SAME_THREAD)
class SingleLLMPromptExecutorIntegrationTest {
    companion object {
        private lateinit var testResourcesDir: Path

        @JvmStatic
        @BeforeAll
        fun setupTestResources() {
            testResourcesDir =
                Paths.get(SingleLLMPromptExecutorIntegrationTest::class.java.getResource("/media")!!.toURI())
        }

        // combinations for usual universal tests
        @JvmStatic
        fun modelClientCombinations(): Stream<Arguments> {
            val openAIClientInstance = OpenAILLMClient(readTestOpenAIKeyFromEnv())
            val anthropicClientInstance = AnthropicLLMClient(readTestAnthropicKeyFromEnv())
            val googleClientInstance = GoogleLLMClient(readTestGoogleAIKeyFromEnv())
            val openRouterClientInstance = OpenRouterLLMClient(readTestOpenRouterKeyFromEnv())
            val bedrockClientInstance = BedrockLLMClient(
                readAwsAccessKeyIdFromEnv(),
                readAwsSecretAccessKeyFromEnv(),
                readAwsSessionTokenFromEnv(),
                BedrockClientSettings()
            )

            return Stream.concat(
                Stream.concat(
                    Stream.concat(
                        Models.openAIModels().map { model -> Arguments.of(model, openAIClientInstance) },
                        Models.anthropicModels().map { model -> Arguments.of(model, anthropicClientInstance) }
                    ),
                    Stream.concat(
                        Models.googleModels().map { model -> Arguments.of(model, googleClientInstance) },
                        Models.openRouterModels().map { model -> Arguments.of(model, openRouterClientInstance) }
                    )
                ),
                Models.bedrockModels().map { model -> Arguments.of(model, bedrockClientInstance) }
            )
        }

        @JvmStatic
        fun bedrockCombinations(): Stream<Arguments> {
            val bedrockClientInstance = BedrockLLMClient(
                readAwsAccessKeyIdFromEnv(),
                readAwsSecretAccessKeyFromEnv(),
                readAwsSessionTokenFromEnv(),
                BedrockClientSettings(),
            )

            return Models.bedrockModels().map { model -> Arguments.of(model, bedrockClientInstance) }
        }

        @JvmStatic
        fun markdownScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.markdownScenarioModelCombinations()
        }

        @JvmStatic
        fun imageScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.imageScenarioModelCombinations()
        }

        @JvmStatic
        fun textScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.textScenarioModelCombinations()
        }

        @JvmStatic
        fun audioScenarioModelCombinations(): Stream<Arguments> {
            return MediaTestScenarios.audioScenarioModelCombinations()
        }
    }

    private fun getClient(model: LLModel): LLMClient {
        return when (model.provider) {
            LLMProvider.Anthropic -> AnthropicLLMClient(
                readTestAnthropicKeyFromEnv()
            )

            LLMProvider.OpenAI -> OpenAILLMClient(
                readTestOpenAIKeyFromEnv()
            )

            LLMProvider.Bedrock -> BedrockLLMClient(
                readAwsAccessKeyIdFromEnv(),
                readAwsSecretAccessKeyFromEnv(),
                readAwsSessionTokenFromEnv(),
                BedrockClientSettings()
            )

            else -> GoogleLLMClient(
                readTestGoogleAIKeyFromEnv()
            )
        }
    }

    private fun createCalculatorTool(): ToolDescriptor {
        return ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )
    }

    private fun createCalculatorPrompt() = Prompt.build("test-tools") {
        system(
            "You are a helpful assistant with access to a calculator tool. When asked to perform calculations, use the calculator tool instead of calculating the answer yourself."
        )
        user("What is 123 + 456?")
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecute(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-prompt") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testExecute[${model.id}]") {
            val response = executor.execute(prompt, model, emptyList())
            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

            val message = response.first() as Message.Assistant
            assertTrue(
                message.content.contains("Paris", ignoreCase = true),
                "Response should contain 'Paris'"
            )
            assertNotNull(message.metaInfo.inputTokensCount, "Input tokens count should not be null")
            assertNotNull(message.metaInfo.outputTokensCount, "Output tokens count should not be null")
            assertNotNull(message.metaInfo.totalTokensCount, "Total tokens count should not be null")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testExecuteStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }
        // TODO fix (KG-394): OpenRouter anthropic/claude-sonnet-4 streaming is incompatible with our current client setup (SSE/protocol)
        if (model.provider == LLMProvider.OpenRouter && model.id.contains("anthropic/claude-sonnet-4")) {
            assumeTrue(false, "Skipping OpenRouter anthropic/claude-sonnet-4 streaming: protocol incompatibility")
        }

        val executor = SingleLLMPromptExecutor(client)

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant.")
            user("Count from 1 to 5.")
        }

        withRetry(times = 3, testName = "integration_testExecuteStreaming[${model.id}]") {
            val responseChunks = executor.executeStreaming(prompt, model)
                .filterTextOnly()
                .toList()
            assertNotNull(responseChunks, "Response chunks should not be null")
            assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

            val fullResponse = responseChunks.joinToString("")
            assertTrue(
                fullResponse.contains("1") &&
                    fullResponse.contains("2") &&
                    fullResponse.contains("3") &&
                    fullResponse.contains("4") &&
                    fullResponse.contains("5"),
                "Full response should contain numbers 1 through 5"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        withRetry(times = 3, testName = "integration_testToolsWithRequiredParams[${model.id}]") {
            val executor = SingleLLMPromptExecutor(client)
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithRequiredOptionalParams(model: LLModel, client: LLMClient) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

            val calculatorTool = ToolDescriptor(
                name = "calculator",
                description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "operation",
                        description = "The operation to perform.",
                        type = ToolParameterType.Enum(
                            CalculatorOperation.entries.map { it.name }
                                .toTypedArray()
                        )
                    ),
                    ToolParameterDescriptor(
                        name = "a",
                        description = "The first argument (number)",
                        type = ToolParameterType.Float
                    ),
                    ToolParameterDescriptor(
                        name = "b",
                        description = "The second argument (number)",
                        type = ToolParameterType.Float
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "comment",
                        description = "Comment to the result (string)",
                        type = ToolParameterType.String
                    )
                )
            )

            val prompt = Prompt.build("test-tools") {
                system(
                    "You are a helpful assistant with access to a calculator tool. Don't use optional params if possible. ALWAYS CALL TOOL FIRST."
                )
                user("What is 123 + 456?")
            }

            val executor = SingleLLMPromptExecutor(client)

            withRetry(times = 3, testName = "integration_testToolsWithRequiredOptionalParams[${model.id}]") {
                val response = executor.execute(prompt, model, listOf(calculatorTool))
                assertTrue(response.isNotEmpty(), "Response should not be empty")
            }
        }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithOptionalParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "comment",
                    description = "Comment to the result (string)",
                    type = ToolParameterType.String
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val executor = SingleLLMPromptExecutor(client)
        withRetry(times = 3, testName = "integration_testToolsWithOptionalParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(calculatorTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNoParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
        )

        val calculatorToolBetter = ToolDescriptor(
            name = "calculatorBetter",
            description = "A better calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to calculator tools. Use the best one.")
            user("What is 123 + 456?")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithNoParams[${model.id}]") {
            val response =
                executor.execute(prompt, model, listOf(calculatorTool, calculatorToolBetter))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithListEnumParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val colorPickerTool = ToolDescriptor(
            name = "colorPicker",
            description = "A tool that can randomly pick a color from a list of colors.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "color",
                    description = "The color to be picked.",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(
                            Colors.entries.map { it.name }
                                .toTypedArray()
                        )
                    )
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant with access to a color picker tool. ALWAYS CALL TOOL FIRST.")
            user("Pick me a color!")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithListEnumParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(colorPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolsWithNestedListParams(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val lotteryPickerTool = ToolDescriptor(
            name = "lotteryPicker",
            description = "A tool that can randomly you some lottery winners and losers",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "Numbers",
                    description = "A list of the numbers for lottery winners and losers from 1 to 100",
                    type = ToolParameterType.List(ToolParameterType.List(ToolParameterType.Integer))
                )
            )
        )

        val prompt = Prompt.build("test-tools") {
            system("You are a helpful assistant. ALWAYS CALL TOOL FIRST.")
            user("Pick me lottery winners and losers! 5 of each")
        }

        val executor = SingleLLMPromptExecutor(client)

        withRetry(times = 3, testName = "integration_testToolsWithNestedListParams[${model.id}]") {
            val response = executor.execute(prompt, model, listOf(lotteryPickerTool))
            assertTrue(response.isNotEmpty(), "Response should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testRawStringStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 600.seconds) {
        Models.assumeAvailable(model.provider)
        if (model.id == OpenAIModels.Audio.GPT4oAudio.id || model.id == OpenAIModels.Audio.GPT4oMiniAudio.id) {
            assumeTrue(false, "https://github.com/JetBrains/koog/issues/231")
        }
        // TODO fix (KG-394): OpenRouter anthropic/claude-sonnet-4 streaming is incompatible with our current client setup (SSE/protocol)
        if (model.provider == LLMProvider.OpenRouter && model.id.contains("anthropic/claude-sonnet-4")) {
            assumeTrue(false, "Skipping OpenRouter anthropic/claude-sonnet-4 streaming: protocol incompatibility")
        }

        val prompt = Prompt.build("test-streaming") {
            system("You are a helpful assistant. You have NO output length limitations.")
            user("Count from 1 to 5.")
        }

        val responseChunks = mutableListOf<StreamFrame>()

        withRetry(times = 3, testName = "integration_testRawStringStreaming[${model.id}]") {
            client.executeStreaming(prompt, model).collect { chunk ->
                responseChunks.add(chunk)
            }

            assertTrue(responseChunks.isNotEmpty(), "Response chunks should not be empty")

            val fullResponse = responseChunks.joinToString("")
            assertTrue(
                fullResponse.contains("1") &&
                    fullResponse.contains("2") &&
                    fullResponse.contains("3") &&
                    fullResponse.contains("4") &&
                    fullResponse.contains("5"),
                "Full response should contain numbers 1 through 5"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredDataStreaming(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model != OpenAIModels.CostOptimized.GPT4_1Nano, "Model $model is too small for structured streaming")
        // TODO fix (KG-394): OpenRouter anthropic/claude-sonnet-4 streaming is incompatible with our current client setup (SSE/protocol)
        if (model.provider == LLMProvider.OpenRouter && model.id.contains("anthropic/claude-sonnet-4")) {
            assumeTrue(false, "Skipping OpenRouter anthropic/claude-sonnet-4 streaming: protocol incompatibility")
        }

        val countries = mutableListOf<Country>()
        val countryDefinition = markdownCountryDefinition()

        val prompt = Prompt.build("test-structured-streaming") {
            system("You are a helpful assistant.")
            user(
                """
                Please provide information about 3 European countries in this format:

                $countryDefinition

                Make sure to follow this exact format with the # for country names and * for details.
                """.trimIndent()
            )
        }

        withRetry(times = 3, testName = "integration_testStructuredDataStreaming[${model.id}]") {
            val markdownStream = client.executeStreaming(prompt, model)

            parseMarkdownStreamToCountries(markdownStream).collect { country ->
                countries.add(country)
            }

            assertTrue(countries.isNotEmpty(), "Countries list should not be empty")
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceRequired(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        /** tool choice auto is default and thus is tested by [integration_testToolsWithRequiredParams] */

        withRetry(times = 3, testName = "integration_testToolChoiceRequired[${model.id}]") {
            val response = client.execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Required
                    )
                ),
                model,
                listOf(calculatorTool)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Tool.Call)
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNone(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        withRetry(times = 3, testName = "integration_testToolChoiceNone[${model.id}]") {
            val response = client.execute(
                Prompt.build("test-tools") {
                    system("You are a helpful assistant. Do not use calculator tool, it's broken!")
                    user("What is 123 + 456?")
                }.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.None
                    )
                ),
                model,
                listOf(calculatorTool)
            )

            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant)
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testToolChoiceNamed(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(!(model.provider == LLMProvider.OpenRouter && model.id.contains("anthropic")), "KG-282")
        assumeTrue(model.capabilities.contains(LLMCapability.ToolChoice), "Model $model does not support tools")

        val calculatorTool = createCalculatorTool()
        val prompt = createCalculatorPrompt()

        val nothingTool = ToolDescriptor(
            name = "nothing",
            description = "A tool that does nothing",
        )

        withRetry(times = 3, testName = "integration_testToolChoiceNamed[${model.id}]") {
            val response = client.execute(
                prompt.withParams(
                    prompt.params.copy(
                        toolChoice = ToolChoice.Named(nothingTool.name)
                    )
                ),
                model,
                listOf(calculatorTool, nothingTool)
            )

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(
                response.first() is Message.Tool.Call,
                "First message should be a tool call, but was ${response.first().role}"
            )
            val toolCall = response.first() as Message.Tool.Call
            assertEquals("nothing", toolCall.tool, "Tool name should be 'nothing'")
        }
    }

    /*
     * IMPORTANT about the testing approach!
     * The number of combinations between specific executors and media types will make tests slower.
     * The compatibility of each LLM profile with the media processing is covered in the E2E agents tests.
     * Therefore, in the scope of the executor tests, we'll check one executor of each provider
     * to decrease the number of possible combinations and to avoid redundant checks.*/

    @ParameterizedTest
    @MethodSource("markdownScenarioModelCombinations")
    fun integration_testMarkdownProcessingBasic(
        scenario: MarkdownTestScenario,
        model: LLModel
    ) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val file = MediaTestUtils.createMarkdownFileForScenario(scenario, testResourcesDir)

            val prompt =
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("markdown-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze markdown files.")

                        user {
                            markdown {
                                +"I'm sending you a markdown file with different markdown elements. "
                                +"Please list all the markdown elements used in it and describe its structure clearly."
                            }

                            attachments {
                                textFile(KtPath(file.pathString), "text/plain")
                            }
                        }
                    }
                } else {
                    prompt("markdown-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze markdown files.")

                        user {
                            markdown {
                                +"I'm sending you a markdown file with different markdown elements. "
                                +"Please list all the markdown elements used in it and describe its structure clearly."
                                newline()
                                +file.readText()
                            }
                        }
                    }
                }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    when (scenario) {
                        MarkdownTestScenario.MALFORMED_SYNTAX,
                        MarkdownTestScenario.MATH_NOTATION,
                        MarkdownTestScenario.BROKEN_LINKS,
                        MarkdownTestScenario.IRREGULAR_TABLES -> {
                            checkResponseBasic(response)
                        }

                        else -> {
                            checkExecutorMediaResponse(response)
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

    @ParameterizedTest
    @MethodSource("imageScenarioModelCombinations")
    fun integration_testImageProcessing(scenario: ImageTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(model.capabilities.contains(LLMCapability.Vision.Image), "Model must support vision capability")

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val imageFile = MediaTestUtils.getImageFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("image-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant that can analyze images.")

                user {
                    markdown {
                        +"I'm sending you an image. Please analyze it and identify the image format if possible."
                    }

                    attachments {
                        when (scenario) {
                            ImageTestScenario.LARGE_IMAGE, ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                                image(
                                    Attachment.Image(
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
            }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    // For some edge cases, exceptions are expected
                    when (scenario) {
                        ImageTestScenario.LARGE_IMAGE_ANTHROPIC, ImageTestScenario.LARGE_IMAGE -> {
                            assertEquals(
                                e.message?.contains("400 Bad Request"),
                                true,
                                "Expected exception for a large image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            assertEquals(
                                e.message?.contains("image exceeds"),
                                true,
                                "Expected exception for a large image [image exceeds] was not found, got [${e.message}] instead"
                            )
                        }

                        ImageTestScenario.CORRUPTED_IMAGE, ImageTestScenario.EMPTY_IMAGE -> {
                            assertEquals(
                                e.message?.contains("400 Bad Request"),
                                true,
                                "Expected exception for a corrupted image [400 Bad Request] was not found, got [${e.message}] instead"
                            )
                            if (model.provider == LLMProvider.Anthropic) {
                                assertEquals(
                                    e.message?.contains("Could not process image"),
                                    true,
                                    "Expected exception for a corrupted image [Could not process image] was not found, got [${e.message}] instead"
                                )
                            } else if (model.provider == LLMProvider.OpenAI) {
                                assertEquals(
                                    e.message?.contains(
                                        "You uploaded an unsupported image. Please make sure your image is valid."
                                    ),
                                    true,
                                    "Expected exception for a corrupted image [You uploaded an unsupported image. Please make sure your image is valid..] was not found, got [${e.message}] instead"
                                )
                            }
                        }

                        else -> {
                            throw e
                        }
                    }
                }
            }
        }

    @ParameterizedTest
    @MethodSource("textScenarioModelCombinations")
    fun integration_testTextProcessingBasic(scenario: TextTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val file = MediaTestUtils.createTextFileForScenario(scenario, testResourcesDir)

            val prompt =
                if (model.capabilities.contains(LLMCapability.Document) && model.provider != LLMProvider.OpenAI) {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                            }

                            attachments {
                                textFile(KtPath(file.pathString), "text/plain")
                            }
                        }
                    }
                } else {
                    prompt("text-test-${scenario.name.lowercase()}") {
                        system("You are a helpful assistant that can analyze and process text.")

                        user {
                            markdown {
                                +"I'm sending you a text file. Please analyze it and summarize its content."
                                newline()
                                +file.readText()
                            }
                        }
                    }
                }

            withRetry {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    when (scenario) {
                        TextTestScenario.EMPTY_TEXT -> {
                            if (model.provider == LLMProvider.Google) {
                                assertEquals(
                                    e.message?.contains("400 Bad Request"),
                                    true,
                                    "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertEquals(
                                    e.message?.contains(
                                        "Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again."
                                    ),
                                    true,
                                    "Expected exception for empty text [Unable to submit request because it has an empty inlineData parameter. Add a value to the parameter and try again] was not found, got [${e.message}] instead"
                                )
                            }
                        }

                        TextTestScenario.LONG_TEXT_5_MB -> {
                            if (model.provider == LLMProvider.Anthropic) {
                                assertEquals(
                                    e.message?.contains("400 Bad Request"),
                                    true,
                                    "Expected exception for long text [400 Bad Request] was not found, got [${e.message}] instead"
                                )
                                assertEquals(
                                    e.message?.contains("prompt is too long"),
                                    true,
                                    "Expected exception for long text [prompt is too long:] was not found, got [${e.message}] instead"
                                )
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

    @ParameterizedTest
    @MethodSource("audioScenarioModelCombinations")
    fun integration_testAudioProcessingBasic(scenario: AudioTestScenario, model: LLModel) =
        runTest(timeout = 300.seconds) {
            Models.assumeAvailable(model.provider)
            assumeTrue(
                model.capabilities.contains(LLMCapability.Audio),
                "Model must support audio capability"
            )

            val client = getClient(model)
            val executor = SingleLLMPromptExecutor(client)

            val audioFile = MediaTestUtils.createAudioFileForScenario(scenario, testResourcesDir)

            val prompt = prompt("audio-test-${scenario.name.lowercase()}") {
                system("You are a helpful assistant.")

                user {
                    +"I'm sending you an audio file. Please tell me a couple of words about it."

                    attachments {
                        audio(KtPath(audioFile.pathString))
                    }
                }
            }

            withRetry(times = 3, testName = "integration_testAudioProcessingBasic[${model.id}]") {
                try {
                    val response = executor.execute(prompt, model).single()
                    checkExecutorMediaResponse(response)
                } catch (e: Exception) {
                    if (scenario == AudioTestScenario.CORRUPTED_AUDIO) {
                        assertEquals(
                            e.message?.contains("400 Bad Request"),
                            true,
                            "Expected exception for empty text [400 Bad Request] was not found, got [${e.message}] instead"
                        )
                        if (model.provider == LLMProvider.OpenAI) {
                            assertEquals(
                                e.message?.contains("This model does not support the format you provided."),
                                true,
                                "Expected exception for corrupted audio [This model does not support the format you provided.]"
                            )
                        } else if (model.provider == LLMProvider.Google) {
                            assertEquals(
                                e.message?.contains("Request contains an invalid argument."),
                                true,
                                "Expected exception for corrupted audio [Request contains an invalid argument.]"
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

    /*
     * Checking just images to make sure the file is uploaded in base64 format
     * */
    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testBase64EncodedAttachment(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val executor = SingleLLMPromptExecutor(client)

        assumeTrue(
            model.capabilities.contains(LLMCapability.Vision.Image),
            "Model must support vision capability"
        )

        // Skip audio-only models
        assumeTrue(
            !model.id.contains("audio", ignoreCase = true),
            "Audio-only models are not supported for this test"
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

                attachments {
                    image(KtPath(tempImageFile.pathString))
                }
            }
        }

        withRetry {
            val response = executor.execute(prompt, model).single()
            checkExecutorMediaResponse(response)

            assertTrue(
                response.content.contains("image", ignoreCase = true),
                "Response should mention the image"
            )
        }
    }

    /*
     * Checking just images to make sure the file is uploaded by URL
     * */
    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testUrlBasedAttachment(model: LLModel, client: LLMClient) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.provider !== LLMProvider.Google, "Google models do not support URL attachments")
        val executor = SingleLLMPromptExecutor(client)

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

                attachments {
                    image(imageUrl)
                }
            }
        }

        withRetry {
            val response = executor.execute(prompt, model).single()
            checkExecutorMediaResponse(response)

            assertTrue(
                response.content.contains("image", ignoreCase = true) ||
                    response.content.contains("python", ignoreCase = true) ||
                    response.content.contains("logo", ignoreCase = true),
                "Response should mention the image content"
            )
        }
    }

    /**
     * Tests the simpleBedrockExecutor function with different Bedrock models.
     *
     * Some models may require an inference profile instead of on-demand throughput.
     * The test may fail if the AWS account doesn't have access to the specified models.
     */
    @ParameterizedTest
    @MethodSource("bedrockCombinations")
    fun integration_testSimpleBedrockExecutor(model: LLModel) = runTest(timeout = 300.seconds) {
        val executor = simpleBedrockExecutor(
            readAwsAccessKeyIdFromEnv(),
            readAwsSecretAccessKeyFromEnv(),
            readAwsSessionTokenFromEnv(),
        )

        val prompt = Prompt.build("test-simple-bedrock-executor") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        withRetry(times = 3, testName = "integration_testSimpleBedrockExecutor[${model.id}]") {
            val response = executor.execute(prompt, model, emptyList())

            assertNotNull(response, "Response should not be null")
            assertTrue(response.isNotEmpty(), "Response should not be empty")
            assertTrue(response.first() is Message.Assistant, "Response should be an Assistant message")

            val message = response.first() as Message.Assistant

            assertTrue(
                message.content.contains("Paris", ignoreCase = true),
                "Response should contain 'Paris'"
            )

            assertNotNull(message.metaInfo.inputTokensCount, "Input tokens count should not be null")
            assertNotNull(message.metaInfo.outputTokensCount, "Output tokens count should not be null")
            assertNotNull(message.metaInfo.totalTokensCount, "Total tokens count should not be null")
        }
    }

    /*
     * Structured native/manual output tests.
     * */

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputNative(model: LLModel, client: LLMClient) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )
        // TODO fix (KG-394): OpenRouter anthropic/claude-sonnet-4 streaming is incompatible with our current client setup (SSE/protocol)
        if (model.provider == LLMProvider.OpenRouter) {
            assumeTrue(false, "Skipping StructuredOutputNative for OpenRouter due to schema incompatibilities upstream")
        }
        val executor = SingleLLMPromptExecutor(client)

        withRetry {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = getConfigNoFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputNativeWithFixingParser(model: LLModel, client: LLMClient) = runTest {
        assumeTrue(
            model.capabilities.contains(LLMCapability.Schema.JSON.Standard),
            "Model does not support Standard JSON Schema"
        )
        // TODO fix (KG-394) OpenRouter
        if (model.provider == LLMProvider.OpenRouter) {
            assumeTrue(
                false,
                "Skipping StructuredOutputNativeWithFixingParser for OpenRouter due to upstream schema incompatibilities"
            )
        }
        val executor = SingleLLMPromptExecutor(client)

        withRetry {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = getConfigFixingParserNative(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputManual(model: LLModel, client: LLMClient) = runTest {
        assumeTrue(
            model.provider !== LLMProvider.Google,
            "Google models fail to return manually requested structured output without fixing"
        )
        val executor = SingleLLMPromptExecutor(client)

        withRetry {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigNoFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }

    @ParameterizedTest
    @MethodSource("modelClientCombinations")
    fun integration_testStructuredOutputManualWithFixingParser(model: LLModel, client: LLMClient) = runTest {
        assumeTrue(
            (model !== GoogleModels.Gemini2_0FlashLite) && (model !== GoogleModels.Gemini2_0FlashLite001),
            "Gemini Flash Lite 2.0 models fail to return manually requested structured output"
        )
        val executor = SingleLLMPromptExecutor(client)

        withRetry(6) {
            val result = executor.executeStructured(
                prompt = StructuredTest.prompt,
                model = model,
                config = StructuredTest.getConfigFixingParserManual(model)
            )

            assertTrue(result.isSuccess, "Structured output should succeed: ${result.exceptionOrNull()}")
            checkResponse(result)
        }
    }
}
