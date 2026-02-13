package ai.koog.agents.core.optimization

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.optimizableNode
import ai.koog.agents.core.optimization.optimizers.mipro.InstructionProposer
import ai.koog.agents.core.optimization.optimizers.mipro.InstructionProposerConfig
import ai.koog.agents.core.optimization.optimizers.mipro.TIPS
import ai.koog.agents.core.optimization.optimizers.utils.describeForOptimization
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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstructionProposerTest {

    private val trainset = listOf(
        Example(input = "What is 2+2?", label = "4"),
        Example(input = "What is 3+3?", label = "6"),
        Example(input = "What is 5+5?", label = "10"),
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

    private val noDescriptionStrategy = strategy("test-strategy-no-desc") {
        val thinking by optimizableNode(
            instruction = "Think step by step about the math question.",
        )
        val answer by optimizableNode(
            instruction = "Provide the final numeric answer.",
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
     * - DescribeProgram prompts → returns program description
     * - DescribeModule prompts → returns module description
     * - Instruction generation prompts → returns a generated instruction
     *
     * @param trackCalls If non-null, appends prompt names to this list for call tracking
     */
    private fun createMockExecutor(trackCalls: MutableList<String>? = null): PromptExecutor {
        return object : PromptExecutor by getMockExecutor(init = { }) {
            private fun respond(prompt: Prompt): List<Message.Response> {
                val systemContent = prompt.messages.firstOrNull()?.content ?: ""
                val userContent = prompt.messages.lastOrNull()?.content ?: ""
                val metaInfo = ResponseMetaInfo.create(Clock.System)

                // Dataset descriptor (observation)
                if ("write observations about trends" in systemContent) {
                    trackCalls?.add("dataset-descriptor")
                    if ("PRIOR OBSERVATIONS" in userContent) {
                        return listOf(Message.Assistant("Additional observation: the data contains math questions.", metaInfo))
                    }
                    return listOf(Message.Assistant("The dataset contains simple arithmetic questions with numeric answers.", metaInfo))
                }

                // Observation summarizer
                if ("summarize them into a brief" in systemContent) {
                    trackCalls?.add("observation-summarizer")
                    return listOf(Message.Assistant("This dataset contains arithmetic questions requiring numeric answers.", metaInfo))
                }

                // DescribeProgram
                if ("describe what task this program is designed to solve" in systemContent) {
                    trackCalls?.add("describe-program")
                    return listOf(Message.Assistant("This program solves arithmetic questions using a two-step reasoning approach.", metaInfo))
                }

                // DescribeModule
                if ("describe the role of the specified module" in systemContent) {
                    trackCalls?.add("describe-module")
                    val moduleName = when {
                        "thinking" in userContent -> "thinking"
                        "answer" in userContent -> "answer"
                        else -> "unknown"
                    }
                    return listOf(Message.Assistant("The $moduleName module handles its step in the reasoning pipeline.", metaInfo))
                }

                // Instruction generation
                if ("generate a new instruction" in systemContent) {
                    trackCalls?.add("generate-instruction")
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
        assertFalse(config.useInstructHistory)
        assertFalse(config.setHistoryRandomly)
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
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


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
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


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
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


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
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )


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

    @Test
    fun testUserProvidedProgramDescriptionSkipsLLMCall() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            programDescription = "User-provided program description",
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        // Should NOT have called describe-program since we provided a description
        assertTrue(
            "describe-program" !in calls,
            "Should not call DescribeProgram when user provides programDescription"
        )
    }

    @Test
    fun testUserProvidedNodeDescriptionSkipsLLMCall() = runBlocking {
        // twoNodeStrategy has descriptions on both nodes ("Reasoning node", "Answer node")
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        // Should NOT have called describe-module since both nodes have descriptions
        assertTrue(
            "describe-module" !in calls,
            "Should not call DescribeModule when nodes have descriptions"
        )
    }

    @Test
    fun testMissingDescriptionTriggersLLMGeneration() = runBlocking {
        // noDescriptionStrategy has no descriptions on nodes
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


        // Descriptions are now generated per-call (Gap 4), not during create()
        assertTrue(
            "describe-program" !in calls,
            "Should not call DescribeProgram during create() (now per-call)"
        )

        // Generate 1 candidate to trigger per-call describe calls
        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        // Should have called describe-program during proposal
        assertTrue(
            "describe-program" in calls,
            "Should call DescribeProgram per-call when no programDescription provided"
        )

        // Should have called describe-module for each node without a description
        val describeModuleCalls = calls.count { it == "describe-module" }
        assertEquals(2, describeModuleCalls, "Should call DescribeModule for each node without description")
    }

    @Test
    fun testModuleDescriptionAppearsInInstructionPrompt() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        // Use noDescriptionStrategy so LLM generates module descriptions
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


        val result = proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
        )

        // Verify instructions were generated
        assertEquals(2, result.size, "Should have instructions for 2 modules")
        for ((_, instructions) in result) {
            assertEquals(1, instructions.size)
            assertTrue(instructions.first().isNotBlank())
        }

        // The generate-instruction calls should have been made
        assertTrue(
            "generate-instruction" in calls,
            "Should have called instruction generation"
        )
    }

    @Test
    fun testProgramAwareDisabledSkipsAllDescriptionCalls() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val config = InstructionProposerConfig(
            programAware = false,
            useDatasetSummary = false,
        )
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        assertTrue("describe-program" !in calls, "Should not call DescribeProgram when programAware=false")
        assertTrue("describe-module" !in calls, "Should not call DescribeModule when programAware=false")
    }

    /**
     * Creates a mock executor that also captures all prompt user-content for inspection.
     */
    private fun createPromptCapturingExecutor(
        capturedPrompts: MutableList<Pair<String, String>>,
        trackCalls: MutableList<String>? = null,
    ): PromptExecutor {
        return object : PromptExecutor by getMockExecutor(init = { }) {
            private fun respond(prompt: Prompt): List<Message.Response> {
                val systemContent = prompt.messages.firstOrNull()?.content ?: ""
                val userContent = prompt.messages.lastOrNull()?.content ?: ""
                val metaInfo = ResponseMetaInfo.create(Clock.System)

                // Capture all prompts
                capturedPrompts.add(systemContent to userContent)

                // Dataset descriptor
                if ("write observations about trends" in systemContent) {
                    trackCalls?.add("dataset-descriptor")
                    if ("PRIOR OBSERVATIONS" in userContent) {
                        return listOf(Message.Assistant("Additional observation.", metaInfo))
                    }
                    return listOf(Message.Assistant("The dataset contains arithmetic questions.", metaInfo))
                }

                // Observation summarizer
                if ("summarize them into a brief" in systemContent) {
                    trackCalls?.add("observation-summarizer")
                    return listOf(Message.Assistant("Arithmetic questions with numeric answers.", metaInfo))
                }

                // DescribeProgram
                if ("describe what task this program is designed to solve" in systemContent) {
                    trackCalls?.add("describe-program")
                    return listOf(Message.Assistant("This program solves arithmetic questions.", metaInfo))
                }

                // DescribeModule
                if ("describe the role of the specified module" in systemContent) {
                    trackCalls?.add("describe-module")
                    return listOf(Message.Assistant("The module handles its step.", metaInfo))
                }

                // Instruction generation
                if ("generate a new instruction" in systemContent) {
                    trackCalls?.add("generate-instruction")
                    val moduleName = when {
                        "thinking" in userContent -> "thinking"
                        "answer" in userContent -> "answer"
                        else -> "unknown"
                    }
                    return listOf(Message.Assistant("Carefully analyze the $moduleName step.", metaInfo))
                }

                return listOf(Message.Assistant("Default mock response", metaInfo))
            }

            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> = respond(prompt)
            override suspend fun execute(prompt: Prompt, model: LLModel): List<Message.Response> = respond(prompt)
        }
    }

    // ---- Gap 1: programCode in instruction generation prompt ----

    @Test
    fun testPromptContainsProgramCodeAndProgramDescriptionSections() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        // Find instruction generation prompts
        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        assertTrue(instructionPrompts.isNotEmpty(), "Should have instruction generation prompts")

        for ((_, userContent) in instructionPrompts) {
            assertTrue("PROGRAM CODE:" in userContent, "Should contain PROGRAM CODE: section")
            assertTrue("PROGRAM DESCRIPTION:" in userContent, "Should contain PROGRAM DESCRIPTION: section")
            // Verify PROGRAM CODE comes before PROGRAM DESCRIPTION
            val codeIndex = userContent.indexOf("PROGRAM CODE:")
            val descIndex = userContent.indexOf("PROGRAM DESCRIPTION:")
            assertTrue(codeIndex < descIndex, "PROGRAM CODE should come before PROGRAM DESCRIPTION")
        }
    }

    @Test
    fun testPromptUsesModuleSectionName() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        for ((_, userContent) in instructionPrompts) {
            // Should use MODULE: not MODULE TO OPTIMIZE:
            assertTrue("MODULE:" in userContent, "Should contain MODULE: section")
            assertFalse("MODULE TO OPTIMIZE:" in userContent, "Should not contain old MODULE TO OPTIMIZE: name")
            // Should use PROGRAM DESCRIPTION: not PROGRAM STRUCTURE:
            assertFalse("PROGRAM STRUCTURE:" in userContent, "Should not contain old PROGRAM STRUCTURE: name")
        }
    }

    // ---- Gap 2: Previous instructions / instruction history ----

    @Test
    fun testPreviousInstructionsAppearsWhenHistoryEnabled() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val config = InstructionProposerConfig(
            useInstructHistory = true,
            useDatasetSummary = false,
            programAware = false,
        )
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )


        val previousInstructions = mapOf(
            "thinking" to listOf(
                "Think carefully" to 0.80,
                "Reason step by step" to 0.90,
                "Consider all options" to 0.70,
            ),
            "answer" to listOf(
                "Give final answer" to 0.85,
            ),
        )

        proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
            previousInstructions = previousInstructions,
        )

        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        assertTrue(instructionPrompts.isNotEmpty(), "Should have instruction generation prompts")

        for ((_, userContent) in instructionPrompts) {
            assertTrue(
                "PREVIOUS INSTRUCTIONS:" in userContent,
                "Should contain PREVIOUS INSTRUCTIONS: section when useInstructHistory=true and history is non-empty"
            )
            assertTrue("Score:" in userContent, "Should contain Score: entries")
        }
    }

    @Test
    fun testPreviousInstructionsAbsentWhenHistoryDisabled() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val config = InstructionProposerConfig(
            useInstructHistory = false,
            useDatasetSummary = false,
            programAware = false,
        )
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )


        val previousInstructions = mapOf(
            "thinking" to listOf("Think carefully" to 0.80),
        )

        proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
            previousInstructions = previousInstructions,
        )

        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        for ((_, userContent) in instructionPrompts) {
            assertFalse(
                "PREVIOUS INSTRUCTIONS:" in userContent,
                "Should not contain PREVIOUS INSTRUCTIONS: when useInstructHistory=false"
            )
        }
    }

    @Test
    fun testPreviousInstructionsAbsentWhenHistoryEmpty() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val config = InstructionProposerConfig(
            useInstructHistory = true,
            useDatasetSummary = false,
            programAware = false,
        )
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )


        // Empty history
        proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
            previousInstructions = emptyMap(),
        )

        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        for ((_, userContent) in instructionPrompts) {
            assertFalse(
                "PREVIOUS INSTRUCTIONS:" in userContent,
                "Should not contain PREVIOUS INSTRUCTIONS: when history is empty"
            )
        }
    }

    @Test
    fun testInstructionHistoryFormat() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val config = InstructionProposerConfig(
            useInstructHistory = true,
            useDatasetSummary = false,
            programAware = false,
        )
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
        )


        val previousInstructions = mapOf(
            "thinking" to listOf(
                "Instruction A" to 0.50,
                "Instruction B" to 0.90,
                "Instruction C" to 0.70,
            ),
        )

        proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
            previousInstructions = previousInstructions,
        )

        // Find the thinking-module instruction prompt
        val thinkingPrompt = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }.first { (_, user) -> "thinking" in user }

        val userContent = thinkingPrompt.second
        assertTrue("PREVIOUS INSTRUCTIONS:" in userContent)

        // Sorted by score desc, then reversed → ascending order: A(0.50), C(0.70), B(0.90)
        assertTrue("\"Instruction A\" | Score: 0.50" in userContent)
        assertTrue("\"Instruction C\" | Score: 0.70" in userContent)
        assertTrue("\"Instruction B\" | Score: 0.90" in userContent)

        val aIndex = userContent.indexOf("Instruction A")
        val cIndex = userContent.indexOf("Instruction C")
        val bIndex = userContent.indexOf("Instruction B")
        assertTrue(aIndex < cIndex, "A (0.50) should come before C (0.70)")
        assertTrue(cIndex < bIndex, "C (0.70) should come before B (0.90)")
    }

    // ---- Gap 3: setHistoryRandomly ----

    @Test
    fun testSetHistoryRandomlyTogglesHistoryInclusion() = runBlocking {
        // Use a seeded random that we can predict
        // Random(42).nextBoolean() returns a deterministic sequence
        val seed = 42
        val expectedFirstBool = Random(seed).nextBoolean()

        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val config = InstructionProposerConfig(
            setHistoryRandomly = true,
            useInstructHistory = false, // overridden by setHistoryRandomly
            useDatasetSummary = false,
            programAware = false,
        )
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            config = config,
            random = Random(seed),
        )


        val previousInstructions = mapOf(
            "thinking" to listOf("Some instruction" to 0.75),
            "answer" to listOf("Another instruction" to 0.80),
        )

        proposer.proposeInstructionsForProgram(
            demoCandidates = null,
            numCandidates = 1,
            previousInstructions = previousInstructions,
        )

        val instructionPrompts = capturedPrompts.filter { (sys, _) ->
            "generate a new instruction" in sys
        }

        // All prompts in this round should have the same history inclusion
        val hasHistory = instructionPrompts.all { (_, user) -> "PREVIOUS INSTRUCTIONS:" in user }
        val noHistory = instructionPrompts.all { (_, user) -> "PREVIOUS INSTRUCTIONS:" !in user }

        if (expectedFirstBool) {
            assertTrue(hasHistory, "With setHistoryRandomly and seed=$seed, history should be included")
        } else {
            assertTrue(noHistory, "With setHistoryRandomly and seed=$seed, history should be excluded")
        }
    }

    // ---- Gap 4: Per-call DescribeProgram/DescribeModule ----

    @Test
    fun testDescriptionsGeneratedPerCallNotDuringInitialize() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


        // After create(), no describe calls should have been made
        assertFalse(
            "describe-program" in calls,
            "describe-program should not be called during create()"
        )
        assertFalse(
            "describe-module" in calls,
            "describe-module should not be called during create()"
        )

        // Now generate instructions — this should trigger describe calls
        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        assertTrue(
            "describe-program" in calls,
            "describe-program should be called per-instruction"
        )
        assertTrue(
            "describe-module" in calls,
            "describe-module should be called per-instruction"
        )
    }

    @Test
    fun testPerCallDescribeCalledPerInstruction() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )


        // Generate 3 candidates → 3 calls per module × 2 modules = 6 describe-program, 6 describe-module
        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 3)

        val describeProgramCalls = calls.count { it == "describe-program" }
        val describeModuleCalls = calls.count { it == "describe-module" }

        // 2 modules × 3 candidates = 6 per-call describe-program calls
        assertEquals(6, describeProgramCalls, "describe-program should be called per (module, candidate) pair")
        // 2 modules × 3 candidates = 6 per-call describe-module calls
        assertEquals(6, describeModuleCalls, "describe-module should be called per (module, candidate) pair")
    }

    @Test
    fun testUserProvidedDescriptionsSkipLLMEvenPerCall() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = createMockExecutor(trackCalls = calls)
        val proposer = InstructionProposer.create(
            strategy = twoNodeStrategy, // has node descriptions
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
            programDescription = "User-provided description", // skips describe-program
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 3)

        assertFalse("describe-program" in calls, "User-provided programDescription should skip describe-program per-call")
        assertFalse("describe-module" in calls, "User-provided node descriptions should skip describe-module per-call")
    }

    // ---- Gap 5: programExample in describeModulePrompt ----

    @Test
    fun testDescribeModulePromptContainsProgramExample() = runBlocking {
        val capturedPrompts = mutableListOf<Pair<String, String>>()
        val executor = createPromptCapturingExecutor(capturedPrompts)
        val proposer = InstructionProposer.create(
            strategy = noDescriptionStrategy,
            trainset = trainset,
            promptExecutor = executor,
            llModel = llModel,
        )

        proposer.proposeInstructionsForProgram(demoCandidates = null, numCandidates = 1)

        // Find describe-module prompts
        val describeModulePrompts = capturedPrompts.filter { (sys, _) ->
            "describe the role of the specified module" in sys
        }

        assertTrue(describeModulePrompts.isNotEmpty(), "Should have describe-module prompts")

        for ((_, userContent) in describeModulePrompts) {
            assertTrue(
                "EXAMPLE OF PROGRAM IN USE:" in userContent,
                "describe-module prompt should contain EXAMPLE OF PROGRAM IN USE: section"
            )
        }
    }
}
