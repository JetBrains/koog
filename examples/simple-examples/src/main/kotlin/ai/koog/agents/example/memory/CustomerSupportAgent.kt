package ai.koog.agents.example.memory

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.memory.tools.DiagnosticToolSet
import ai.koog.agents.example.memory.tools.KnowledgeBaseToolSet
import ai.koog.agents.example.memory.tools.UserInfoToolSet
import ai.koog.agents.ext.agent.ProvideStringSubgraphResult
import ai.koog.agents.ext.agent.StringSubgraphResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.nodes.nodeLoadFromMemory
import ai.koog.agents.memory.feature.nodes.nodeSaveToMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.Aes256GCMEncryptor
import ai.koog.agents.memory.storage.EncryptedStorage
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.io.path.Path

private object MemorySubjects {
    /**
     * Information specific to the user
     * Examples: Conversation preferences, issue history, contact information
     */
    @Serializable
    data object User : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String =
            "User information (conversation preferences, issue history, contact details, etc.)"
        override val priorityLevel: Int = 1
    }

    /**
     * Information specific to the machine or device
     * Examples: Device type, error codes, diagnostic results
     */
    @Serializable
    data object Machine : MemorySubject() {
        override val name: String = "machine"
        override val promptDescription: String =
            "Machine or device information (device type, error codes, diagnostic results, etc.)"
        override val priorityLevel: Int = 2
    }

    /**
     * Information specific to the organization
     * Examples: Corporate customer details, product information, solutions
     */
    @Serializable
    data object Organization : MemorySubject() {
        override val name: String = "organization"
        override val promptDescription: String =
            "Organization information (corporate customer details, product information, solutions, etc.)"
        override val priorityLevel: Int = 3
    }
}

/**
 * Creates and configures a customer support agent that demonstrates memory system usage.
 * This agent tracks and utilizes:
 * 1. User conversation preferences and issue history
 * 2. Machine/device diagnostic information
 * 3. Organization-specific product information and solutions
 *
 * The agent uses encrypted local storage to securely persist information
 * and demonstrates proper memory organization using subjects and scopes.
 */
