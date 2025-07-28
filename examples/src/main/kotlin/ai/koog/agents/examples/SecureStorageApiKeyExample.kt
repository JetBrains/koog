package ai.koog.agents.examples

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.secure.storage.SecureStorage
import ai.koog.agents.secure.storage.SecureStorageConfig
import ai.koog.agents.secure.storage.EncryptedMode
import ai.koog.agents.secure.crypto.PassphraseKeyProvider
import ai.koog.agents.secure.crypto.createPlatformKeyProvider
import ai.koog.agents.secure.apikeys.ApiKeySource
import ai.koog.agents.secure.apikeys.ApiKeyProviders
import ai.koog.agents.secure.apikeys.resolveApiKey
import ai.koog.agents.secure.apikeys.saveApiKey
import ai.koog.agents.secure.storage.backend.SecureKVBackendImpl
import ai.koog.agents.secure.storage.backend.SecurePersistencyBackendImpl
import ai.koog.agents.memory.providers.SecureMemoryProvider
import ai.koog.agents.memory.providers.SecureMemoryConfig
import ai.koog.agents.snapshot.providers.SecurePersistencyProvider
import ai.koog.agents.snapshot.providers.SecurePersistencyConfig
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating how to use SecureStorage with API key management in real agent implementations.
 * 
 * This example shows multiple patterns for managing user-provided API keys in production
 * agent deployments, including SaaS applications and enterprise environments.
 */

// Example 1: Personal Assistant Agent with User-provided API Keys
fun createPersonalAssistantAgent(
    userId: String,
    secureStorage: SecureStorage
): AIAgent<String, String> {
    // Validate that user has provided required API keys
    val resolver = secureStorage.apiKeyResolver()
    
    // Get user's API key for OpenAI
    val userApiKey = runBlocking {
        resolver.resolveApiKey(
            ApiKeyProviders.OpenAI,
            userContext = "user:$userId",
            agentContext = "agent:personal-assistant"
        ) ?: throw SecurityException("No OpenAI API key available for user $userId")
    }
    
    return AIAgent(
        executor = simpleOpenAIExecutor(userApiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a personal assistant for user $userId. Be helpful and personalized.",
        temperature = 0.7,
        toolRegistry = ToolRegistry { /* Add tools as needed */ }
    )
}

/**
 * Factory method for creating PersonalAssistantAgent with user API key setup.
 */
suspend fun createPersonalAssistantAgentWithUserKey(
    userId: String,
    userApiKey: String,
    secureStorage: SecureStorage
): AIAgent<String, String> {
    // Store user's API key securely
    secureStorage.apiKeys("user:$userId").saveApiKey(ApiKeyProviders.OpenAI, userApiKey)
    
    return createPersonalAssistantAgent(userId, secureStorage)
}

// Example 2: Multi-Tenant Enterprise Agent
fun createEnterpriseAgent(
    tenantId: String,
    departmentId: String,
    secureStorage: SecureStorage
): AIAgent<String, String> {
    /**
     * Enterprise agent with hierarchical API key resolution:
     * 1. Department-specific keys
     * 2. Tenant-wide keys  
     * 3. Organization default keys
     * 4. Environment fallback
     */
    
    val userContext = "tenant:$tenantId"
    val agentContext = "dept:$departmentId"
    val resolver = secureStorage.apiKeyResolver()
    
    // Get the most specific API key available
    val apiKey = runBlocking {
        resolver.resolveApiKey(
            ApiKeyProviders.OpenAI,
            userContext = userContext,
            agentContext = agentContext
        ) ?: throw SecurityException("No API key configured for tenant $tenantId")
    }
    
    return AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are an enterprise assistant for $tenantId department $departmentId. Follow corporate guidelines.",
        temperature = 0.3,
        toolRegistry = ToolRegistry { /* Add enterprise tools */ }
    )
}

/**
 * Setup enterprise agent with department-specific API key configuration.
 */
suspend fun createEnterpriseAgentWithDepartmentKey(
    tenantId: String,
    departmentId: String,
    departmentApiKey: String,
    secureStorage: SecureStorage
): AIAgent<String, String> {
    // Store department-specific API key
    val departmentContext = "tenant:$tenantId:dept:$departmentId"
    secureStorage.apiKeys().saveApiKey(ApiKeyProviders.OpenAI, departmentApiKey, departmentContext)
    
    return createEnterpriseAgent(tenantId, departmentId, secureStorage)
}

