package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ToolSchemaExecutorIntegrationTest {
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

        @JvmStatic
        fun invalidToolDescriptors(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    ToolDescriptor(
                        name = "",
                        description = "Tool with empty name",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("param", "A parameter", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.name': empty string. Expected a string with minimum length 1, but got an empty string instead."
                ),
                // Todo uncomment when KG-185 is fixed
                /*Arguments.of(
                    ToolDescriptor(
                        name = "test_tool",
                        description = "",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("param", "A parameter", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.description': empty string. Expected a string with minimum length 1, but got an empty string instead."
                ),
                Arguments.of(
                    ToolDescriptor(
                        name = "param_name_test",
                        description = "Tool to test parameter name validation",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("", "Parameter with empty name", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.requiredParameters[0]': empty string. Expected a string with minimum length 1, but got an empty string instead."
                )*/
            )
        }
    }

    val model = OpenAIModels.Chat.GPT4o
    val client = OpenAILLMClient(TestUtils.readTestOpenAIKeyFromEnv())

    class FileTools : ToolSet {

        @Tool
        @LLMDescription(
            "Writes content to a file (creates new or overwrites existing). BOTH filePath AND content parameters are REQUIRED."
        )
        fun writeFile(
            @LLMDescription("Full path where the file should be created") filePath: String,
            @LLMDescription("Content to write to the file - THIS IS REQUIRED AND CANNOT BE EMPTY") content: String,
            @LLMDescription("Whether to overwrite if file exists (default: false)") overwrite: Boolean = false
        ) {
            println("Writing '$content' to file '$filePath' with overwrite=$overwrite")
        }
    }

    @Serializable
    data class FileOperation(
        val filePath: String,
        val content: String,
        val overwrite: Boolean = false
    )

    @ParameterizedTest
    @MethodSource("anthropicModels", "googleModels", "openAIModels")
    fun integration_testToolSchemaExecutor(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val client = when (model.provider) {
            is LLMProvider.Anthropic -> AnthropicLLMClient(TestUtils.readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> GoogleLLMClient(TestUtils.readTestGoogleAIKeyFromEnv())
            else -> OpenAILLMClient(TestUtils.readTestOpenAIKeyFromEnv())
        }

        val fileTools = FileTools()

        val toolsFromCallable = fileTools.asTools()

        val tools = toolsFromCallable.map { it.descriptor }

        val writeFileTool = tools.first { it.name == "writeFile" }

        val prompt = prompt("test-write-file", params = LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant with access to a file writing tool. ALWAYS use tools.")
            user("Please write 'Hello, World!' to a file named 'hello.txt'.")
        }

        withRetry {
            val response = client.execute(prompt, model, listOf(writeFileTool))
            val responseText = response.joinToString("\n") { it.content }
            val fileOperation = Json.decodeFromString<FileOperation>(responseText)

            assertNotNull(response)
            assertTrue(response.isNotEmpty())
            assertEquals("hello.txt", fileOperation.filePath)
            assertEquals("Hello, World!", fileOperation.content)
        }
    }

    @ParameterizedTest
    @MethodSource("invalidToolDescriptors")
    fun integration_testInvalidToolDescriptorShouldFail(invalidToolDescriptor: ToolDescriptor, message: String) =
        runTest(timeout = 300.seconds) {
            val prompt = prompt("test-invalid-tool", params = LLMParams(toolChoice = ToolChoice.Required)) {
                system("You are a helpful assistant with access to tools.")
                user("Hi.")
            }

            val exception = assertFailsWith<Exception> {
                client.execute(prompt, model, listOf(invalidToolDescriptor))
            }

            assumeTrue(
                exception.message?.contains(message) == true,
                "Expected exception message to contain '$message', but got '${exception.message}'"
            )
        }
}
