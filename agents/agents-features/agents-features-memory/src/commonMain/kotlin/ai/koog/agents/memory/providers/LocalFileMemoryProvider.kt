package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.storage.Storage
import ai.koog.rag.base.files.FileSystemProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * File-based implementation of [AgentMemoryProvider] that provides persistent storage of agent memory
 * using a hierarchical file system structure. This implementation is designed for durability,
 * thread safety, and human readability of stored data.
 *
 * Key features:
 * - Thread-safe operations using mutex locks
 * - Hierarchical storage structure for efficient organization
 * - JSON-based storage with pretty printing for human readability
 * - Support for multiple memory scopes and subjects
 * - Atomic read/write operations
 *
 * Storage Structure:
 * ```
 * root/
 *   storageDirectory/
 *     agent/                    # For MemoryScope.Agent
 *       [agent-name]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     feature/                  # For MemoryScope.Feature
 *       [feature-id]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     product/                  # For MemoryScope.Product
 *       [product-name]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     organization/             # For MemoryScope.CrossProduct
 *       subject/
 *         [subject-name]/
 *           facts.json
 * ```
 *
 * Usage example:
 * ```
 * val provider = LocalFileMemoryProvider(
 *     config = LocalMemoryConfig("memory-storage"),
 *     storage = EncryptedStorage(fileSystem),
 *     fs = JVMFileSystemProvider,
 *     root = Path("path/to/root")
 * )
 *
 * // Store environment information
 * provider.save(
 *     fact = environmentFact,
 *     subject = MemorySubject.Machine,
 *     scope = MemoryScope.Agent("env-analyzer")
 * )
 *
 * // Retrieve project dependencies
 * val dependencies = provider.load(
 *     concept = dependenciesConcept,
 *     subject = MemorySubject.Project,
 *     scope = MemoryScope.Product("my-product")
 * )
 * ```
 *
 * @param Path The type representing file system paths (platform-specific)
 * @property config Configuration for local storage including base directory and options
 * @property storage Implementation of storage operations (can be encrypted or plain)
 * @property fs Platform-specific file system provider for path manipulations
 * @property root Root directory where all memory storage will be located
 */