fun createCustomerSupportAgent(
    userInfoToolSet: ToolSet,
    diagnosticToolSet: ToolSet,
    knowledgeBaseToolSet: ToolSet,
    memoryProvider: AgentMemoryProvider,
    promptExecutor: PromptExecutor,
    maxAgentIterations: Int = 50,
    featureName: String? = null,
    productName: String? = null,
    organizationName: String? = null,
): AIAgent<String, String> {
    // Memory concepts
    val userPreferencesConcept = Concept(
        keyword = "user-preferences",
        description = """
            Information about the user's conversation preferences including:
            - Preferred lexicon and terminology
            - Preference for long or short responses
            - Communication style (formal, casual, technical)
            - Preferred contact methods
            This information helps in personalizing the support experience.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val userIssuesConcept = Concept(
        keyword = "user-issues",
        description = """
            Information about the user's resolved and open issues including:
            - Issue descriptions and identifiers
            - Resolution status and details
            - Timestamps and duration
            - Related products or services
            This information helps in tracking the user's history with support.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val diagnosticResultsConcept = Concept(
        keyword = "diagnostic-results",
        description = """
            Information about diagnostic results for specific devices or error codes including:
            - Device identifiers and types
            - Error codes and descriptions
            - Diagnostic steps performed
            - Results and recommendations
            This information helps avoid repeating diagnostic steps for known issues.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val organizationSolutionsConcept = Concept(
        keyword = "organization-solutions",
        description = """
            Information about solutions provided to corporate customers including:
            - Product or service involved
            - Issue description
            - Solution details
            - Customer organization
            This information helps in sharing knowledge across an organization.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    // Agent configuration
    val agentConfig = AIAgentConfig(
        prompt = prompt("customer-support") {},
        model = AnthropicModels.Sonnet_3_7,
        maxAgentIterations = maxAgentIterations
    )

    // Create agent strategy
    val strategy = strategy<String, String>("customer-support", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
        val loadMemory by subgraph<String, String>(tools = emptyList()) {
            val nodeLoadUserPreferences by nodeLoadFromMemory<String>(
                concept = userPreferencesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadUserIssues by nodeLoadFromMemory<String>(
                concept = userIssuesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadDiagnosticResults by nodeLoadFromMemory<String>(
                concept = diagnosticResultsConcept,
                subject = MemorySubjects.Machine,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadOrganizationSolutions by nodeLoadFromMemory<String>(
                concept = organizationSolutionsConcept,
                subject = MemorySubjects.Organization,
                scope = MemoryScopeType.PRODUCT
            )

            nodeStart then nodeLoadUserPreferences then nodeLoadUserIssues then nodeLoadDiagnosticResults then
                nodeLoadOrganizationSolutions then
                nodeFinish
        }

        val supportSession by subgraphWithTask<String>(
            tools = userInfoToolSet.asTools() + diagnosticToolSet.asTools() + knowledgeBaseToolSet.asTools()
        ) { userInput ->
            markdown {
                h2(
                    "You are a customer support agent that helps users resolve issues and tracks information for future reference"
                )
                text("You should:")
                br()
                bulleted {
                    item {
                        text(
                            "Understand the user's preferences and communication style. " +
                                "Do not ask this explicitly, but use this information (if available) from your own knowledge or memory"
                        )
                    }
                    item { text("Review the user's issue history to provide context") }
                    item { text("Use diagnostic information to avoid repeating steps") }
                    item { text("Leverage organization-wide solutions when applicable") }
                    item { text("Solve the user's issue and provide a solution if possible") }
                }

                h2("User's question:")
                text(userInput)
            }
        }
        val retrieveResult by node<StringSubgraphResult, String> { it.result }

        val saveToMemory by subgraph<String, String>(tools = emptyList()) {
            val saveUserPreferences by nodeSaveToMemory<String>(
                concept = userPreferencesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val saveUserIssues by nodeSaveToMemory<String>(
                concept = userIssuesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val saveDiagnosticResults by nodeSaveToMemory<String>(
                concept = diagnosticResultsConcept,
                subject = MemorySubjects.Machine,
                scope = MemoryScopeType.PRODUCT
            )

            val saveOrganizationSolutions by nodeSaveToMemory<String>(
                concept = organizationSolutionsConcept,
                subject = MemorySubjects.Organization,
                scope = MemoryScopeType.PRODUCT
            )

            nodeStart then saveUserPreferences then saveUserIssues then saveDiagnosticResults then
                saveOrganizationSolutions then
                nodeFinish
        }

        nodeStart then loadMemory then supportSession then retrieveResult then saveToMemory then nodeFinish
    }

    // Create and configure the agent runner
    return AIAgent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            tools(userInfoToolSet.asTools())
            tools(diagnosticToolSet.asTools())
            tools(knowledgeBaseToolSet.asTools())

            tool(ProvideStringSubgraphResult)
        }
    ) {
        install(AgentMemory) {
            this.memoryProvider = memoryProvider

            if (featureName != null) this.featureName = featureName
            if (productName != null) this.productName = productName
            if (organizationName != null) this.organizationName = organizationName
        }
    }
}

/**
 * Main entry point for running the customer support agent.
 */
fun main() = runBlocking {
    // Example key, generated by AI :)
    val secretKey = "7UL8fsTqQDq9siUZgYO3bLGqwMGXQL4vKMWMscKB7Cw="

    // Create memory provider
    val memoryProvider = LocalFileMemoryProvider(
        config = LocalMemoryConfig("customer-support-memory"),
        storage = EncryptedStorage(
            fs = JVMFileSystemProvider.ReadWrite,
            encryption = Aes256GCMEncryptor(secretKey)
        ),
        fs = JVMFileSystemProvider.ReadWrite,
        root = Path("path/to/memory/root")
    )

    // Create and run the agent
    val agent = createCustomerSupportAgent(
        userInfoToolSet = UserInfoToolSet(),
        diagnosticToolSet = DiagnosticToolSet(),
        knowledgeBaseToolSet = KnowledgeBaseToolSet(),
        memoryProvider = memoryProvider,
        featureName = "customer-support",
        productName = "support-system",
        organizationName = "grazie",
        promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
    )
    agent.run("")
}