// Example 3: Development Agent with Environment Fallback
fun createDevelopmentAgent(
    secureStorage: SecureStorage
): AIAgent<String, String> {
    /**
     * Development-friendly agent that uses environment variables as fallback.
     * Perfect for local development and testing.
     */
    
    // Try to get API key from secure storage or fall back to environment
    val apiKey = try {
        val resolver = secureStorage.apiKeyResolver()
        runBlocking {
            resolver.resolveApiKey(ApiKeyProviders.OpenAI)
        }
    } catch (e: SecurityException) {
        // If secure storage is in plain mode, fall back to ApiKeyService
        null
    } ?: ApiKeyService.openAIApiKey
    
    return AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = "You are a development assistant. Help test and debug features.",
        temperature = 0.1,
        toolRegistry = ToolRegistry { /* Add development tools */ }
    )
}

// Example 4: Agent Factory with Different Key Strategies
object AgentFactory {
    
    /**
     * Creates agents with different API key strategies based on deployment environment.
     */
    
    suspend fun createProductionAgent(
        userId: String,
        userProvidedKey: String
    ): AIAgent<String, String> {
        // Production: User must provide their own keys
        val secureStorage = setupProductionStorage()
        return createPersonalAssistantAgentWithUserKey(userId, userProvidedKey, secureStorage)
    }
    
    suspend fun createDevelopmentAgent(): AIAgent<String, String> {
        // Development: Use environment variables
        val secureStorage = setupDevelopmentStorage()
        return createDevelopmentAgent(secureStorage)
    }
    
    suspend fun createEnterpriseAgent(
        tenantId: String,
        departmentId: String,
        adminProvidedKey: String
    ): AIAgent<String, String> {
        // Enterprise: Admin configures keys per department
        val secureStorage = setupEnterpriseStorage()
        return createEnterpriseAgentWithDepartmentKey(
            tenantId, departmentId, adminProvidedKey, secureStorage
        )
    }
    
    private fun setupProductionStorage(): SecureStorage {
        return SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = createPlatformKeyProvider(
                    keyIdentifier = "koog-production-key",
                    serviceName = "ai.koog.agents.production",
                    fallbackProvider = PassphraseKeyProvider(
                        passphrase = System.getenv("MASTER_PASSPHRASE") 
                            ?: throw IllegalStateException("MASTER_PASSPHRASE required"),
                        salt = "production-salt-change-me".encodeToByteArray(),
                        iterations = 600000
                    )
                ),
                databasePath = "production-secure.db"
            )
        })
    }
    
    private fun setupDevelopmentStorage(): SecureStorage {
        return SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = PassphraseKeyProvider(
                    passphrase = "dev-passphrase-change-me", 
                    salt = "dev-salt".encodeToByteArray(),
                    iterations = 100000
                ),
                databasePath = "dev-secure.db"
            )
        })
    }
    
    private fun setupEnterpriseStorage(): SecureStorage {
        return SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = createPlatformKeyProvider(
                    keyIdentifier = "koog-enterprise-key",
                    serviceName = "ai.koog.agents.enterprise",
                    fallbackProvider = PassphraseKeyProvider(
                        passphrase = System.getenv("ENTERPRISE_MASTER_KEY")
                            ?: throw IllegalStateException("ENTERPRISE_MASTER_KEY required"),
                        salt = "enterprise-salt-change-me".encodeToByteArray(),
                        iterations = 600000
                    )
                ),
                databasePath = "enterprise-secure.db"
            )
        })
    }
}

