package ai.grazie.code.agents.local.memory.providers

import ai.grazie.code.agents.local.memory.model.Concept
import ai.grazie.code.agents.local.memory.model.Fact
import ai.grazie.code.agents.local.memory.model.MemoryScope
import ai.grazie.code.agents.local.memory.model.MemorySubject
import ai.grazie.model.cloud.AuthType
import ai.grazie.model.cloud.AuthVersion
import io.ktor.http.*
import kotlinx.serialization.Contextual
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
 * Configuration for shared remote memory storage.
 * This implementation enables:
 * - Centralized knowledge storage
 * - Multi-agent memory sharing
 * - Cross-instance persistence
 *
 * Usage example:
 * ```
 * val config = SharedRemoteMemoryConfig(
 *     clientConfig = RemoteMemoryClientConfig(
 *         connection = RemoteMemoryConnectionConfig(
 *             protocol = URLProtocol.HTTPS,
 *             host = "memory.example.com",
 *             port = 443
 *         )
 *     )
 * )
 * ```
 *
 * @property clientConfig Remote connection and authentication settings
 * @property defaultScope Default visibility scope, typically cross-product
 */
@Serializable
@SerialName("remote")
data class SharedRemoteMemoryConfig(
    val clientConfig: RemoteMemoryClientConfig,
    override val defaultScope: MemoryScope = MemoryScope.CrossProduct,
) : MemoryProviderConfig {
    val serverUrl: String get() = clientConfig.connection.url
}

/**
 * Comprehensive configuration for remote memory client.
 * This class combines all necessary settings for:
 * - Network connectivity
 * - Authentication
 * - Operation timeouts
 * - Environment selection
 *
 * Security considerations:
 * - Uses secure protocols by default
 * - Supports multiple auth methods
 * - Implements connection timeouts
 *
 * @property connection Network connection parameters
 * @property auth Authentication configuration
 * @property timeout Network timeout settings
 * @property grazieEnvironment Target environment selection
 */
@Serializable
data class RemoteMemoryClientConfig(
    val connection: RemoteMemoryConnectionConfig,
    val auth: RemoteMemoryClientAuthConfig = RemoteMemoryClientAuthConfig(),
    val timeout: RemoteMemoryConnectionTimeoutConfig = RemoteMemoryConnectionTimeoutConfig(),
    val grazieEnvironment: GrazieEnvironment = GrazieEnvironment.Production,
)

/**
 * Network connection configuration for remote memory service.
 * This class defines the core networking parameters required
 * to establish a connection with a remote memory server.
 *
 * Security considerations:
 * - HTTPS should be preferred for production use
 * - Non-standard ports may require firewall configuration
 * - Host verification should be implemented
 *
 * Usage example:
 * ```
 * val connection = RemoteMemoryConnectionConfig(
 *     protocol = URLProtocol.HTTPS,
 *     host = "memory.example.com",
 *     port = 443
 * )
 *
 * // Access complete URL
 * println(connection.url) // "https://memory.example.com:443"
 * ```
 *
 * @property protocol Network protocol (HTTPS recommended for production)
 * @property host Remote memory service hostname
 * @property port Network port for the service
 */
@Serializable
data class RemoteMemoryConnectionConfig(
    @Contextual
    val protocol: URLProtocol,
    val host: String,
    val port: Int
) {
    /** 
     * Constructs the complete URL for the remote memory service.
     * @return Formatted URL string including protocol, host, and port
     */
    val url: String get() = "$protocol://$host:$port"
}

/**
 * Authentication configuration for secure remote memory access.
 * This class defines how clients authenticate with the remote
 * memory service, supporting different authentication methods
 * and protocol versions.
 *
 * Key features:
 * - Multiple authentication types support
 * - Version-based protocol selection
 * - Secure defaults (User auth and V5 protocol)
 *
 * Usage example:
 * ```
 * // Default configuration (recommended)
 * val auth = RemoteMemoryClientAuthConfig()
 *
 * // Custom configuration
 * val customAuth = RemoteMemoryClientAuthConfig(
 *     type = AuthType.Service,
 *     version = AuthVersion.V5
 * )
 * ```
 *
 * Security considerations:
 * - Always use the latest auth version in production
 * - Service auth type requires proper key management
 * - User auth type integrates with platform security
 *
 * @property type The type of authentication to use, defaults to User authentication
 * @property version The version of authentication protocol, defaults to V5
 */
@Serializable
data class RemoteMemoryClientAuthConfig(
    val type: AuthType = AuthType.User,
    val version: AuthVersion = AuthVersion.V5,
)

/**
 * Network timeout configuration for remote memory operations.
 * This class provides fine-grained control over different aspects
 * of network timeouts to ensure reliable operation and proper
 * error handling in various network conditions.
 *
 * Key features:
 * - Separate timeouts for different operation phases
 * - Reasonable defaults (60 seconds)
 * - Millisecond precision
 *
 * Usage example:
 * ```
 * // Default configuration (60 seconds for all timeouts)
 * val config = RemoteMemoryConnectionTimeoutConfig()
 *
 * // Custom configuration for slow networks
 * val customConfig = RemoteMemoryConnectionTimeoutConfig(
 *     requestTimeoutMillis = 120_000, // 2 minutes total
 *     connectTimeoutMillis = 30_000,  // 30 seconds for connection
 *     socketTimeoutMillis = 90_000    // 90 seconds for data transfer
 * )
 * ```
 *
 * Timeout phases:
 * 1. Connect timeout: Initial connection establishment
 * 2. Socket timeout: Individual read/write operations
 * 3. Request timeout: Overall operation completion
 *
 * @property requestTimeoutMillis Total time allowed for request completion
 * @property connectTimeoutMillis Maximum time to establish connection
 * @property socketTimeoutMillis Maximum time between data packets
 */
@Serializable
data class RemoteMemoryConnectionTimeoutConfig(
    val requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    val connectTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    val socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) {
    private companion object {
        private const val DEFAULT_TIMEOUT_MS: Long = 60_000
    }
}

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
 * Usage example:
 * ```
 * // Use predefined environments
 * val stagingConfig = GrazieEnvironment.Staging
 * val prodConfig = GrazieEnvironment.Production
 *
 * // Create custom environment
 * val customConfig = GrazieEnvironment.Custom("https://custom.memory.company.com")
 *
 * // Use in client configuration
 * val clientConfig = RemoteMemoryClientConfig(
 *     connection = connectionConfig,
 *     grazieEnvironment = GrazieEnvironment.Production
 * )
 * ```
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
