package ai.koog.agents.secure.storage

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.secure.crypto.EncryptionKeyProvider
import ai.koog.agents.secure.apikeys.SecureApiKeyStorage
import ai.koog.agents.secure.apikeys.SecureApiKeyStorageImpl
import ai.koog.agents.secure.apikeys.ApiKeyResolver
import ai.koog.agents.secure.apikeys.ContextScopedApiKeyStorage
import ai.koog.agents.secure.storage.impl.EncryptedKVStorage
import ai.koog.agents.secure.storage.impl.PlainKVStorage
import ai.koog.agents.secure.storage.backend.kottage.KottageLocalKVBackend
import kotlinx.serialization.Serializable

/**
 * Secure Storage feature implementation for storing and managing encrypted data.
 * 
 * This class represents the runtime instance of the SecureStorage feature,
 * providing access to the configured storage backend, encryption settings,
 * and API key management capabilities.
 */
public class SecureStorage(
    public val config: SecureStorageConfig
) {
    
    /**
     * Lazy-initialized storage backend based on configuration.
     */
    private val storage: LocalKVStorage by lazy {
        val backend = KottageLocalKVBackend(config.mode.databasePath)
        when (val mode = config.mode) {
            is EncryptedMode -> {
                val keyProvider = mode.keyProvider 
                    ?: throw IllegalStateException("EncryptedMode requires a keyProvider")
                EncryptedKVStorage(backend, keyProvider)
            }
            is PlainMode -> PlainKVStorage(backend)
        }
    }
    
    /**
     * API key storage interface for managing encrypted user API keys.
     * 
     * Provides secure storage for user-provided API keys (OpenAI, Anthropic, etc.)
     * with context scoping and hierarchical fallback support.
     * 
     * **Usage:**
     * ```kotlin
     * // Save user's OpenAI key
     * secureStorage.apiKeys().saveApiKey("openai", "sk-...", "user:alice")
     * 
     * // Get key with fallback
     * val key = secureStorage.apiKeys().getApiKey("openai", "user:alice")
     * ```
     */
    public fun apiKeys(): SecureApiKeyStorage {
        // Only support API key storage in encrypted mode
        val currentStorage = storage
        return when (currentStorage) {
            is EncryptedKVStorage -> SecureApiKeyStorageImpl(currentStorage)
            else -> throw IllegalStateException(
                "API key storage requires encrypted mode. " +
                "Configure SecureStorage with EncryptedMode to use API key features."
            )
        }
    }
    
    /**
     * API key storage with specific context scoping.
     * 
     * @param context The context to scope API keys to (e.g., "user:alice", "agent:assistant")
     * @return API key storage interface scoped to the given context
     */
    public fun apiKeys(context: String): SecureApiKeyStorage {
        val baseStorage = apiKeys()
        return ContextScopedApiKeyStorage(baseStorage, context)
    }
    
    /**
     * Multi-level API key resolver with environment fallback.
     * 
     * Provides hierarchical key resolution: user keys → agent keys → environment keys
     * 
     * **Usage:**
     * ```kotlin
     * val resolver = secureStorage.apiKeyResolver()
     * val key = resolver.resolveApiKey("openai", "user:alice", "agent:assistant")
     * ```
     */
    public fun apiKeyResolver(): ApiKeyResolver {
        return ApiKeyResolver(apiKeys())
    }
    
    /**
     * Direct access to the underlying storage for advanced use cases.
     * 
     * This exposes the LocalKVStorage interface for when you need direct
     * storage access beyond the provided higher-level APIs.
     */
    public fun storage(): LocalKVStorage {
        return storage
    }
    
    /**
     * Secure Storage feature for Koan Agents framework.
     * 
     * Provides enterprise-grade encrypted storage for agent memory and persistency data.
     * While plain (unencrypted) mode is available for development, this feature is designed
     * with security as the primary focus.
     * 
     * **Security-First Design:**
     * - **Encrypted Mode** (recommended): AES-256-GCM authenticated encryption
     * - **Plain Mode** (development): Unencrypted storage with security warnings
     * 
     * **Usage Examples:**
     * 
     * Secure storage (recommended for production):
     * ```kotlin
     * install(SecureStorage) {
     *     mode = EncryptedMode {
     *         keyProvider = PassphraseKeyProvider("secure-passphrase", salt, 100000)
     *         databasePath = "secure.db"
     *     }
     * }
     * ```
     * 
     * Plain storage (development/testing only):
     * ```kotlin
     * install(SecureStorage) {
     *     mode = PlainMode {
     *         databasePath = "dev.db"
     *         suppressSecurityWarning = true  // Acknowledge security implications
     *     }
     * }
     * ```
     * 
     * Other features automatically use the configured storage:
     * ```kotlin
     * install(AgentMemory) {
     *     memoryProvider = SecureMemoryProvider()  // Uses SecureStorage config
     * }
     * 
     * install(Persistency) {
     *     persistencyProvider = SecurePersistencyProvider()  // Uses SecureStorage config
     * }
     * ```
     * 
     * **Enterprise Security Features:**
     * - GDPR/SOC2/HIPAA compliance-ready encryption
     * - Configurable key management (passphrases, keystores)
     * - High-performance SQLite backend with B-tree indexes
     * - Multiplatform compatibility (JVM, JS)
     * - Security warnings for unencrypted usage
     */
    public companion object Feature : AIAgentFeature<SecureStorageConfig, SecureStorage> {
        override val key: AIAgentStorageKey<SecureStorage> = 
            createStorageKey<SecureStorage>("secure-storage-feature")
        
        override fun createInitialConfig(): SecureStorageConfig = SecureStorageConfig()
        
        override fun install(config: SecureStorageConfig, pipeline: AIAgentPipeline) {
            pipeline.interceptContextAgentFeature(this) { agentContext ->
                SecureStorage(config)
            }
        }
    }
}

