package ai.grazie.code.agents.example.memory

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.example.memory.tools.BashTool
import ai.grazie.code.agents.example.memory.tools.CodeAnalysisTool
import ai.grazie.code.agents.example.memory.tools.FileSearchTool
import ai.grazie.code.agents.local.memory.model.*
import ai.grazie.code.agents.local.memory.providers.AgentMemoryProvider
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals


/**
 * Tests for the ProjectAnalyzerAgent.
 * These tests verify that the agent correctly analyzes projects and stores information in memory.
 */
class ProjectAnalyzerTest {
    object MemorySubjects {
        /**
         * Information specific to the local machine environment
         * Examples: Installed tools, SDKs, OS configuration, available commands
         */
        @Serializable
        data object Machine : MemorySubject() {
            override val name: String = "machine"
            override val promptDescription: String = "Technical environment (installed tools, package managers, packages, SDKs, OS, etc.)"
            override val priorityLevel: Int = 1
        }

        /**
         * Information specific to the current user
         * Examples: Preferences, settings, authentication tokens
         */
        @Serializable
        data object User : MemorySubject() {
            override val name: String = "user"
            override val promptDescription: String = "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
            override val priorityLevel: Int = 2
        }

        /**
         * Information specific to the current project
         * Examples: Build configuration, dependencies, code style rules
         */
        @Serializable
        data object Project : MemorySubject() {
            override val name: String = "project"
            override val promptDescription: String = "Project details, requirements, and constraints, dependencies, folders, technologies, modules, documentation, etc."
            override val priorityLevel: Int = 3
        }

        /**
         * Information shared across an organization
         * Examples: Coding standards, shared configurations, team practices
         */
        @Serializable
        data object Organization : MemorySubject() {
            override val name: String = "organization"
            override val promptDescription: String = "Organization structure and policies"
            override val priorityLevel: Int = 4
        }
    }


    /**
     * Test memory provider that stores facts in memory for testing.
     */
    class TestMemoryProvider : AgentMemoryProvider {
        val facts = mutableMapOf<String, MutableList<Fact>>()

        override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
            val key = "${subject}_${scope}"
            facts.getOrPut(key) { mutableListOf() }.add(fact)
        }

