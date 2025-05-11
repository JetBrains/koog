package ai.grazie.code.agents.local.memory.providers

import ai.grazie.code.agents.local.memory.model.Concept
import ai.grazie.code.agents.local.memory.model.Fact
import ai.grazie.code.agents.local.memory.model.MemoryScope
import ai.grazie.code.agents.local.memory.model.MemorySubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Core interface for managing an agent's persistent memory system.
 * This interface defines the fundamental operations for storing and retrieving
 * knowledge in a structured, context-aware manner.
 *
 * Key features:
 * - Structured knowledge storage using concepts and facts
 * - Context-aware memory organization (subjects and scopes)
 * - Flexible storage backend support (local/remote)
 * - Semantic search capabilities
 *
 * Usage example:
 * ```
 * val provider: AgentMemoryProvider = LocalFileMemoryProvider(
 *     config = LocalMemoryConfig("memory"),
 *     storage = EncryptedStorage(fs, encryption),
 *     fs = JVMFileSystemProvider,
 *     root = basePath
 * )
 *
 * // Store project information
 * provider.save(
 *     fact = SingleFact(
 *         concept = Concept("build-system", "Project build configuration", FactType.SINGLE),
 *         timestamp = currentTime,
 *         value = "Gradle 8.0"
 *     ),
 *     subject = MemorySubject.PROJECT,
 *     scope = MemoryScope.Product("my-app")
 * )
 *
 * // Retrieve environment information
 * val envFacts = provider.loadByDescription(
 *     description = "system environment",
 *     subject = MemorySubject.MACHINE,
 *     scope = MemoryScope.Agent("env-analyzer")
 * )
 * ```
 */
interface AgentMemoryProvider {
    /**
     * Persists a fact in the agent's memory system.
     * This operation ensures:
     * - Atomic storage of the fact
     * - Proper scoping and subject categorization
     * - Consistent storage format
     *
     * @param fact Knowledge unit to store (can be SingleFact or MultipleFacts)
     * @param subject Context category (e.g., MACHINE, PROJECT)
     * @param scope Visibility boundary (e.g., Agent, Feature)
     * @throws IOException if storage operation fails
     */
    abstract suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope)

    /**
     * Retrieves facts associated with a specific concept.
     * This operation provides:
     * - Direct concept-based knowledge retrieval
     * - Context-aware fact filtering
     * - Ordered fact list (typically by timestamp)
     *
     * @param concept Knowledge category to retrieve
     * @param subject Context to search within
     * @param scope Visibility boundary to consider
     * @return List of matching facts, empty if none found
     */
    abstract suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact>

    /**
     * Retrieves all facts within a specific context.
     * This operation is useful for:
     * - Building comprehensive context understanding
     * - Memory analysis and debugging
     * - Data migration between storage backends
     *
     * @param subject Context to retrieve from
     * @param scope Visibility boundary to consider
     * @return All available facts in the context
     */
    abstract suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact>

    /**
     * Performs semantic search across stored facts.
     * This operation enables:
     * - Natural language queries
     * - Fuzzy concept matching
     * - Context-aware search results
     *
     * Implementation considerations:
     * - May use different matching algorithms
     * - Could integrate with LLM for better understanding
     * - Should handle synonyms and related terms
     *
     * @param description Natural language query or description
     * @param subject Context to search within
     * @param scope Visibility boundary to consider
     * @return Facts matching the semantic query
     */
    abstract suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact>
}

/**
 * Base configuration interface for memory system features.
 * This interface standardizes configuration across different
 * memory implementations while allowing for specialized settings.
 *
 * Key aspects:
 * - Serializable for configuration storage
 * - Extensible for different storage types
 * - Default scope specification
 */
@Serializable
sealed interface MemoryProviderConfig {
    /**
     * Default visibility scope for stored facts.
     * This setting determines the initial accessibility of stored information
     * when no explicit scope is provided.
     */
    val defaultScope: MemoryScope
}

/**
 * Configuration for file-based local memory storage.
 * This implementation provides:
 * - Persistent local storage
 * - File system organization
 * - Optional encryption support
 *
 * Usage example:
 * ```
 * val config = LocalMemoryConfig(
 *     storageDirectory = "agent-memory",
 *     defaultScope = MemoryScope.Agent("assistant")
 * )
 * ```
 *
 * @property storageDirectory Base directory for memory files
 * @property defaultScope Default visibility scope, typically agent-specific
 */
@Serializable
@SerialName("local")
data class LocalMemoryConfig(
    val storageDirectory: String,
    override val defaultScope: MemoryScope = MemoryScope.CrossProduct,
) : MemoryProviderConfig

/**
 * Environment configuration for the Grazie platform integration.
 * This sealed class provides a type-safe way to configure different
 * deployment environments for remote memory service, with predefined
 * configurations and support for custom deployments.
 *
 * Key features:
 * - Type-safe environment selection
 * - Predefined configurations for standard environments
 * - Support for custom deployments
 * - Serialization support for configuration storage
 *
 * @property url Base URL of the Grazie platform environment
 */
@Serializable
sealed class GrazieEnvironment(val url: String) {

    /**
     * Staging environment for pre-production validation.
     * This environment provides:
     * - Integration testing capabilities
     * - Pre-production feature validation
     * - Staging-specific configurations
     */
    @Serializable
    @SerialName("staging")
    data object Staging : GrazieEnvironment("https://api.app.stgn.grazie.aws.intellij.net")

    /**
     * Production environment for live deployments.
     * This environment ensures:
     * - High availability
     * - Production-grade security
     * - Stable API versions
     */
    @Serializable
    @SerialName("production")
    data object Production : GrazieEnvironment("https://api.app.prod.grazie.aws.intellij.net")

    /**
     * Custom environment for specialized deployments.
     * Use this when you need to:
     * - Connect to private deployments
     * - Use custom domain names
     * - Implement special routing
     *
     * @property customUrl The custom URL to use for the Grazie platform
     */
    @Serializable
    @SerialName("custom")
    data class Custom(val customUrl: String) : GrazieEnvironment(customUrl)
}

/**
 node {
    memory.saveToLLM
 }
 * */