public data class LocalFileMemoryProvider<Path>(
    private val config: LocalMemoryConfig,
    private val storage: Storage<Path>,
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
) : AgentMemoryProvider {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Mutex for ensuring thread-safe access to fact storage.
     * This lock prevents race conditions during concurrent read/write operations by:
     * - Ensuring atomic updates to fact collections
     * - Preventing concurrent modifications to the same file
     * - Maintaining consistency between memory and disk state
     */
    private val mutex = Mutex()

    /**
     * JSON serializer configuration optimized for memory storage.
     * Configuration choices:
     * - prettyPrint = true: Makes stored files human-readable for debugging
     * - ignoreUnknownKeys = true: Enables forward compatibility with schema changes
     *
     * This configuration balances human readability with storage efficiency.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Constructs the storage path for a given subject and scope.
     * This method implements the hierarchical storage structure that organizes
     * facts based on their scope and subject, ensuring proper isolation of
     * different types of memory.
     *
     * Path structure example for an agent scope:
     * ```
     * root/
     *   storageDirectory/
     *     agent/
     *       agent-name/
     *         subject/
     *           machine/
     *             facts.json
     * ```
     *
     * @param subject Determines the subject-specific directory (e.g., MACHINE, PROJECT)
     * @param scope Determines the scope-specific directory structure
     * @return Complete path for storing facts for the given context
     */
    private fun getStoragePath(subject: MemorySubject, scope: MemoryScope): Path {
        val segments = listOf(config.storageDirectory) + when (scope) {
            is MemoryScope.Agent -> listOf("agent", scope.name, "subject", subject.name)
            is MemoryScope.Feature -> listOf("feature", scope.id, "subject", subject.name)
            is MemoryScope.Product -> listOf("product", scope.name, "subject", subject.name)
            MemoryScope.CrossProduct -> listOf("organization", "subject", subject.name)
        }
        return segments.fold(root) { acc, segment -> fs.joinPath(acc, segment) }
    }

    /**
     * Loads facts from storage with thread-safe access and error handling.
     * This method provides atomic read operations with the following guarantees:
     * - Thread safety through mutex locking
     * - Graceful handling of missing files (returns empty map)
     * - Graceful handling of corrupted JSON files (returns empty map with warning)
     * - Consistent deserialization of stored facts
     *
     * The returned map uses concept keywords as keys for efficient lookup
     * of facts related to specific concepts.
     *
     * @param path Location of the facts storage file
     * @return Map of concept keywords to their associated facts
     */
    private suspend fun loadFacts(path: Path): Map<String, List<Fact>> = mutex.withLock {
        val content = storage.read(path) ?: return emptyMap()

        return try {
            json.decodeFromString<Map<String, List<Fact>>>(content)
        } catch (e: SerializationException) {
            logger.warn(e) { "Failed to deserialize facts from $path: ${e.message}" }
            emptyMap()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error loading facts from $path: ${e.message}" }
            emptyMap()
        }
    }

    /**
     * Saves facts to storage with thread-safe access and atomic updates.
     * This method ensures data consistency through:
     * - Mutex locking for thread safety
     * - Atomic write operations
     * - Consistent JSON serialization
     *
     * The facts are stored in a map structure where:
     * - Keys are concept keywords for efficient retrieval
     * - Values are lists of facts associated with each concept
     * - JSON is pretty-printed for human readability
     *
     * @param path Destination path for the facts storage file
     * @param facts Map of concept keywords to their associated facts
     */
    private suspend fun saveFacts(path: Path, facts: Map<String, List<Fact>>) = mutex.withLock {
        val serialized = json.encodeToString(facts)
        storage.write(path, serialized)
    }

    /**
     * Persists a fact to the local storage system with thread-safe guarantees.
     * This method provides atomic updates to the fact collection by:
     * 1. Creating necessary storage directories if they don't exist
     * 2. Loading existing facts for the concept
     * 3. Appending the new fact to the existing collection
     * 4. Saving the updated collection atomically
     *
     * Example usage:
     * ```
     * // Save project dependency information
     * save(
     *     fact = SingleFact(
     *         concept = Concept("dependencies", "Project build dependencies", FactType.MULTIPLE),
     *         timestamp = timeProvider.getCurrentTimestamp(),
     *         value = "org.jetbrains.kotlin:kotlin-stdlib:1.8.0"
     *     ),
     *     subject = MemorySubject.Project,
     *     scope = MemoryScope.Product("my-app")
     * )
     * ```
     *
     * @param fact New fact to be stored
     * @param subject Context category for the fact (e.g., MACHINE, PROJECT)
     * @param scope Visibility scope for the fact (e.g., Agent, Feature)
     */
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val path = getStoragePath(subject, scope)
        storage.createDirectories(fs.joinPath(root, config.storageDirectory))

        val facts = loadFacts(path).toMutableMap()
        val key = fact.concept.keyword
        facts[key] = (facts[key] ?: emptyList()) + fact

        saveFacts(path, facts)
    }

    /**
     * Retrieves facts associated with a specific concept from storage.
     * This method provides efficient concept-based fact retrieval with:
     * - Direct lookup using concept keyword
     * - Thread-safe access to storage
     * - Graceful handling of missing data
     *
     * Example usage:
     * ```
     * // Load environment information
     * val envFacts = load(
     *     concept = Concept("env-info", "Machine environment details", FactType.SINGLE),
     *     subject = MemorySubject.Machine,
     *     scope = MemoryScope.Agent("system-analyzer")
     * )
     * ```
     *
     * @param concept The concept whose facts should be retrieved
     * @param subject Context category to search in (e.g., MACHINE, PROJECT)
     * @param scope Visibility scope to search in (e.g., Agent, Feature)
     * @return List of facts for the concept, or empty list if none found
     */
    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val path = getStoragePath(subject, scope)
        val facts = loadFacts(path)
        return facts[concept.keyword] ?: emptyList()
    }

    /**
     * Retrieves all facts stored within a specific subject and scope context.
     * This method is useful for:
     * - Analyzing all stored knowledge in a context
     * - Migrating or backing up memory data
     * - Debugging memory contents
     *
     * Example usage:
     * ```
     * // Load all project-related facts
     * val projectFacts = loadAll(
     *     subject = MemorySubject.Project,
     *     scope = MemoryScope.Product("my-app")
     * )
     * ```
     *
     * @param subject Context category to retrieve from (e.g., MACHINE, PROJECT)
     * @param scope Visibility scope to retrieve from (e.g., Agent, Feature)
     * @return Combined list of all facts in the specified context
     */
    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val path = getStoragePath(subject, scope)
        return loadFacts(path).values.flatten()
    }

    /**
     * Searches for facts using natural language descriptions.
     * This method enables semantic-based fact retrieval by:
     * - Matching against concept descriptions (not just keywords)
     * - Using case-insensitive substring matching
     * - Supporting natural language queries
     *
     * Example usage:
     * ```
     * // Find facts about coding style
     * val styleFacts = loadByDescription(
     *     description = "code style rules",
     *     subject = MemorySubject.Project,
     *     scope = MemoryScope.Organization
     * )
     * ```
     *
     * Note: This implementation uses simple substring matching.
     * Future enhancements could include:
     * - Semantic similarity matching
     * - Fuzzy text matching
     * - Natural language understanding
     *
     * @param description Natural language description to search for
     * @param subject Context category to search in (e.g., MACHINE, PROJECT)
     * @param scope Visibility scope to search in (e.g., Agent, Feature)
     * @return List of facts whose concepts match the description
     */
    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        val path = getStoragePath(subject, scope)
        val facts = loadFacts(path)

        return facts.values.flatten().filter { fact ->
            fact.concept.description.contains(description, ignoreCase = true)
        }
    }
}