        override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
            val key = "${subject}_${scope}"
            return facts[key]?.filter { it.concept == concept } ?: emptyList()
        }

        override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
            val key = "${subject}_${scope}"
            return facts[key] ?: emptyList()
        }

        override suspend fun loadByDescription(
            description: String,
            subject: MemorySubject,
            scope: MemoryScope
        ): List<Fact> {
            val key = "${subject}_${scope}"
            return facts[key]?.filter { it.concept.description.contains(description) } ?: emptyList()
        }

        fun clear() {
            facts.clear()
        }
    }

    /**
     * Mock LLM executor that returns predefined responses for testing.
     */
    class MockLLMExecutor : PromptExecutor {
        // Configure responses for different prompts
        val responseForEnvironmentInfo = "I'll analyze the environment information."
        val responseForProjectDependencies = "I'll analyze the project dependencies."
        val responseForProjectStructure = "I'll analyze the project structure."
        val responseForCodeStyle = "I'll analyze the code style."

        // Track which tools were called
        var bashToolCallsCount = 0
        var fileSearchToolCallsCount = 0
        var codeAnalysisToolCallsCount = 0

        fun clear() {
            bashToolCallsCount = 0
            fileSearchToolCallsCount = 0
            codeAnalysisToolCallsCount = 0
        }

        val toolCallsCount: Int get() = bashToolCallsCount + fileSearchToolCallsCount + codeAnalysisToolCallsCount

        override suspend fun execute(prompt: Prompt, model: LLModel): String {
            return handlePrompt(prompt).content
        }

        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
            val messages = prompt.messages.map { it.content }

            // If we should use a tool, return a tool call
            val toolCall = if (tools.isNotEmpty()) {
                var result: Message.Tool.Call? = null

                for (toolToUse in tools) {
                    when (toolToUse.name) {
                        "bash" -> {
                            if (bashToolCallsCount++ >= 4) continue
                            result = when (bashToolCallsCount) {
                                1 -> {
                                    if (messages.any { it.contains("OS x86_64 GNU/Linux") }) continue
                                    Message.Tool.Call(
                                        id = "1",
                                        tool = toolToUse.name,
                                        content = """{"command": "uname -a", "working_dir": "."}"""
                                    )
                                }

                                2 -> {
                                    if (messages.any { it.contains("openjdk version \"17.0.2\"") }) continue
                                    Message.Tool.Call(
                                        id = "2",
                                        tool = toolToUse.name,
                                        content = """{"command": "java -version", "working_dir": "."}"""
                                    )
                                }

                                3 -> {
                                    if (messages.any { it.contains("gradle is located in") }) continue
                                    Message.Tool.Call(
                                        id = "3",
                                        tool = toolToUse.name,
                                        content = """{"command": "which gradle", "working_dir": "."}"""
                                    )
                                }

                                4 -> {
                                    if (messages.any { it.contains("JAVA_HOME=/usr/lib/jvm") }) continue
                                    Message.Tool.Call(
                                        id = "4",
                                        tool = toolToUse.name,
                                        content = """{"command": "printenv", "working_dir": "."}"""
                                    )
                                }

                                else -> throw RuntimeException("Unexpected bash tool call count: $bashToolCallsCount")
                            }

                            break
                        }

                        "files" -> {
                            if (fileSearchToolCallsCount++ >= 3) continue
                            result = when (fileSearchToolCallsCount) {
                                1 -> Message.Tool.Call(
                                    id = "1",
                                    tool = toolToUse.name,
                                    content = """{"pattern": "**/*.{kt,java}", "base_dir": ".", "read_content": false}"""
                                )

                                2 -> Message.Tool.Call(
                                    id = "2",
                                    tool = toolToUse.name,
                                    content = """{"pattern": "**/build.gradle.kts", "base_dir": ".", "read_content": false}"""
                                )

                                3 -> Message.Tool.Call(
                                    id = "3",
                                    tool = toolToUse.name,
                                    content = """{"pattern": "**/.editorconfig", "base_dir": ".", "read_content": false}"""
                                )

                                else -> throw RuntimeException("Unexpected files tool call count: $fileSearchToolCallsCount")
                            }

                            break
                        }

                        "code-analysis" -> {
                            if (codeAnalysisToolCallsCount++ >= 3) continue
                            result = when (codeAnalysisToolCallsCount) {
                                1 -> Message.Tool.Call(
                                    id = "1",
                                    tool = toolToUse.name,
                                    content = """{"analysis_type": "dependencies", "file_path": "build.gradle.kts", "base_dir": "."}"""
                                )

                                2 -> Message.Tool.Call(
                                    id = "2",
                                    tool = toolToUse.name,
                                    content = """{"analysis_type": "style", "file_path": "build.gradle.kts", "base_dir": "."}"""
                                )

                                3 -> Message.Tool.Call(
                                    id = "3",
                                    tool = toolToUse.name,
                                    content = """{"analysis_type": "structure", "file_path": "build.gradle.kts", "base_dir": "."}"""
                                )

                                else -> throw RuntimeException("Unexpected code-analysis tool call count: $codeAnalysisToolCallsCount")
                            }

                            break
                        }

                        else -> throw RuntimeException("Unknown tool: ${toolToUse.name}")
                    }
                }

                result
            } else {
                null
            }

            // Otherwise return a normal response
            val result = listOf(toolCall ?: handlePrompt(prompt))

            return result
        }

        override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
            return flow {
                emit(this@MockLLMExecutor.handlePrompt(prompt).content)
            }
        }

        private fun handlePrompt(prompt: Prompt): Message.Response {
            val lastMessage = prompt.messages.last()

            // Return different responses based on the prompt content
            return when {
                lastMessage.content.contains(
                    "Based on our previous conversation, " +
                        "what are the key facts about concept \"environment-info\""
                ) ->
                    Message.Assistant(
                        """
                        OS x86_64 GNU/Linux
                        openjdk version \"17.0.2\"
                        gradle is located in "/usr/bin/gradle"
                        JAVA_HOME=/usr/lib/jvm/java-17-openjdk\nGRADLE_HOME=/opt/gradle/7.4.2
                    """.trimIndent()
                    )

                lastMessage.content.contains(
                    "Based on our previous conversation, " +
                        "what are the key facts about concept \"project-dependencies\""
                ) ->
                    Message.Assistant(
                        """
                        kotlinx.coroutines:1.6.4
                        kotlinx.serialization:1.4.1
                        javax.json:1.1.4
                    """.trimIndent()
                    )

                lastMessage.content.contains(
                    "Based on our previous conversation, " +
                        "what are the key facts about concept \"project-structure\""
                ) ->
                    Message.Assistant(
                        """
                        Project has one module
                        Important file src/main/kotlin/com/example/Example.kt
                        Project's build file is located in build.gradle.kts
                    """.trimIndent()
                    )

                lastMessage.content.contains(
                    "Based on our previous conversation, " +
                        "what are the key facts about concept \"project-codestyle\""
                ) ->
                    Message.Assistant(
                        """
                        Project uses kotlin official code style
                    """.trimIndent()
                    )

                lastMessage.content.contains("environment") ->
                    Message.Assistant(responseForEnvironmentInfo)

                lastMessage.content.contains("dependencies") ->
                    Message.Assistant(responseForProjectDependencies)

                lastMessage.content.contains("structure") ->
                    Message.Assistant(responseForProjectStructure)

                lastMessage.content.contains("code style") ->
                    Message.Assistant(responseForCodeStyle)

                else ->
                    Message.Assistant("I've analyzed the project and found some interesting information.")
            }
        }
    }

    /**
     * Mock BashTool implementation for testing.
     */
    class MockBashTool : BashTool() {
        override suspend fun doExecute(args: Args): String {
            return when (args.command) {
                "uname -a" -> "Linux testmachine 5.15.0-generic #1 SMP x86_64 GNU/Linux"
                "java -version" -> "openjdk version \"17.0.2\" 2022-01-18\nOpenJDK Runtime Environment (build 17.0.2+8)\nOpenJDK 64-Bit Server VM (build 17.0.2+8, mixed mode, sharing)"
                "which gradle" -> "/usr/bin/gradle"
                "printenv" -> "JAVA_HOME=/usr/lib/jvm/java-17-openjdk\nGRADLE_HOME=/opt/gradle/7.4.2"
                else -> "Command executed successfully with no output"
            }
        }
    }

    /**
     * Mock FileSearchTool implementation for testing.
     */
    class MockFileSearchTool : FileSearchTool() {
        override suspend fun doExecute(args: Args): String {
            return when (args.pattern) {
                "**/*.{kt,java}" -> "/src/main/kotlin/Example.kt\n/src/main/kotlin/Utils.kt\n/src/main/java/JavaClass.java"
                "**/build.gradle.kts" -> "/build.gradle.kts\n/module1/build.gradle.kts"
                "**/.editorconfig" -> "/.editorconfig"
                else -> "No files found matching pattern: ${args.pattern}"
            }
        }
    }

    /**
     * Mock CodeAnalysisTool implementation for testing.
     */
    class MockCodeAnalysisTool : CodeAnalysisTool() {
        override suspend fun doExecute(args: Args): String {
            return when (args.analysisType) {
                "dependencies" -> when {
                    args.filePath.endsWith(".gradle.kts") ->
                        "Found dependencies in Gradle file:\nimplementation(\"org.jetbrains.kotlin:kotlin-stdlib:1.7.10\")\nimplementation(\"com.google.code.gson:gson:2.9.0\")"

                    args.filePath.endsWith(".xml") ->
                        "Found dependencies in Maven file:\n<dependency>\n  <groupId>org.jetbrains.kotlin</groupId>\n  <artifactId>kotlin-stdlib</artifactId>\n</dependency>"

                    else -> "No dependencies found"
                }

                "style" -> when {
                    args.filePath.endsWith(".editorconfig") ->
                        "Found code style rules in EditorConfig:\nindent_size = 4\nend_of_line = lf\ninsert_final_newline = true"

                    else -> "No code style rules found"
                }

                "structure" -> when {
                    args.filePath.endsWith(".gradle.kts") ->
                        "Found project structure in Gradle file:\nproject(\":core\")\nproject(\":app\")"

                    else -> "No project structure information found"
                }

                else -> "Error: Unknown analysis type: ${args.analysisType}"
            }
        }
    }

    private lateinit var memoryProvider: TestMemoryProvider
    private lateinit var mockLLMExecutor: MockLLMExecutor
    private lateinit var mockBashTool: MockBashTool
    private lateinit var mockFileSearchTool: MockFileSearchTool
    private lateinit var mockCodeAnalysisTool: MockCodeAnalysisTool

    // Static variables for memory configuration
    companion object {
        private const val TEST_PRODUCT_NAME = "test-dev-cli"
        private const val TEST_FEATURE_NAME = "test-feature-project-analyzer"
        private const val TEST_ORG_NAME = "test-org-jetbrains"
    }

    @BeforeEach
    fun setup() {
        memoryProvider = TestMemoryProvider()
        mockLLMExecutor = MockLLMExecutor()
        mockBashTool = MockBashTool()
        mockFileSearchTool = MockFileSearchTool()
        mockCodeAnalysisTool = MockCodeAnalysisTool()
    }

    /**
     * Test that the agent stores environment information in memory.
     * This test verifies that after running the agent, facts about the environment
     * are stored in the memory provider.
     */
    @Test
    fun `test agent stores environment information in memory`() = runTest {
        // Create the agent
        val agent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the agent
        agent.run("")

        // Verify that environment facts were stored in memory
        val machineSubjectFacts = memoryProvider.loadAll(MemorySubjects.Machine, MemoryScope.Product(TEST_PRODUCT_NAME))
        assertFalse(machineSubjectFacts.isEmpty(), "No facts about the machine environment were stored")

        // Verify that at least one fact contains environment information
        val environmentInfoFacts = machineSubjectFacts.filter {
            it.concept.keyword == "environment-info"
        }
        assertFalse(environmentInfoFacts.isEmpty(), "No environment-info facts were stored")
    }

    /**
     * Test that the agent stores project dependencies in memory.
     * This test verifies that after running the agent, facts about project dependencies
     * are stored in the memory provider.
     */
    @Test
    fun `test agent stores project dependencies in memory`() = runTest {
        // Create the agent
        val agent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the agent
        agent.run("")

        // Verify that project facts were stored in memory
        val projectSubjectFacts = memoryProvider.loadAll(MemorySubjects.Project, MemoryScope.Product(TEST_PRODUCT_NAME))
        assertFalse(projectSubjectFacts.isEmpty(), "No facts about the project were stored")

        // Verify that at least one fact contains dependency information
        val dependencyFacts = projectSubjectFacts.filter {
            it.concept.keyword == "project-dependencies"
        }
        assertFalse(dependencyFacts.isEmpty(), "No project-dependencies facts were stored")
    }

    /**
     * Test that the agent stores project structure information in memory.
     * This test verifies that after running the agent, facts about project structure
     * are stored in the memory provider.
     */
    @Test
    fun `test agent stores project structure in memory`() = runTest {
        // Create the agent
        val agent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the agent
        agent.run("")

        // Verify that project structure facts were stored in memory
        val projectSubjectFacts = memoryProvider.loadAll(MemorySubjects.Project, MemoryScope.Product(TEST_PRODUCT_NAME))

        // Verify that at least one fact contains structure information
        val structureFacts = projectSubjectFacts.filter {
            it.concept.keyword == "project-structure"
        }
        assertFalse(structureFacts.isEmpty(), "No project-structure facts were stored")
        assertEquals(1, structureFacts.size)
        assertTrue(
            structureFacts.first() is MultipleFacts,
            "Expected multiple project-structure facts, got ${structureFacts.first()} instead"
        )
        val facts = structureFacts.first() as MultipleFacts
        assertEquals(3, facts.values.size)
        assertContains(facts.values, "Project has one module")
    }

    /**
     * Test that the agent stores code style information in memory.
     * This test verifies that after running the agent, facts about code style
     * are stored in the memory provider.
     */
    @Test
    fun `test agent stores code style information in memory`() = runTest {
        // Create the agent
        val agent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the agent
        agent.run("")

        // Verify that code style facts were stored in memory
        val projectSubjectFacts = memoryProvider.loadAll(MemorySubjects.Project, MemoryScope.Product(TEST_PRODUCT_NAME))

        // Verify that at least one fact contains code style information
        val codeStyleFacts = projectSubjectFacts.filter {
            it.concept.keyword == "project-codestyle"
        }
        assertFalse(codeStyleFacts.isEmpty(), "No project-codestyle facts were stored")
    }

    /**
     * Test that a second agent can access facts stored by the first agent.
     * This test verifies that memory is shared between agents.
     */
    @Test
    fun `test second agent can access facts from first agent`() = runTest {
        // Create the first agent
        val firstAgent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the first agent
        firstAgent.run("")

        val firstAgentToolCalls = mockLLMExecutor.toolCallsCount
        mockLLMExecutor.clear()

        // Verify that facts were stored in memory
        val factsBeforeSecondAgent =
            memoryProvider.loadAll(MemorySubjects.Project, MemoryScope.Product(TEST_PRODUCT_NAME))
        assertFalse(factsBeforeSecondAgent.isEmpty(), "No facts were stored by the first agent")

        // Create a second agent with the same memory provider
        val secondAgent = createProjectAnalyzerAgent(
            bashTool = mockBashTool,
            fileSearchTool = mockFileSearchTool,
            codeAnalysisTool = mockCodeAnalysisTool,
            memoryProvider = memoryProvider,
            featureName = TEST_FEATURE_NAME,
            productName = TEST_PRODUCT_NAME,
            organizationName = TEST_ORG_NAME,
            promptExecutor = mockLLMExecutor,
            cs = this
        )

        // Run the second agent
        secondAgent.run("")

        val secondAgentToolCalls = mockLLMExecutor.toolCallsCount

        // Verify that the second agent could access the facts from the first agent
        // This is implicit in the fact that the second agent ran successfully
        // We could also verify that no duplicate facts were created
        val factsAfterSecondAgent =
            memoryProvider.loadAll(MemorySubjects.Project, MemoryScope.Product(TEST_PRODUCT_NAME))
        assertTrue(
            factsAfterSecondAgent.size >= factsBeforeSecondAgent.size,
            "Second agent should have access to at least as many facts as were stored by the first agent"
        )

        assertTrue(
            secondAgentToolCalls < firstAgentToolCalls,
            "Second agent should have made less calls to the LLM than the first agent because of the memory"
        )
    }
}
