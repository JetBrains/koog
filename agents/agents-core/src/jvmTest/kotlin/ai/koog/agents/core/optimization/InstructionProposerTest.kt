package ai.koog.agents.core.optimization

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.optimizers.mipro.InstructionProposer
import ai.koog.agents.core.optimization.optimizers.mipro.InstructionProposerConfig
import ai.koog.agents.core.optimization.optimizers.mipro.TIPS
import ai.koog.agents.core.optimization.util.describeForOptimization
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstructionProposerTest {

    private val trainset = listOf(
        Example(
            data = mapOf("question" to "What is 2+2?", "answer" to "4"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 3+3?", "answer" to "6"),
            labelKey = "answer"
        ),
        Example(
            data = mapOf("question" to "What is 5+5?", "answer" to "10"),
            labelKey = "answer"
        ),
    )

    private val twoNodeStrategy = strategy("test-strategy") {
        val thinking by optimizableNode(
            instruction = "Think step by step about the math question.",
            description = "Reasoning node",
        )
        val answer by optimizableNode(
            instruction = "Provide the final numeric answer.",
            description = "Answer node",
        )

        edge(nodeStart forwardTo thinking)
        edge(thinking forwardTo answer)
        edge(answer forwardTo nodeFinish)
    }

    private val llModel = OpenAIModels.Chat.GPT5Nano

    /**
     * Creates a mock executor that returns canned responses based on prompt content.
     *
     * - Observation prompts → returns observation text
     * - Summarizer prompts → returns summary text
     * - Instruction generation prompts → returns a generated instruction
     */
    private fun createMockExecutor(): PromptExecutor {
        val baseExecutor = getMockExecutor {
            mockLLMAnswer("unused")
        }

        return object : PromptExecutor by baseExecutor {
            private fun respond(prompt: Prompt): List<Message.Response> {
                val systemContent = prompt.messages.firstOrNull()?.content ?: ""
                val userContent = prompt.messages.lastOrNull()?.content ?: ""
                val metaInfo = ResponseMetaInfo.create(Clock.System)

                // Dataset descriptor (observation)
                if ("write observations about trends" in systemContent) {
                    if ("PRIOR OBSERVATIONS" in userContent) {
                        return listOf(Message.Assistant("Additional observation: the data contains math questions.", metaInfo))
                    }
                    return listOf(Message.Assistant("The dataset contains simple arithmetic questions with numeric answers.", metaInfo))
                }

                // Observation summarizer
                if ("summarize them into a brief" in systemContent) {
                    return listOf(Message.Assistant("This dataset contains arithmetic questions requiring numeric answers.", metaInfo))
                }

                // Instruction generation
                if ("generate a new instruction" in systemContent) {
                    // Include the module name from the user content for distinguishability
                    val moduleName = when {
                        "thinking" in userContent -> "thinking"
                        "answer" in userContent -> "answer"
                        else -> "unknown"
                    }
                    return listOf(Message.Assistant("Carefully analyze the $moduleName step and provide a clear response.", metaInfo))
                }

                return listOf(Message.Assistant("Default mock response", metaInfo))
            }

            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> = respond(prompt)
            override suspend fun execute(prompt: Prompt, model: LLModel): List<Message.Response> = respond(prompt)
        }
    }

    @Test
    fun testConfigDefaults() {
        val config = InstructionProposerConfig()
        assertTrue(config.useDatasetSummary)
        assertTrue(config.programAware)
        assertTrue(config.useTaskDemos)
        assertEquals(3, config.numDemosInContext)
        assertTrue(config.useTip)
        assertTrue(config.setTipRandomly)
    }

    @Test
    fun testConfigCustomization() {
        val config = InstructionProposerConfig(
            useDatasetSummary = false,
            programAware = false,
            useTaskDemos = false,
            numDemosInContext = 5,
            useTip = false,
            setTipRandomly = false,
        )
        assertEquals(false, config.useDatasetSummary)
        assertEquals(false, config.programAware)
        assertEquals(false, config.useTaskDemos)
        assertEquals(5, config.numDemosInContext)
        assertEquals(false, config.useTip)
        assertEquals(false, config.setTipRandomly)
    }

    @Test
    fun testTipsDictionaryKeysAndValues() {
        assertEquals(6, TIPS.size)
        assertTrue("none" in TIPS)
        assertTrue("creative" in TIPS)
        assertTrue("simple" in TIPS)
        assertTrue("description" in TIPS)
        assertTrue("high_stakes" in TIPS)
        assertTrue("persona" in TIPS)

        assertEquals("", TIPS["none"])
        assertTrue(TIPS["creative"]!!.isNotBlank())
        assertTrue(TIPS["persona"]!!.contains("You are a"))
    }

    @Test
    fun testCorrectNumberOfCandidatesPerModule() = runBlocking {
        val executor = createMockExecutor()
        val proposer = InstructionProposer(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.initialize()

        val numCandidates = 4
        val result = proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = numCandidates,
        )

        // Should have entries for both nodes
        assertEquals(2, result.size, "Should have instructions for 2 modules")
        assertTrue("thinking" in result, "Should have thinking module")
        assertTrue("answer" in result, "Should have answer module")

        // Each module should have exactly numCandidates instructions
        assertEquals(numCandidates, result["thinking"]!!.size, "thinking should have $numCandidates candidates")
        assertEquals(numCandidates, result["answer"]!!.size, "answer should have $numCandidates candidates")
    }

    @Test
    fun testWithDemoCandidatesProducesNonBlankInstructions() = runBlocking {
        val executor = createMockExecutor()
        val proposer = InstructionProposer(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.initialize()

        val demoCandidates = mapOf(
            "thinking" to listOf(
                listOf(Demonstration("What is 2+2?", "Let me think: 2+2=4", isBootstrapped = true)),
                listOf(Demonstration("What is 3+3?", "Let me think: 3+3=6", isBootstrapped = true)),
                listOf(Demonstration("What is 5+5?", "Let me think: 5+5=10", isBootstrapped = false)),
            ),
            "answer" to listOf(
                listOf(Demonstration("Let me think: 2+2=4", "4", isBootstrapped = true)),
                listOf(Demonstration("Let me think: 3+3=6", "6", isBootstrapped = true)),
                listOf(Demonstration("Let me think: 5+5=10", "10", isBootstrapped = false)),
            ),
        )

        val result = proposer.proposeInstructionsForProgram(
            demoCandidates = demoCandidates,
            numCandidates = 3,
        )

        for ((moduleName, instructions) in result) {
            for ((index, instruction) in instructions.withIndex()) {
                assertTrue(
                    instruction.isNotBlank(),
                    "Instruction $index for module '$moduleName' should not be blank"
                )
            }
        }
    }

    @Test
    fun testRespectsDemoSetCountLimit() = runBlocking {
        val executor = createMockExecutor()
        val proposer = InstructionProposer(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.initialize()

        // Only 2 demo sets available
        val demoCandidates = mapOf(
            "thinking" to listOf(
                listOf(Demonstration("Q1", "T1", isBootstrapped = true)),
                listOf(Demonstration("Q2", "T2", isBootstrapped = true)),
            ),
            "answer" to listOf(
                listOf(Demonstration("T1", "A1", isBootstrapped = true)),
                listOf(Demonstration("T2", "A2", isBootstrapped = true)),
            ),
        )

        // Request 5 candidates, but only 2 demo sets → should get min(2, 5) = 2
        val result = proposer.proposeInstructionsForProgram(
            demoCandidates = demoCandidates,
            numCandidates = 5,
        )

        assertEquals(2, result["thinking"]!!.size, "Should be limited to 2 demo sets")
        assertEquals(2, result["answer"]!!.size, "Should be limited to 2 demo sets")
    }

    @Test
    fun testWithAllFeaturesDisabledStillGeneratesInstructions() = runBlocking {
        val executor = createMockExecutor()
        val config = InstructionProposerConfig(
            useDatasetSummary = false,
            programAware = false,
            useTaskDemos = false,
            useTip = false,
        )
        val proposer = InstructionProposer(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )

        proposer.initialize()

        val result = proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 2,
        )

        assertEquals(2, result.size)
        for ((_, instructions) in result) {
            assertEquals(2, instructions.size)
            for (instruction in instructions) {
                assertTrue(instruction.isNotBlank())
            }
        }
    }

    @Test
    fun testDescribeForOptimizationOutput() {
        val description = twoNodeStrategy.describeForOptimization()

        assertTrue("test-strategy" in description, "Should contain strategy name")
        assertTrue("thinking" in description, "Should contain thinking node")
        assertTrue("answer" in description, "Should contain answer node")
        assertTrue("Reasoning node" in description, "Should contain thinking description")
        assertTrue("Answer node" in description, "Should contain answer description")
        assertTrue("Think step by step" in description, "Should contain thinking instruction")
        assertTrue("Provide the final numeric answer" in description, "Should contain answer instruction")
    }
}