// Example 5: Usage Demonstration
suspend fun main() {
    println("=== SecureStorage API Key Management Examples ===\n")
    
    // Production SaaS example
    println("1. Production SaaS Agent:")
    try {
        val productionAgent = AgentFactory.createProductionAgent(
            userId = "alice",
            userProvidedKey = "sk-user-provided-key-123"
        )
        val response = productionAgent.run("Hello, how are you?")
        println("   Response: $response\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}\n")
    }
    
    // Development example
    println("2. Development Agent with Environment Fallback:")
    try {
        val devAgent = AgentFactory.createDevelopmentAgent()
        val response = devAgent.run("Test new chat feature")
        println("   Response: $response\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}\n")
    }
    
    // Enterprise example
    println("3. Enterprise Multi-Tenant Agent:")
    try {
        val enterpriseAgent = AgentFactory.createEnterpriseAgent(
            tenantId = "acme-corp",
            departmentId = "sales",
            adminProvidedKey = "sk-enterprise-sales-key-456"
        )
        val response = enterpriseAgent.run("Generate sales report")
        println("   Response: $response\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}\n")
    }
    
    println("=== API Key Management Patterns ===")
    println("• Personal Assistant: User provides own API keys")
    println("• Enterprise: Admin configures department keys")  
    println("• Development: Environment variable fallback")
    println("• All modes: Secure AES-256-GCM encryption")
}

/**
 * Example 6: Generic Backend Integration Pattern
 * 
 * This demonstrates how to use the new generic backend architecture where
 * memory and persistency features can use secure storage backends without
 * tight coupling to specific storage implementations.
 */
object GenericBackendExample {
    
    /**
     * Shows how to create an agent with secure memory using the pluggable backend pattern.
     */
    fun createAgentWithSecureMemory(secureStorage: SecureStorage): AIAgent<String, String> {
        // Create secure memory backend using the pluggable pattern
        val memoryBackend = SecureKVBackendImpl(secureStorage.storage())
        
        // The memory provider now uses the generic backend interface
        val memoryProvider = SecureMemoryProvider(
            config = SecureMemoryConfig(
                storageDirectory = "agent-memory"
            ),
            backend = memoryBackend
        )
        
        return AIAgent(
            executor = simpleOpenAIExecutor("your-api-key"),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = "You are an AI assistant with secure memory capabilities.",
            temperature = 0.7,
            toolRegistry = ToolRegistry { /* Add memory-enhanced tools */ }
        ) {
            // Memory would be configured in the agent setup
            // install(AgentMemory) { memoryProvider = memoryProvider }
        }
    }
    
    /**
     * Shows how to create an agent with secure persistence using the pluggable backend pattern.
     */
    fun createAgentWithSecurePersistence(secureStorage: SecureStorage): AIAgent<String, String> {
        // Create secure persistence backend using the pluggable pattern
        val persistencyBackend = SecurePersistencyBackendImpl(secureStorage.storage())
        
        // The persistence provider now uses the generic backend interface
        val persistencyProvider = SecurePersistencyProvider(
            config = SecurePersistencyConfig(
                persistenceId = "agent-session-123",
                maxCheckpoints = 10
            ),
            backend = persistencyBackend
        )
        
        return AIAgent(
            executor = simpleOpenAIExecutor("your-api-key"),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = "You are an AI assistant with secure checkpoint persistence.",
            temperature = 0.7,
            toolRegistry = ToolRegistry { /* Add persistence-enhanced tools */ }
        ) {
            // Persistence would be configured in the agent setup
            // install(Persistency) { persistencyProvider = persistencyProvider }
        }
    }
    
    /**
     * Complete example showing both secure memory and persistence with backend swapping capability.
     */
    fun createFullySecureAgent(secureStorage: SecureStorage): AIAgent<String, String> {
        // Both features use the same underlying secure storage but through different backends
        val memoryBackend = SecureKVBackendImpl(secureStorage.storage())
        val persistencyBackend = SecurePersistencyBackendImpl(secureStorage.storage())
        
        return AIAgent(
            executor = simpleOpenAIExecutor("your-api-key"),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = "You are a fully secure AI assistant with encrypted memory and persistence.",
            temperature = 0.7,
            toolRegistry = ToolRegistry { /* Add comprehensive agent tools */ }
        ) {
            // Both features can be installed with secure backends
            // install(AgentMemory) {
            //     memoryProvider = SecureMemoryProvider(
            //         config = SecureMemoryConfig(storageDirectory = "secure-memory"),
            //         backend = memoryBackend
            //     )
            // }
            // install(Persistency) {
            //     persistencyProvider = SecurePersistencyProvider(
            //         config = SecurePersistencyConfig(persistenceId = "secure-agent"),
            //         backend = persistencyBackend
            //     )
            // }
        }
    }
}