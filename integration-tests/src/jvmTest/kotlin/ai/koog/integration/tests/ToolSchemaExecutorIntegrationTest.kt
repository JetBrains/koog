package ai.koog.integration.tests

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
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
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
    }

    class FileTools : ToolSet {

        @Tool
        @LLMDescription("Writes content to a file (creates new or overwrites existing). BOTH filePath AND content parameters are REQUIRED.")
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
}
