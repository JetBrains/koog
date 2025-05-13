package ai.grazie.code.agents.example.memory

import ai.grazie.code.agents.core.agent.Agent
import ai.grazie.code.agents.core.agent.config.AgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.memory.tools.*
import ai.grazie.code.agents.local.memory.config.MemoryScopeType
import ai.grazie.code.agents.local.memory.feature.MemoryFeature
import ai.grazie.code.agents.local.memory.feature.nodes.nodeLoadFromMemory
import ai.grazie.code.agents.local.memory.feature.nodes.nodeSaveToMemory
import ai.grazie.code.agents.local.memory.model.Concept
import ai.grazie.code.agents.local.memory.model.FactType
import ai.grazie.code.agents.local.memory.model.MemorySubject
import ai.grazie.code.agents.local.memory.providers.AgentMemoryProvider
import ai.grazie.code.agents.local.memory.providers.LocalFileMemoryProvider
import ai.grazie.code.agents.local.memory.providers.LocalMemoryConfig
import ai.grazie.code.agents.local.memory.storage.Aes256GCMEncryptor
import ai.grazie.code.agents.local.memory.storage.EncryptedStorage
import ai.grazie.code.files.jvm.JVMFileSystemProvider
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path

/**
 * Creates and configures a project analyzer agent that demonstrates memory system usage.
 * This agent analyzes and stores information about:
 * 1. Development environment (OS, tools, etc.)
 * 2. Project dependencies and structure
 * 3. Code style and practices
 *
 * The agent uses encrypted local storage to securely persist information
 * and demonstrates proper memory organization using subjects and scopes.
 */
