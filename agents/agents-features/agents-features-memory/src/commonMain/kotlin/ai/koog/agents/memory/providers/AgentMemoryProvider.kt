package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

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
 *     subject = MemorySubject.Project,
 *     scope = MemoryScope.Product("my-app")
 * )
 *
 * // Retrieve environment information
 * val envFacts = provider.loadByDescription(
 *     description = "system environment",
 *     subject = MemorySubject.Machine,
 *     scope = MemoryScope.Agent("env-analyzer")
 * )
 * ```
 */
public interface AgentMemoryProvider {
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
    public suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope)

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
    public suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact>

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
    public suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact>

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
    public suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact>
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
public sealed interface MemoryProviderConfig {
    /**
     * Default visibility scope for stored facts.
     * This setting determines the initial accessibility of stored information
     * when no explicit scope is provided.
     */
    public val defaultScope: MemoryScope
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
public data class LocalMemoryConfig @JvmOverloads constructor(
    val storageDirectory: String,
    override val defaultScope: MemoryScope = MemoryScope.CrossProduct,
) : MemoryProviderConfig
