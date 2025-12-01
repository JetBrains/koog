package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.asAssistantMessage
import ai.koog.agents.core.dsl.extension.containsToolCalls
import ai.koog.agents.core.dsl.extension.executeMultipleTools
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.agents.core.dsl.extension.sendMultipleToolResults
import ai.koog.agents.core.feature.TestFeature
import ai.koog.agents.core.feature.mock.TestFeatureMessageProcessor
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.SubgraphWithTaskUtils
import ai.koog.agents.ext.agent.subtask
import ai.koog.agents.ext.agent.subtaskWithVerification
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class FunctionalAIAgentTest {
    @Test
    fun mixedTools_thenAssistantMessage() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val assistantResponse = "Hey, I want to call following tools:"
        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = true) {
            mockLLMAnswer(assistantResponse) onRequestContains assistantResponse
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse

            val toolCalls = listOf(
                CreateTool to CreateTool.Args("solve"),
                CreateTool to CreateTool.Args("solve2"),
                CreateTool to CreateTool.Args("solve3"),
            )
            val assistantResponses = listOf(assistantResponse)
            mockLLMMixedResponse(toolCalls, assistantResponses) onRequestEquals "Solve task"
        }

        val agent = AIAgent<String, String>(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            strategy = functionalStrategy { inputParam ->
                var responses = requestLLMMultiple(inputParam)

                while (responses.containsToolCalls()) {
                    val tools = extractToolCalls(responses)
                    val results = executeMultipleTools(tools)
                    responses = sendMultipleToolResults(results)
                }

                responses.single().asAssistantMessage().content
            },
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(3, actualToolCalls.size)
        assertEquals(assistantResponse, result)
    }

    @Test
    fun assistantOnly_thenFinalMessage() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("Task solved!!") onRequestContains "Solve task"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        // Install EventHandler feature via the featureContext builder overload
        val agent = AIAgent<String, String>(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            strategy = functionalStrategy { inputParam ->
                val resp = llm.writeSession {
                    appendPrompt { user(inputParam) }
                    requestLLM()
                }
                resp.content
            },
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry,
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(0, actualToolCalls.size)
        assertEquals("Task solved!!", result)
    }

    @Test
    fun singleTool_thenFollowUpMessage() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tool(CreateTool)
        }

        val mockLLMApi = getMockExecutor {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMAnswer("Tools called!") onRequestContains "created"
            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }

        val agent = AIAgent(
            mockLLMApi,
            OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry,
            strategy = functionalStrategy { inputParam: String ->
                var responses = requestLLMMultiple(inputParam)

                while (responses.containsToolCalls()) {
                    val tools = extractToolCalls(responses)
                    val results = executeMultipleTools(tools)
                    responses = sendMultipleToolResults(results)
                }

                responses.single().asAssistantMessage().content
            }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        assertEquals(1, actualToolCalls.size)
        assertEquals("Tools called!", result)
    }

    enum class MissionProfile { ORBITAL, LUNAR, INTERPLANETARY, LANDER }

    @Serializable
    data class SchemaDescriptor(
        val id: String,
        val version: String = "1.0.0",
        val format: String = "json",
        val url: String? = null
    )

    @Serializable
    data class Constraints(
        @property:LLMDescription("The maximum amount of fuel the vehicle can carry.")
        val maxGLoad: Double = 3.0,
        @property:LLMDescription("Maximum radiation exposure (in Joules per second) of the vehicle.")
        val maxRadiationSv: Double = 0.1,
        @property:LLMDescription("Maximum operating temperature (in degrees Celsius) of the vehicle.")
        val maxOperatingTempC: Int = 120
    )

    @Serializable
    data class Architecture(
        val name: String,
        val schema: SchemaDescriptor,
        val version: String = "1.0",
        val missionProfile: MissionProfile = MissionProfile.ORBITAL,
        val constraints: Constraints = Constraints()
    )

    enum class FuelType { CHEMICAL, ION, NUCLEAR, ELECTRIC }
    enum class Material { ALUMINUM_LITHIUM, TITANIUM, CARBON_COMPOSITE, STAINLESS_STEEL }
    enum class ComponentStatus { DESIGNED, BUILT, TESTED, QUALIFIED }

    @Serializable
    data class Engine(
        val name: String,
        val model: String = "X-1",
        val fuel: FuelType = FuelType.CHEMICAL,
        val maxThrustKN: Double = 0.0,
        val specificImpulseS: Int = 0,
        val massKg: Double = 0.0,
        val powerRequirementKW: Double? = null,
        val status: ComponentStatus = ComponentStatus.DESIGNED
    )

    @Serializable
    data class Body(
        val name: String,
        val hullMaterial: Material = Material.ALUMINUM_LITHIUM,
        val dryMassKg: Double = 0.0,
        val maxCargoKg: Double = 0.0,
        val crewCapacity: Int = 0,
        val heatShieldRating: String? = null,
        val status: ComponentStatus = ComponentStatus.DESIGNED
    )

    @Serializable
    enum class InterfaceType { ENGINE_MOUNT, POWER_BUS, DATA_BUS }

    @Serializable
    data class InterfaceSpec(
        val type: InterfaceType,
        val version: String,
        val notes: String? = null
    )

    @Serializable
    data class Assembly(
        val engine: Engine,
        val body: Body
    ) {
        val totalDryMassKg: Double get() = engine.massKg + body.dryMassKg
    }

    @Serializable
    data class Spacecraft(
        val engine: Engine,
        val body: Body,
        val architecture: Architecture,
        val serial: String = "<serial>",
        val notes: String? = null
    ) {
        val dryMassKg: Double get() = engine.massKg + body.dryMassKg
        val missionProfile: MissionProfile get() = architecture.missionProfile
    }

    @Serializable
    data class QAReport(val correct: Boolean, val feedback: String) {
        val feedbackIfIncorrect: String? = if (correct) null else feedback
    }

    @Serializable
    data class FullQAReport(
        @property:LLMDescription("The report for the engine component.")
        val engineReport: QAReport,
        @property:LLMDescription("The report for the body component.")
        val bodyReport: QAReport,
        @property:LLMDescription("The report about the architecture of the spacecraft.")
        val architectureReport: QAReport
    ) {
        val isCorrect: Boolean = engineReport.correct && bodyReport.correct && architectureReport.correct
    }

    @Serializable
    data class SimpleOut(val value: String)

    object QATools {
        object TestEngine : SimpleTool<Spacecraft>() {
            override val argsSerializer: KSerializer<Spacecraft> = Spacecraft.serializer()

            override val description: String = "Performs testing of the spacecraft engine."

            override suspend fun doExecute(args: Spacecraft): String = "Engine is good"
        }

        object TestBody : SimpleTool<Spacecraft>() {
            override val argsSerializer: KSerializer<Spacecraft> = Spacecraft.serializer()

            override val description: String = "Performs testing of the spacecraft bofy."

            override suspend fun doExecute(args: Spacecraft): String = "Body is good"
        }

        object TestBuild : SimpleTool<Spacecraft>() {
            override val argsSerializer: KSerializer<Spacecraft> = Spacecraft.serializer()

            override val description: String = "Tests how spacecraft is built."

            override suspend fun doExecute(args: Spacecraft): String =
                "Spacecraft is built badly... Engine is too big for the body"
        }

        val tools = listOf(TestEngine, TestBody, TestBuild)
    }

    // Define sample tools for subtasks, similar in spirit to QATools so tool lists are not empty
    object ArchitectureTools {
        object AnalyzeRequirements : SimpleTool<String>() {
            override val argsSerializer: KSerializer<String> = String.serializer()
            override val description: String = "Analyzes high-level mission requirements."
            override suspend fun doExecute(args: String): String = "Requirements analyzed: $args"
        }

        object DraftArchitecture : SimpleTool<Architecture>() {
            override val argsSerializer: KSerializer<Architecture> = Architecture.serializer()
            override val description: String = "Drafts an initial spacecraft architecture proposal."
            override suspend fun doExecute(args: Architecture): String = "Drafted architecture: ${'$'}{args.name}"
        }

        val tools: List<Tool<*, *>> = listOf(AnalyzeRequirements, DraftArchitecture)
    }

    object BuildEngineTools {
        object EstimateThrust : SimpleTool<Architecture>() {
            override val argsSerializer: KSerializer<Architecture> = Architecture.serializer()
            override val description: String = "Estimates required thrust for the given architecture."
            override suspend fun doExecute(args: Architecture): String = "Estimated thrust for ${'$'}{args.name}"
        }

        object SelectFuelType : SimpleTool<Architecture>() {
            override val argsSerializer: KSerializer<Architecture> = Architecture.serializer()
            override val description: String = "Selects suitable fuel type based on mission profile and constraints."
            override suspend fun doExecute(args: Architecture): String = "Fuel selected for ${'$'}{args.name}"
        }

        val tools: List<Tool<*, *>> = listOf(EstimateThrust, SelectFuelType)
    }

    object BuildBodyTools {
        object ComputeMassBudget : SimpleTool<Architecture>() {
            override val argsSerializer: KSerializer<Architecture> = Architecture.serializer()
            override val description: String = "Computes mass budget for the spacecraft body."
            override suspend fun doExecute(args: Architecture): String = "Mass budget computed for ${'$'}{args.name}"
        }

        object ChooseMaterial : SimpleTool<Architecture>() {
            override val argsSerializer: KSerializer<Architecture> = Architecture.serializer()
            override val description: String = "Chooses hull material given constraints."
            override suspend fun doExecute(args: Architecture): String = "Material chosen for ${'$'}{args.name}"
        }

        val tools: List<Tool<*, *>> = listOf(ComputeMassBudget, ChooseMaterial)
    }

    object AssemblyTools {
        object CheckInterfaces : SimpleTool<Assembly>() {
            override val argsSerializer: KSerializer<Assembly> = Assembly.serializer()
            override val description: String = "Checks mechanical, power, and data interfaces between components."
            override suspend fun doExecute(args: Assembly): String =
                "Interfaces check passed for engine ${'$'}{args.engine.name} and body ${'$'}{args.body.name}"
        }

        object ComputeDryMass : SimpleTool<Assembly>() {
            override val argsSerializer: KSerializer<Assembly> = Assembly.serializer()
            override val description: String = "Computes total dry mass of the assembly."
            override suspend fun doExecute(args: Assembly): String = "Dry mass: ${'$'}{args.totalDryMassKg} kg"
        }

        val tools: List<Tool<*, *>> = listOf(CheckInterfaces, ComputeDryMass)
    }

    @Test
    fun `test_complex_subtasks_multistep_no_parallel_tools`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry {
            tools(QATools.tools)
        }

        val chosenArchitecture = Architecture(
            name = "Starship",
            schema = SchemaDescriptor(id = "arch-1"),
            missionProfile = MissionProfile.ORBITAL,
            constraints = Constraints()
        )
        val chosenEngine = Engine(
            name = "Raptor",
            model = "X-1",
            fuel = FuelType.CHEMICAL,
            maxThrustKN = 1000.0,
            specificImpulseS = 330,
            massKg = 2000.0,
            powerRequirementKW = null,
            status = ComponentStatus.DESIGNED
        )
        val chosenBody = Body(
            name = "Hull",
            hullMaterial = Material.STAINLESS_STEEL,
            dryMassKg = 8000.0,
            maxCargoKg = 100000.0,
            crewCapacity = 0,
            heatShieldRating = "PICA-Next",
            status = ComponentStatus.DESIGNED
        )

        val enginePromptExact = "Create the engine for the given architecture: $chosenArchitecture"
        val bodyPromptExact = "Create the body for the given architecture: $chosenArchitecture"
        val productV1 = Spacecraft(
            engine = chosenEngine,
            body = chosenBody,
            architecture = chosenArchitecture,
            serial = "SN-1",
            notes = null
        )

        var qaAttempt = 0

        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = false) {
            // Design architecture subtask - match exact first request
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<Architecture>(),
                chosenArchitecture
            ) onRequestEquals "Create the architecture for the following machinery: Solve task"
            // Build engine/body subtasks - match exact composed prompts
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<Engine>(),
                chosenEngine
            ) onRequestEquals enginePromptExact
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<Engine>(),
                chosenEngine
            ) onRequestContains "Create the engine for the given architecture:"
            mockLLMToolCall(SubgraphWithTaskUtils.finishTool<Body>(), chosenBody) onRequestEquals bodyPromptExact
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<Body>(),
                chosenBody
            ) onRequestContains "Create the body for the given architecture:"
            // Assembly subtask
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<Spacecraft>(),
                productV1
            ) onRequestContains "Assemble the product"
            // QA subtask first attempt: not correct
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<FullQAReport>(),
                FullQAReport(
                    engineReport = QAReport(true, "OK"),
                    bodyReport = QAReport(false, "Engine is too big for the body"),
                    architectureReport = QAReport(true, "OK")
                )
            ) onCondition { input -> input.contains("Verify the product") && qaAttempt++ == 0 }
            // QA subtask second attempt: correct
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<FullQAReport>(),
                FullQAReport(
                    engineReport = QAReport(true, "OK"),
                    bodyReport = QAReport(true, "OK"),
                    architectureReport = QAReport(true, "OK")
                )
            ) onCondition { input -> input.contains("Verify the product") && qaAttempt > 0 }

            mockLLMAnswer("I don't know how to answer that.").asDefaultResponse
        }

        val agent = AIAgent(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            strategy = functionalStrategy<String, Spacecraft> { input ->
                var qaReport: FullQAReport? = null
                var product: Spacecraft? = null

                while (qaReport?.isCorrect != true) {
                    val architecture = designArchitecture(
                        input = input,
                        additionalInfo = qaReport?.architectureReport?.feedbackIfIncorrect
                    )
                    val engine = buildEngine(
                        architecture = architecture,
                        additionalInfo = qaReport?.engineReport?.feedbackIfIncorrect
                    )
                    val body = buildBody(
                        architecture = architecture,
                        additionalInfo = qaReport?.bodyReport?.feedbackIfIncorrect
                    )

                    product = subtask<Assembly, Spacecraft>(
                        input = Assembly(engine, body),
                        tools = AssemblyTools.tools,
                        llmModel = OllamaModels.Meta.LLAMA_4,
                        runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
                    ) {
                        "Assemble the product: $it"
                    }

                    qaReport = subtask<Spacecraft, FullQAReport>(
                        input = product,
                        tools = QATools.tools,
                        runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
                    ) {
                        "Verify the product is built correctly: $it"
                    }

                    if (qaReport.isCorrect) break
                }

                product!!
            },
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = testToolRegistry
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("Solve task")

        // Since finish tool calls are handled internally, no external tool executions are expected
        assertEquals(0, actualToolCalls.size)
        assertEquals("SN-1", result.serial)
        assertEquals("Raptor", result.engine.name)
        assertEquals("Hull", result.body.name)
        assertEquals("Starship", result.architecture.name)
    }

    private suspend fun AIAgentFunctionalContext.buildBody(
        architecture: Architecture,
        additionalInfo: String? = null
    ): Body = subtask<Architecture, Body>(
        input = architecture,
        tools = BuildBodyTools.tools,
        llmModel = GoogleModels.Gemini2_0Flash,
        runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
    ) {
        "Create the body for the given architecture: $it" +
            (additionalInfo?.let { "Additional feedback: $additionalInfo" } ?: "")
    }

    private suspend fun AIAgentFunctionalContext.buildEngine(
        architecture: Architecture,
        additionalInfo: String? = null
    ): Engine = subtask<Architecture, Engine>(
        input = architecture,
        tools = BuildEngineTools.tools,
        llmModel = AnthropicModels.Sonnet_4_5,
        runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
    ) {
        "Create the engine for the given architecture: $it" +
            (additionalInfo?.let { "Additional feedback: $additionalInfo" } ?: "")
    }

    private suspend fun AIAgentFunctionalContext.designArchitecture(
        input: String,
        additionalInfo: String? = null
    ): Architecture = subtask<String, Architecture>(
        input = input,
        tools = ArchitectureTools.tools,
        llmModel = OpenAIModels.Chat.GPT5,
        runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
    ) {
        "Create the architecture for the following machinery: $input" +
            (additionalInfo?.let { "Additional feedback: $additionalInfo" } ?: "")
    }

    @Test
    fun `subtask_default_sequential_finish_only`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = false) {
            // The subtask should immediately call the finish tool in SEQUENTIAL (multi-tool) mode
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<SimpleOut>(),
                SimpleOut("done-seq")
            ) onRequestContains "Do simple subtask:"

            mockLLMAnswer("default").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLMApi,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = ToolRegistry.EMPTY,
            strategy = functionalStrategy<String, SimpleOut> { input ->
                subtask<String, SimpleOut>(
                    input = input,
                    tools = null, // no extra tools
                    runMode = ToolCalls.SEQUENTIAL
                ) {
                    "Do simple subtask: $it"
                }
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("input-1")
        assertEquals("done-seq", result.value)
        // finish tool is executed internally, so external tool executions list should be empty
        assertEquals(0, actualToolCalls.size)
    }

    @Test
    fun `subtask_sequential_with_normal_tool_then_finish`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val testToolRegistry = ToolRegistry { tool(DummyTool) }

        val mockLLMApi = getMockExecutor(testToolRegistry, handleLastAssistantMessage = false) {
            // First, LLM asks to call a normal tool, then after tool results it calls finish tool
            mockLLMToolCall(
                listOf(
                    DummyTool to Unit // first response: call normal tool
                )
            ) onRequestEquals "Compose task with tool: seed-X"

            // After tool result is sent back to LLM, it should call finish tool
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<SimpleOut>(),
                SimpleOut("final-from-finish")
            ) onRequestContains "DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD."

            mockLLMAnswer("default").asDefaultResponse
        }

        val agent = AIAgent(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            strategy = functionalStrategy<String, SimpleOut> { input ->
                subtask<String, SimpleOut>(
                    input = input,
                    tools = listOf(DummyTool),
                    runMode = ToolCalls.SEQUENTIAL
                ) {
                    "Compose task with tool: $it"
                }
            },
            toolRegistry = testToolRegistry
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("seed-X")
        assertEquals("final-from-finish", result.value)
        // Only the normal tool goes through environment, finish tool is internal
        assertEquals(1, actualToolCalls.size)
        // Verify that DummyTool has been called once with Unit args
        assertEquals("kotlin.Unit", actualToolCalls.first())
    }

    @Test
    fun `subtask_parallel_finish_only`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = false) {
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<SimpleOut>(),
                SimpleOut("done-par")
            ) onRequestContains "Parallel subtask:"

            mockLLMAnswer("default").asDefaultResponse
        }

        val agent = AIAgent(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            strategy = functionalStrategy<String, SimpleOut> { input ->
                subtask<String, SimpleOut>(
                    input = input,
                    tools = null,
                    runMode = ToolCalls.PARALLEL
                ) {
                    "Parallel subtask: $it"
                }
            }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("input-2")
        assertEquals("done-par", result.value)
        assertEquals(0, actualToolCalls.size)
    }

    @Test
    fun `subtask_single_run_sequential_finish_only`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = false) {
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<SimpleOut>(),
                SimpleOut("done-single")
            ) onRequestContains "Single-run subtask:"

            mockLLMAnswer("default").asDefaultResponse
        }

        val agent = AIAgent(
            systemPrompt = "You are helpful",
            promptExecutor = mockLLMApi,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            strategy = functionalStrategy<String, SimpleOut> { input ->
                subtask<String, SimpleOut>(
                    input = input,
                    tools = null,
                    runMode = ToolCalls.SINGLE_RUN_SEQUENTIAL
                ) {
                    "Single-run subtask: $it"
                }
            }
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("input-3")
        assertEquals("done-single", result.value)
        assertEquals(0, actualToolCalls.size)
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `subtask_withVerification_success`() = runTest {
        val actualToolCalls = mutableListOf<String>()

        val mockLLMApi = getMockExecutor(handleLastAssistantMessage = false) {
            mockLLMToolCall(
                SubgraphWithTaskUtils.finishTool<ai.koog.agents.ext.agent.CriticResultFromLLM>(),
                ai.koog.agents.ext.agent.CriticResultFromLLM(isCorrect = true, feedback = "OK")
            ) onRequestContains "Judge this:"

            mockLLMAnswer("default").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLMApi,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = ToolRegistry.EMPTY,
            strategy = functionalStrategy<String, ai.koog.agents.ext.agent.CriticResult<String>> { input ->
                subtaskWithVerification(
                    input = input,
                    runMode = ToolCalls.SEQUENTIAL
                ) {
                    "Judge this: $it"
                }
            },
            systemPrompt = "You are helpful"
        ) {
            install(EventHandler) {
                onToolCallStarting { eventContext -> actualToolCalls += eventContext.toolArgs.toString() }
            }
        }

        val result = agent.run("case-A")
        assertEquals(true, result.successful)
        assertEquals("OK", result.feedback)
        assertEquals("case-A", result.input)
        assertEquals(0, actualToolCalls.size)
    }

    @Test
    fun testFunctionalAgentFeatureProcessorsClosedAfterRun() = runTest {
        val model = OllamaModels.Meta.LLAMA_3_2
        val strategy = functionalStrategy<String, String> { it }

        val testFeatureMessageProcessor = TestFeatureMessageProcessor()

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            llmModel = model,
            strategy = strategy,
            systemPrompt = "You are helpful"
        ) {
            install(TestFeature) {
                addMessageProcessor(testFeatureMessageProcessor)
            }
        }

        agent.run("Test input")
        assertFalse(
            testFeatureMessageProcessor.isOpen.value,
            "Feature processors should be closed after run"
        )
    }
}