fun createProjectAnalyzerAgent(
    bashTool: BashTool,
    fileSearchTool: FileSearchTool,
    codeAnalysisTool: CodeAnalysisTool,
    memoryProvider: AgentMemoryProvider,
    cs: CoroutineScope,
    promptExecutor: PromptExecutor,
    maxAgentIterations: Int = 50,
    featureName: String? = null,
    productName: String? = null,
    organizationName: String? = null,
): Agent {
    // Memory concepts
    val environmentInfoConcept = Concept(
        keyword = "environment-info",
        description = """
            Comprehensive information about the user's development environment including:
            - Operating system details and version
            - Installed development tools and SDKs
            - Available system commands and package managers
            - Environment variables and system configuration
            This information helps in understanding the development context and available tools.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val projectDependenciesConcept = Concept(
        keyword = "project-dependencies",
        description = """
            Project build script dependencies and their configurations including:
            - Direct dependencies with versions
            - Transitive dependencies
            - Build tool plugins and their versions
            - Repository configurations
            This information is crucial for understanding project requirements and build setup.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val projectStructureConcept = Concept(
        keyword = "project-structure",
        description = """
            Detailed project structure information including:
            - Module hierarchy and relationships
            - Important configuration files
            - Source code organization
            - Resource directories and their purposes
            This helps in understanding the project's organization and architecture.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val codeStyleConcept = Concept(
        keyword = "project-codestyle",
        description = """
            Project code style rules and conventions including:
            - Code formatting preferences
            - Naming conventions
            - Package organization rules
            - Common patterns and practices
            This information ensures consistent code style across the project.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    // Agent configuration
    val agentConfig = AgentConfig(
        prompt = prompt("project-analyzer") {},
        model = AnthropicModels.Sonnet_3_7,
        maxAgentIterations = maxAgentIterations
    )

    // Create agent strategy
    val strategy = strategy("project-analyzer") {
        stage("load-memory") {
            val nodeLoadEnvironmentInfo by nodeLoadFromMemory<Unit>(
                concept = environmentInfoConcept,
                subject = MemorySubject.MACHINE,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadProjectDependencies by nodeLoadFromMemory<Unit>(
                concept = projectDependenciesConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadProjectStructure by nodeLoadFromMemory<Unit>(
                concept = projectStructureConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadCodeStyle by nodeLoadFromMemory<Unit>(
                concept = codeStyleConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            edge(nodeStart forwardTo nodeLoadEnvironmentInfo)
            edge(nodeLoadEnvironmentInfo forwardTo nodeLoadProjectDependencies)
            edge(nodeLoadProjectDependencies forwardTo nodeLoadProjectStructure)
            edge(nodeLoadProjectStructure forwardTo nodeLoadCodeStyle)
            edge(nodeLoadCodeStyle forwardTo nodeFinish transformed { "" })
        }
        stage("gather-information") {
            // Define universal agent nodes
            val defineTask by nodeUpdatePrompt {
                system(
                    "You are a project analyzer agent that helps understand and document project structure, " +
                            "dependencies, and development environment. You should: \n" +
                            "1. Analyze the environment and available tools (operating system, package manager, installed tools, etc.)\n" +
                            "2. Examine project structure and dependencies \n" +
                            "3. Determine code style and practices\n"
                )
            }
            val sendInput by nodeLLMRequest()
            val callTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            // Define the flow
            edge(nodeStart forwardTo defineTask)
            edge(defineTask forwardTo sendInput transformed { "Please analyze this project and gather information about the environment, project structure, dependencies, and code style." })
            edge(sendInput forwardTo callTool onToolCall { true })
            edge(sendInput forwardTo nodeFinish onAssistantMessage { true })
            edge(callTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo callTool onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        }

        stage("save-to-memory") {
            val nodeSaveEnvironmentInfo by nodeSaveToMemory<Unit>(
                concept = environmentInfoConcept,
                subject = MemorySubject.MACHINE,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeSaveProjectDependencies by nodeSaveToMemory<Unit>(
                concept = projectDependenciesConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeSaveProjectStructure by nodeSaveToMemory<Unit>(
                concept = projectStructureConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeSaveCodeStyle by nodeSaveToMemory<Unit>(
                concept = codeStyleConcept,
                subject = MemorySubject.PROJECT,
                scope = MemoryScopeType.PRODUCT
            )

            edge(nodeStart forwardTo nodeSaveEnvironmentInfo)
            edge(nodeSaveEnvironmentInfo forwardTo nodeSaveProjectDependencies)
            edge(nodeSaveProjectDependencies forwardTo nodeSaveProjectStructure)
            edge(nodeSaveProjectStructure forwardTo nodeSaveCodeStyle)
            edge(nodeSaveCodeStyle forwardTo nodeFinish transformed { "" })
        }
    }

    // Create and configure the agent runner
    return Agent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        cs = cs,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            stage("gather-information") {
                tool(bashTool)
                tool(fileSearchTool)
                tool(codeAnalysisTool)
            }
        },
        eventHandler = EventHandler {}
    ) {
        install(MemoryFeature) {
            this.memoryProvider = memoryProvider

            if (featureName != null) this.featureName = featureName
            if (productName != null) this.productName = productName
            if (organizationName != null) this.organizationName = organizationName
        }
    }
}

/**
 * Main entry point for running the project analyzer agent.
 */
fun main() = runBlocking {
    // Create real implementations of tools
    val bashTool = BashToolImpl()
    val fileSearchTool = FileSearchToolImpl()
    val codeAnalysisTool = CodeAnalysisToolImpl()

    // Example key, generated by AI :)
    val secretKey = "7UL8fsTqQDq9siUZgYO3bLGqwMGXQL4vKMWMscKB7Cw="

    // Create memory provider
    val memoryProvider = LocalFileMemoryProvider(
        config = LocalMemoryConfig("project-analyzer-memory"),
        storage = EncryptedStorage(
            fs = JVMFileSystemProvider.ReadWrite,
            encryption = Aes256GCMEncryptor(secretKey)
        ),
        fs = JVMFileSystemProvider.ReadWrite,
        root = Path("path/to/memory/root")
    )

    // Create and run the agent
    val agent = createProjectAnalyzerAgent(
        bashTool = bashTool,
        fileSearchTool = fileSearchTool,
        codeAnalysisTool = codeAnalysisTool,
        memoryProvider = memoryProvider,
        featureName = "project-analyzer",
        productName = "dev-cli",
        organizationName = "grazie",
        promptExecutor = simpleAnthropicExecutor(TokenService.anthropicToken),
        cs = this,
    )
    agent.run("")
}