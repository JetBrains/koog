package ai.koog.agents.examples

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.secure.storage.SecureStorage
import ai.koog.agents.secure.storage.SecureStorageConfig
import ai.koog.agents.secure.storage.EncryptedMode
import ai.koog.agents.secure.storage.PlainMode
import ai.koog.agents.secure.crypto.PassphraseKeyProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example showing how to use SecureStorage for API key management in agents.
 * 
 * Demonstrates different deployment scenarios:
 * - SaaS applications with user API keys
 * - Enterprise environments with tenant isolation  
 * - Development environments with fallbacks
 */

fun main() = runBlocking {
    println("=== SecureStorage Agent Examples ===\n")
    
    // Example 1: SaaS Application
    saasApplicationExample()
    
    // Example 2: Enterprise Setup  
    enterpriseExample()
    
    // Example 3: Development Setup
    developmentExample()
    
    // Example 4: API Key Management Demo
    apiKeyManagementDemo()
}

/**
 * SaaS Application: Users provide their own API keys
 */
suspend fun saasApplicationExample() {
    println("1. SaaS Application (User provides API key)")
    
    // Set up secure storage
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = PassphraseKeyProvider(
                passphrase = "saas-master-key-change-in-production",
                salt = "saas-salt-12345678".encodeToByteArray(),
                iterations = 100000
            ),
            databasePath = "saas-user-keys.db"
        )
    })
    
    val userId = "alice"
    val userApiKey = "sk-user-alice-openai-key-123" // From user input
    
    try {
        // Create agent with user's API key using our helper function
        val agent = createPersonalAssistantAgentWithUserKey(userId, userApiKey, secureStorage)
        
        // Test the agent
        val response = agent.run("Hello! What can you help me with?")
        println("   Agent response: ${response.take(100)}...")
        
        // Show key source
        val resolver = secureStorage.apiKeyResolver()
        val source = resolver.getApiKeySource("openai", "user:$userId")
        println("   API key source: $source\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}\n")
    }
}

/**
 * Enterprise Setup: Multi-tenant with department scoping
 */
suspend fun enterpriseExample() {
    println("2. Enterprise Setup (Multi-Tenant)")
    
    // Set up enterprise secure storage
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = PassphraseKeyProvider(
                passphrase = "enterprise-master-key-change-me",
                salt = "enterprise-salt".encodeToByteArray(),
                iterations = 600000
            ),
            databasePath = "enterprise-secure.db"
        )
    })
    
    val tenantId = "acme-corp"
    val departmentId = "marketing"
    val adminApiKey = "sk-acme-marketing-dept-key-456"
    
    try {
        // Create enterprise agent with department key
        val agent = createEnterpriseAgentWithDepartmentKey(
            tenantId, departmentId, adminApiKey, secureStorage
        )
        
        // Test the agent
        val response = agent.run("Generate a marketing report summary")
        println("   Agent response: ${response.take(100)}...")
        
        // Show key hierarchy
        val resolver = secureStorage.apiKeyResolver()
        val source = resolver.getApiKeySource(
            "openai", 
            "tenant:$tenantId", 
            "dept:$departmentId"
        )
        println("   API key source: $source\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}\n")
    }
}

/**
 * Development Setup: Environment fallback for easy local development
 */
suspend fun developmentExample() {
    println("3. Development Setup (Environment Fallback)")
    
    // Set up development storage (plain mode for simplicity)
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = PlainMode(
            databasePath = "dev-storage.db",
            suppressSecurityWarning = true
        )
    })
    
    try {
        // Create development agent (falls back to ApiKeyService)
        val agent = createDevelopmentAgent(secureStorage)
        
        // Test the agent
        val response = agent.run("Help me debug this issue")
        println("   Agent response: ${response.take(100)}...")
        
        println("   Using fallback API key from ApiKeyService\n")
    } catch (e: Exception) {
        println("   Error: ${e.message}")
        println("   Note: Set OPENAI_API_KEY environment variable for development\n")
    }
}

/**
 * API Key Management Demonstration
 */
suspend fun apiKeyManagementDemo() {
    println("4. API Key Management Features")
    
    // Set up secure storage for demo
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = PassphraseKeyProvider(
                passphrase = "demo-passphrase",
                salt = "demo-salt-bytes-16".encodeToByteArray(),
                iterations = 100000
            ),
            databasePath = "demo-secure.db"
        )
    })
    
    val userContext = "user:demo"
    val userStorage = secureStorage.apiKeys(userContext)
    
    // Store multiple API keys
    userStorage.apply {
        saveApiKey("openai", "sk-demo-openai-key-123")
        saveApiKey("anthropic", "sk-ant-demo-anthropic-key-456") 
        saveApiKey("github", "ghp_demo-github-token-789")
    }
    
    // List all services
    val services = userStorage.listServices()
    println("   Stored services for $userContext: $services")
    
    // Key rotation
    println("   Rotating OpenAI key...")
    userStorage.rotateApiKey("openai", "sk-demo-new-openai-key-999")
    
    val rotatedKey = userStorage.getApiKey("openai")
    println("   New OpenAI key: ${rotatedKey?.take(15)}...")
    
    // Key existence check
    val hasAnthropic = userStorage.hasApiKey("anthropic")
    val hasGoogle = userStorage.hasApiKey("google")
    println("   Has Anthropic key: $hasAnthropic")
    println("   Has Google key: $hasGoogle")
    
    // Delete a key
    userStorage.deleteApiKey("github")
    val servicesAfterDelete = userStorage.listServices()
    println("   Services after deleting GitHub: $servicesAfterDelete")
    
    println("\n=== Key Management Features ===")
    println("• Encrypted storage with AES-256-GCM")
    println("• Context scoping (user:alice vs user:bob)")
    println("• Hierarchical fallback (user → agent → env)")
    println("• Key rotation and lifecycle management")
    println("• Service enumeration and existence checks")
}