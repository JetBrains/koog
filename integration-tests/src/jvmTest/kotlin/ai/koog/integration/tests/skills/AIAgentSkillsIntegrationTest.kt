package ai.koog.integration.tests.skills

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.integration.tests.agent.AIAgentTestBase
import ai.koog.integration.tests.utils.Models
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.serialization.typeToken
import ai.koog.skills.discovery.discoverSkills
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath
import kotlin.time.Duration.Companion.seconds

class AIAgentSkillsIntegrationTest : AIAgentTestBase() {
    @Serializable
    private data class PythonExecutionArgs(
        val scriptPath: String,
        val argument: String,
    )

    @Serializable
    private data class PythonExecutionResult(
        val output: String,
        val exitCode: Int,
    )

    private class ExecutePythonScriptTool : ToolBase<PythonExecutionArgs, PythonExecutionResult>(
        argsType = typeToken<PythonExecutionArgs>(),
        resultType = typeToken<PythonExecutionResult>(),
        name = "execute_python_script",
        description = "Executes a python script by absolute path with a single argument and returns stdout.",
    ) {
        val executedScripts: MutableList<PythonExecutionArgs> = mutableListOf()

        override suspend fun execute(args: PythonExecutionArgs, metadata: ToolCallMetadata): PythonExecutionResult {
            executedScripts += args

            val output = when {
                args.scriptPath.endsWith("evaluate_arithmetic_exp.py") -> "56"
                args.scriptPath.endsWith("retrieve_weather.py") -> retrieveWeather(args.argument)
                else -> "Unsupported script: ${args.scriptPath}"
            }

            return PythonExecutionResult(output = output, exitCode = 0)
        }

        private fun retrieveWeather(location: String): String {
            val escapedLocation = location
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return """{"location":"$escapedLocation","temperature_c":$10,"condition":"cloudy","humidity_percent":35,"wind_kmh":30,"precipitation_chance_percent":50,"note":"fake-weather-data"}"""
        }
    }

    private fun skillsRootDirectory(): String =
        AIAgentSkillsIntegrationTest::class.java.getResource("/skills")!!.toURI().toPath().absolutePathString()

