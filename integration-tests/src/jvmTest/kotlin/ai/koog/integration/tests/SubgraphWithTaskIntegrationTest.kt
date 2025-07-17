package ai.koog.integration.tests

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestGoogleAIKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Integration tests for using sealed classes in structured output with subgraphWithTask
 * and custom implementation of ProvideSubgraphResult.
 */
class SubgraphWithTaskIntegrationTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Ensure API keys are available
            readTestOpenAIKeyFromEnv()
            readTestAnthropicKeyFromEnv()
            readTestGoogleAIKeyFromEnv()
        }

        @JvmStatic
        fun openAIModels(): Stream<LLModel> = Stream.of(
            OpenAIModels.Chat.GPT4o,
            OpenAIModels.Reasoning.GPT4oMini
        )

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> = Stream.of(
            AnthropicModels.Sonnet_3_7
        )

        @JvmStatic
        fun googleModels(): Stream<LLModel> = Stream.of(
            GoogleModels.Gemini1_5Pro
        )
    }

    /**
     * A sealed class hierarchy representing different types of tasks that can be performed.
     */
    @Serializable
    @kotlinx.serialization.json.JsonClassDiscriminator("taskType")
    sealed class TaskResult : SubgraphResult {
        abstract val status: TaskStatus
        abstract val message: String

        override fun toStringDefault(): String = Json.encodeToString(serializer(), this)

        @Serializable
        enum class TaskStatus { Success, Failure, InProgress }

        /**
         * Represents the result of a data analysis task.
         */
        @Serializable
        @SerialName("DataAnalysis")
        data class DataAnalysisResult(
            override val status: TaskStatus,
            override val message: String,
            @SerialName("type") val taskType: String = "DataAnalysis",
            val dataPoints: Int,
            val insights: List<String>
        ) : TaskResult()

        /**
         * Represents the result of a code generation task.
         */
        @Serializable
        @SerialName("CodeGeneration")
        data class CodeGenerationResult(
            override val status: TaskStatus,
            override val message: String,
            @SerialName("type") val taskType: String = "CodeGeneration",
            val language: String,
            val code: String,
            val lineCount: Int
        ) : TaskResult()

        /**
         * Represents the result of a text summarization task.
         */
        @Serializable
        @SerialName("Summarization")
        data class SummarizationResult(
            override val status: TaskStatus,
            override val message: String,
            @SerialName("type") val taskType: String = "Summarization",
            val originalLength: Int,
            val summaryLength: Int,
            val summary: String
        ) : TaskResult()
    }

    /**
     * Custom implementation of ProvideSubgraphResult for TaskResult.
     */
    object ProvideTaskResult : ProvideSubgraphResult<TaskResult>() {
        override val argsSerializer: KSerializer<TaskResult> = TaskResult.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "finish_task",
            description = "Call this tool when you have completed the requested task. Provide the appropriate result based on the task type.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "status",
                    description = "Status of the task: Success, Failure, or InProgress",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "message",
                    description = "A message describing the result or any issues encountered",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "taskType",
                    description = "Type of task: DataAnalysis, CodeGeneration, or Summarization",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                // DataAnalysisResult parameters
                ToolParameterDescriptor(
                    name = "dataPoints",
                    description = "Number of data points analyzed (for DataAnalysis tasks)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "insights",
                    description = "List of insights gained from the data (for DataAnalysis tasks)",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),

                // CodeGenerationResult parameters
                ToolParameterDescriptor(
                    name = "language",
                    description = "Programming language used (for CodeGeneration tasks)",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "code",
                    description = "Generated code (for CodeGeneration tasks)",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "lineCount",
                    description = "Number of lines in the generated code (for CodeGeneration tasks)",
                    type = ToolParameterType.Integer
                ),

                // SummarizationResult parameters
                ToolParameterDescriptor(
                    name = "originalLength",
                    description = "Length of the original text (for Summarization tasks)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "summaryLength",
                    description = "Length of the summary (for Summarization tasks)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "summary",
                    description = "The generated summary (for Summarization tasks)",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: TaskResult): TaskResult {
            return args
        }
    }

    private fun getExecutor(model: LLModel): SingleLLMPromptExecutor {
        return when (model.provider) {
            LLMProvider.OpenAI -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
            LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> throw IllegalArgumentException("Unsupported model provider: ${model.provider}")
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testDataAnalysisTask(model: LLModel) = runTest {
        assumeTrue(
            model.provider != LLMProvider.Google || model == GoogleModels.Gemini1_5Pro,
            "Only Gemini 1.5 Pro supports tools"
        )

        withRetry(3) {
            val executor = getExecutor(model)

            val toolRegistry = ToolRegistry {
                tool(ProvideTaskResult)
            }

            val strategy = strategy<String, TaskResult>("data-analysis-strategy") {
                val analyzeData by subgraphWithTask<String, TaskResult>(
                    tools = listOf(ProvideTaskResult),
                    finishTool = ProvideTaskResult,
                    llmModel = model
                ) { input ->
                    """
                    You are a data analyst. Your task is to analyze the following data and provide insights.
                    
                    Data: $input
                    
                    Analyze the data and provide at least 3 insights. When you're done, call the finish_task tool
                    with the appropriate parameters for a DataAnalysisResult.
                    """
                }

                edge(nodeStart forwardTo analyzeData)
                edge(analyzeData forwardTo nodeFinish)
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt(id = "data-analysis") {
                    system("You are a helpful AI assistant specialized in data analysis.")
                },
                model = model,
                maxAgentIterations = 50
            )

            val agent = AIAgent<String, TaskResult>(
                promptExecutor = executor,
                strategy = strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry
            )

            val testData = """
                Sales Data (2023):
                Q1: $120,000
                Q2: $145,000
                Q3: $180,000
                Q4: $210,000
            """

            val result = agent.run(testData)

            // Verify the result is a DataAnalysisResult
            assertTrue(result is TaskResult.DataAnalysisResult)
            val dataResult = result as TaskResult.DataAnalysisResult

            // Verify the result has the expected properties
            assertEquals(TaskResult.TaskStatus.Success, dataResult.status)
            assertTrue(dataResult.message.isNotEmpty())
            assertTrue(dataResult.dataPoints > 0)
            assertTrue(dataResult.insights.isNotEmpty())
            assertTrue(dataResult.insights.size >= 3)
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testCodeGenerationTask(model: LLModel) = runTest {
        assumeTrue(
            model.provider != LLMProvider.Google || model == GoogleModels.Gemini1_5Pro,
            "Only Gemini 1.5 Pro supports tools"
        )

        withRetry(3) {
            val executor = getExecutor(model)

            val toolRegistry = ToolRegistry {
                tool(ProvideTaskResult)
            }

            val strategy = strategy<String, TaskResult>("code-generation-strategy") {
                val generateCode by subgraphWithTask<String, TaskResult>(
                    tools = listOf(ProvideTaskResult),
                    finishTool = ProvideTaskResult,
                    llmModel = model
                ) { input ->
                    """
                    You are a code generator. Your task is to generate code based on the following requirements.
                    
                    Requirements: $input
                    
                    Generate the requested code. When you're done, call the finish_task tool
                    with the appropriate parameters for a CodeGenerationResult.
                    """
                }

                edge(nodeStart forwardTo generateCode)
                edge(generateCode forwardTo nodeFinish)
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt(id = "code-generation") {
                    system("You are a helpful AI assistant specialized in code generation.")
                },
                model = model,
                maxAgentIterations = 50
            )

            val agent = AIAgent<String, TaskResult>(
                promptExecutor = executor,
                strategy = strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry
            )

            val testRequirements = "Write a Python function that calculates the Fibonacci sequence up to n terms."

            val result = agent.run(testRequirements)

            // Verify the result is a CodeGenerationResult
            assertTrue(result is TaskResult.CodeGenerationResult)
            val codeResult = result as TaskResult.CodeGenerationResult

            // Verify the result has the expected properties
            assertEquals(TaskResult.TaskStatus.Success, codeResult.status)
            assertTrue(codeResult.message.isNotEmpty())
            assertEquals("python", codeResult.language.lowercase())
            assertTrue(codeResult.code.isNotEmpty())
            assertTrue(codeResult.lineCount > 0)
            assertTrue(codeResult.code.contains("def"))
            assertTrue(codeResult.code.contains("fibonacci") || codeResult.code.contains("Fibonacci"))
        }
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels", "googleModels")
    fun integration_testSummarizationTask(model: LLModel) = runTest {
        assumeTrue(
            model.provider != LLMProvider.Google || model == GoogleModels.Gemini1_5Pro,
            "Only Gemini 1.5 Pro supports tools"
        )

        withRetry(3) {
            val executor = getExecutor(model)

            val toolRegistry = ToolRegistry {
                tool(ProvideTaskResult)
            }

            val strategy = strategy<String, TaskResult>("summarization-strategy") {
                val summarizeText by subgraphWithTask<String, TaskResult>(
                    tools = listOf(ProvideTaskResult),
                    finishTool = ProvideTaskResult,
                    llmModel = model
                ) { input ->
                    """
                    You are a text summarizer. Your task is to summarize the following text.
                    
                    Text: $input
                    
                    Create a concise summary of the text. When you're done, call the finish_task tool
                    with the appropriate parameters for a SummarizationResult.
                    """
                }

                edge(nodeStart forwardTo summarizeText)
                edge(summarizeText forwardTo nodeFinish)
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt(id = "summarization") {
                    system("You are a helpful AI assistant specialized in text summarization.")
                },
                model = model,
                maxAgentIterations = 50
            )

            val agent = AIAgent<String, TaskResult>(
                promptExecutor = executor,
                strategy = strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry
            )

            val testText = """
                Artificial intelligence (AI) is intelligence demonstrated by machines, as opposed to intelligence displayed by animals including humans. 
                AI research has been defined as the field of study of intelligent agents, which refers to any system that perceives its environment and 
                takes actions that maximize its chance of achieving its goals. The term "artificial intelligence" had previously been used to describe 
                machines that mimic and display "human" cognitive skills that are associated with the human mind, such as "learning" and "problem-solving". 
                This definition has since been rejected by major AI researchers who now describe AI in terms of rationality and acting rationally, 
                which does not limit how intelligence can be articulated.
                
                AI applications include advanced web search engines (e.g., Google), recommendation systems (used by YouTube, Amazon, and Netflix), 
                understanding human speech (such as Siri and Alexa), self-driving cars (e.g., Waymo), generative or creative tools (ChatGPT and AI art), 
                automated decision-making, and competing at the highest level in strategic game systems (such as chess and Go).
                
                As machines become increasingly capable, tasks considered to require "intelligence" are often removed from the definition of AI, 
                a phenomenon known as the AI effect. For instance, optical character recognition is frequently excluded from things considered to be AI, 
                having become a routine technology.
            """

            val result = agent.run(testText)

            // Verify the result is a SummarizationResult
            assertTrue(result is TaskResult.SummarizationResult)
            val summaryResult = result as TaskResult.SummarizationResult

            // Verify the result has the expected properties
            assertEquals(TaskResult.TaskStatus.Success, summaryResult.status)
            assertTrue(summaryResult.message.isNotEmpty())
            assertTrue(summaryResult.originalLength > 0)
            assertTrue(summaryResult.summaryLength > 0)
            assertTrue(summaryResult.summary.isNotEmpty())
            assertTrue(summaryResult.originalLength > summaryResult.summaryLength)
        }
    }
}