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
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertFailsWith
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
        fun openRouterModels(): Stream<LLModel> {
            return Models.openRouterModels()
        }

        @JvmStatic
        fun bedrockModels(): Stream<LLModel> {
            return Models.bedrockModels()
        }

        @JvmStatic
        fun mistralModels(): Stream<LLModel> {
            return Models.mistralModels()
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
                // Uncomment when KG-185 is fixed
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
    @MethodSource(
        "anthropicModels",
        "googleModels",
        "openAIModels",
        "openRouterModels",
        "bedrockModels",
        "mistralModels"
    )
    fun integration_testToolSchemaExecutor(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val fileTools = FileTools()

        val toolsFromCallable = fileTools.asTools()

        val tools = toolsFromCallable.map { it.descriptor }

        val writeFileTool = tools.first { it.name == "writeFile" }

        val prompt = prompt("test-write-file", params = LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant with access to a file writing tool. ALWAYS use tools.")
            user("Please write 'Hello, World!' to a file named 'hello.txt'.")
        }

        withRetry {
            getLLMClientForProvider(model.provider).execute(prompt, model, listOf(writeFileTool)) shouldNotBeNull {
                shouldNotBeEmpty()
                with(Json.decodeFromString<FileOperation>(joinToString("\n") { it.content })) {
                    filePath shouldBe "hello.txt"
                    content.trim() shouldBe "Hello, World!"
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidToolDescriptors")
    fun integration_testInvalidToolDescriptorShouldFail(invalidToolDescriptor: ToolDescriptor, message: String) =
        runTest(timeout = 300.seconds) {
            val model = OpenAIModels.Chat.GPT4o

            assertFailsWith<Exception> {
                getLLMClientForProvider(model.provider).execute(
                    prompt("test-invalid-tool", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with access to tools.")
                        user("Hi.")
                    },
                    model,
                    listOf(invalidToolDescriptor)
                )
            }.message.shouldNotBeNull {
                shouldContain(
                    message
                )
            }
        }
}