/**
 * Configuration for the SecureStorage feature.
 * 
 * Security-focused configuration with encrypted mode as the recommended default.
 * Plain mode is available but discouraged for production use.
 */
public class SecureStorageConfig : FeatureConfig() {
    /**
     * Storage mode selection - plain (default) or encrypted (requires key provider).
     * 
     * **PlainMode**: Default mode for development (with security warning)
     * **EncryptedMode**: AES-256-GCM authenticated encryption (configure keyProvider)
     */
    public var mode: StorageMode = PlainMode()
}

/**
 * Base interface for storage mode configurations.
 * 
 * Sealed interface ensures type safety and allows for future
 * storage mode additions (e.g., RemoteStorageMode, etc.)
 */
@Serializable
public sealed interface StorageMode {
    /**
     * Database file path for the storage backend.
     */
    public val databasePath: String
}

/**
 * Plain (unencrypted) storage mode configuration.
 * 
 * ⚠️  **SECURITY WARNING**: This mode stores data unencrypted and should only be used 
 * for development, testing, or when compliance requirements don't mandate encryption.
 * 
 * **Use cases:**
 * - Development and testing environments
 * - Non-sensitive data workloads
 * - Performance-critical applications (with acknowledged security trade-offs)
 * - Environments where encryption is handled at infrastructure level
 * 
 * @property databasePath Path to the SQLite database file
 * @property suppressSecurityWarning Set to true to acknowledge security implications and suppress warnings
 */
@Serializable
public data class PlainMode(
    override val databasePath: String = "koog-plain.db",
    val suppressSecurityWarning: Boolean = false
) : StorageMode {
    
    init {
        if (!suppressSecurityWarning) {
            // Emit security warning when plain mode is used without acknowledgment
            println("⚠️  SECURITY WARNING: SecureStorage is configured in Plain mode.")
            println("   Data will NOT be encrypted. This is recommended only for development.")
            println("   To suppress this warning, set suppressSecurityWarning = true.")
            println("   For production, use EncryptedMode with proper key management.")
        }
    }
}

/**
 * Encrypted storage mode configuration (recommended).
 * 
 * Provides AES-256-GCM authenticated encryption for enterprise-grade data protection.
 * This is the recommended mode for all production deployments.
 * 
 * **Ideal for:**
 * - Production environments
 * - Sensitive data workloads (PII, credentials, business data)
 * - Compliance requirements (GDPR, SOC2, HIPAA)
 * - Enterprise deployments
 * 
 * **Security Features:**
 * - AES-256-GCM authenticated encryption
 * - Configurable key management
 * - Protection against unauthorized device access
 * - Enterprise compliance ready
 * - Forward secrecy (unique IV per operation)
 * 
 * @property keyProvider Encryption key provider for data protection
 * @property databasePath Path to the encrypted SQLite database file
 */
@Serializable
public data class EncryptedMode(
    val keyProvider: EncryptionKeyProvider? = null, // Allow lazy initialization
    override val databasePath: String = "koog-secure.db"
) : StorageMode