    @ParameterizedTest
    @MethodSource("latestModels")
    fun `integration test agent uses discovered skills prompt`(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val skillsRoot = skillsRootDirectory()
        val discoveredSkills = JVMFileSystemProvider.ReadOnly.discoverSkills(listOf(skillsRoot))
        val generatedSkillsPrompt = generateSkillsPrompt(discoveredSkills, SkillsPromptFormat.XML)
        val pythonTool = ExecutePythonScriptTool()

        runWithTracking { eventHandlerConfig, state ->
            val agent = AIAgent(
                promptExecutor = getExecutor(model),
                strategy = singleRunStrategy(),
                agentConfig = AIAgentConfig(
                    prompt = prompt("skills-with-tools") {
                        system(
                            """
                            You are a careful assistant.
                            Use the available skills listed below.
                            Before using a skill script, disclose the skills by listing and reading files with tools.
                            If the task is arithmetic, use arithmetic-evaluator skill script.

                            $generatedSkillsPrompt
                            """.trimIndent()
                        )
                    },
                    model = model,
                    maxAgentIterations = 12,
                ),
                toolRegistry = ToolRegistry {
                    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
                    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
                    tool(pythonTool)
                },
                installFeatures = { install(ai.koog.agents.features.eventHandler.feature.EventHandler.Feature, eventHandlerConfig) }
            )

            val result = agent.run("How much is seven multiplied by eight?")

            withClue("Agent result should not be null for model $model when skills prompt is provided") {
                result.shouldNotBeNull()
            }
            withClue("Expected skills-discovery directory listing tool to be used for model $model") {
                state.actualToolCalls.shouldContain("__list_directory__")
            }
            withClue("Expected skills-discovery file reading tool to be used for model $model") {
                state.actualToolCalls.shouldContain("__read_file__")
            }
            withClue("Expected python execution tool to be used for arithmetic skill for model $model") {
                state.actualToolCalls.shouldContain("execute_python_script")
            }
            withClue("Arithmetic skill script should be executed for arithmetic request") {
                pythonTool.executedScripts.any { it.scriptPath.contains("arithmetic-evaluator/scripts/evaluate_arithmetic_exp.py") }
                    .shouldBe(true)
            }
            withClue("Arithmetic skill script should be executed with right arguments") {
                pythonTool.executedScripts.any { it.argument.replace("\\s".toRegex(), "").contains("7*8") }
                    .shouldBe(true)
            }
            withClue("Weather skill script should not be executed for arithmetic request") {
                pythonTool.executedScripts.any { it.scriptPath.contains("weather-retrieval/scripts/retrieve_weather.py") }
                    .shouldBe(false)
            }
            withClue("Final response should include the arithmetic result for model $model") {
                result.shouldContain("56")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun `integration test agent without skills does not activate skill tools`(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val pythonTool = ExecutePythonScriptTool()

        runWithTracking { eventHandlerConfig, state ->
            val agent = AIAgent(
                promptExecutor = getExecutor(model),
                strategy = singleRunStrategy(),
                agentConfig = AIAgentConfig(
                    prompt = prompt("without-skills") {
                        system(
                            """
                            You are a careful assistant.
                            """.trimIndent()
                        )
                    },
                    model = model,
                    maxAgentIterations = 6,
                ),
                toolRegistry = ToolRegistry {
                    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
                    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
                    tool(pythonTool)
                },
                installFeatures = { install(ai.koog.agents.features.eventHandler.feature.EventHandler.Feature, eventHandlerConfig) }
            )

            val result = agent.run("How much is seven multiplied by eight?")

            withClue("Agent result should not be null for model $model without skills prompt") {
                result.shouldNotBeNull()
            }
            withClue("Python execution tool must not be called when no skills are provided") {
                state.actualToolCalls.shouldNotContain("execute_python_script")
            }
            withClue("Directory listing skill-discovery tool must not be called when no skills are provided") {
                state.actualToolCalls.shouldNotContain("__list_directory__")
            }
            withClue("Read-file skill-discovery tool must not be called when no skills are provided") {
                state.actualToolCalls.shouldNotContain("__read_file__")
            }
            withClue("Final response should still contain the arithmetic answer without skills") {
                result.shouldContain("56")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("latestModels")
    fun `integration test agent uses weather retrieval skill for weather request`(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        val skillsRoot = skillsRootDirectory()
        val discoveredSkills = JVMFileSystemProvider.ReadOnly.discoverSkills(listOf(skillsRoot))
        val generatedSkillsPrompt = generateSkillsPrompt(discoveredSkills, SkillsPromptFormat.XML)
        val pythonTool = ExecutePythonScriptTool()

        runWithTracking { eventHandlerConfig, state ->
            val agent = AIAgent(
                promptExecutor = getExecutor(model),
                strategy = singleRunStrategy(),
                agentConfig = AIAgentConfig(
                    prompt = prompt("skills-with-tools-weather") {
                        system(
                            """
                            You are a careful assistant.
                            Use the available skills listed below.
                            Before using a skill script, disclose the skills by listing and reading files with tools.
                            If the task is weather-related, use weather-retrieval skill script.

                            $generatedSkillsPrompt
                            """.trimIndent()
                        )
                    },
                    model = model,
                    maxAgentIterations = 12,
                ),
                toolRegistry = ToolRegistry {
                    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
                    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
                    tool(pythonTool)
                },
                installFeatures = { install(ai.koog.agents.features.eventHandler.feature.EventHandler.Feature, eventHandlerConfig) }
            )

            val result = agent.run("What's the weather like in Paris today?")

            withClue("Agent result should not be null for model $model when weather request is made") {
                result.shouldNotBeNull()
            }
            withClue("Expected skills-discovery directory listing tool to be used for weather request for model $model") {
                state.actualToolCalls.shouldContain("__list_directory__")
            }
            withClue("Expected skills-discovery file reading tool to be used for weather request for model $model") {
                state.actualToolCalls.shouldContain("__read_file__")
            }
            withClue("Expected python execution tool to be used for weather skill for model $model") {
                state.actualToolCalls.shouldContain("execute_python_script")
            }
            withClue("Weather skill script should be executed for weather request") {
                pythonTool.executedScripts.any { it.scriptPath.contains("weather-retrieval/scripts/retrieve_weather.py") }
                    .shouldBe(true)
            }
            withClue("Weather skill script should be executed with city as parameter") {
                pythonTool.executedScripts.any { it.argument.contains("Paris", true) }
                    .shouldBe(true)
            }
            withClue("Arithmetic skill script should not be executed for weather request") {
                pythonTool.executedScripts.any { it.scriptPath.contains("arithmetic-evaluator/scripts/evaluate_arithmetic_exp.py") }
                    .shouldBe(false)
            }
        }
    }
}
