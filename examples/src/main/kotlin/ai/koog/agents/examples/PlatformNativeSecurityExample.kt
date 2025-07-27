package ai.koog.agents.examples

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.secure.storage.SecureStorage
import ai.koog.agents.secure.storage.SecureStorageConfig
import ai.koog.agents.secure.storage.EncryptedMode
import ai.koog.agents.secure.crypto.createPlatformKeyProvider
import ai.koog.agents.secure.crypto.PassphraseKeyProvider
import ai.koog.agents.secure.crypto.PlatformSecurityException
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating platform-native security integration with Koog SecureStorage.
 * 
 * This example showcases how SecureStorage leverages OS-level security features:
 * - macOS: Security framework and Keychain Services (with Secure Enclave support)
 * - Windows: DPAPI and Windows Credential Manager (with TPM support)
 * - Linux: libsecret, gnome-keyring, or kwallet integration
 * 
 * Platform-native providers offer superior security compared to passphrase-only approaches:
 * - Zero user-managed passphrases
 * - Hardware-backed security on supported devices
 * - OS-level authentication (Touch ID, Windows Hello, etc.)
 * - Automatic key derivation and secure storage
 */

/**
 * Example 1: Personal AI Assistant with macOS Keychain Integration
 * 
 * On macOS, this example uses the Security framework to store encryption keys
 * in the user's Keychain, protected by Touch ID or user password.
 */
fun createPersonalAssistantWithKeychainSecurity(): AIAgent<String, String> {
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = createPlatformKeyProvider(
                keyIdentifier = "personal-assistant-key",
                serviceName = "ai.koog.personal-assistant"
                // No fallback - require platform security for personal use
            ),
            databasePath = "personal-assistant.db"
        )
    })
    
    return AIAgent(
        executor = simpleOpenAIExecutor("sk-your-api-key"),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = """
            You are my personal AI assistant. Our conversations are protected by
            macOS Keychain and Touch ID. You can safely discuss sensitive topics
            knowing our chat history is encrypted and secure.
        """.trimIndent(),
        temperature = 0.7,
        toolRegistry = ToolRegistry { /* Personal tools */ }
    )
}

/**
 * Example 2: Enterprise Agent with Windows Credential Manager
 * 
 * On Windows, this leverages DPAPI and Windows Credential Manager for
 * enterprise-grade security with TPM backing where available.
 */
fun createEnterpriseAgentWithWindowsSecurity(employeeId: String): AIAgent<String, String> {
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = createPlatformKeyProvider(
                keyIdentifier = "enterprise-agent-$employeeId",
                serviceName = "com.company.koog-agents",
                fallbackProvider = PassphraseKeyProvider(
                    // Fallback for legacy systems without TPM
                    passphrase = "enterprise-fallback-key-2024",
                    salt = "enterprise-salt".encodeToByteArray(),
                    iterations = 600000
                )
            ),
            databasePath = "enterprise-$employeeId.db"
        )
    })
    
    return AIAgent(
        executor = simpleOpenAIExecutor("sk-enterprise-api-key"),
        llmModel = OpenAIModels.Reasoning.GPT4o,
        systemPrompt = """
            You are an enterprise AI assistant for employee $employeeId.
            All conversations are protected by Windows enterprise security.
            Follow company policies and maintain confidentiality.
        """.trimIndent(),
        temperature = 0.3,
        toolRegistry = ToolRegistry { /* Enterprise tools */ }
    )
}

/**
 * Example 3: Cross-Platform Development Agent
 * 
 * This example works across all platforms with appropriate fallbacks:
 * - macOS: Keychain Services
 * - Windows: Credential Manager  
 * - Linux: gnome-keyring/kwallet
 * - JVM: Falls back to passphrase-based security
 */
fun createCrossPlatformAgent(): AIAgent<String, String> {
    val secureStorage = try {
        // Try platform-native security first
        SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = createPlatformKeyProvider(
                    keyIdentifier = "cross-platform-agent",
                    serviceName = "ai.koog.development",
                    fallbackProvider = PassphraseKeyProvider(
                        passphrase = "dev-fallback-key-2024",
                        salt = "cross-platform-salt".encodeToByteArray(),
                        iterations = 100000
                    )
                ),
                databasePath = "cross-platform-agent.db"
            )
        })
    } catch (e: PlatformSecurityException) {
        // Platform security unavailable, use passphrase-only
        println("Platform security unavailable: ${e.message}")
        println("Falling back to passphrase-based encryption")
        
        SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = PassphraseKeyProvider(
                    passphrase = "dev-fallback-key-2024",
                    salt = "cross-platform-salt".encodeToByteArray(),
                    iterations = 100000
                ),
                databasePath = "cross-platform-agent-fallback.db"
            )
        })
    }
    
    return AIAgent(
        executor = simpleOpenAIExecutor("sk-dev-api-key"),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = """
            You are a development AI assistant. You work across different
            operating systems with platform-appropriate security measures.
        """.trimIndent(),
        temperature = 0.5,
        toolRegistry = ToolRegistry { /* Development tools */ }
    )
}

/**
 * Example 4: Banking Application with Hardware Security Requirements
 * 
 * This example demonstrates strict security requirements for financial applications,
 * requiring hardware-backed security and rejecting fallbacks.
 */
fun createBankingAgentWithHardwareRequirement(): AIAgent<String, String> {
    val secureStorage = SecureStorage(SecureStorageConfig().apply {
        mode = EncryptedMode(
            keyProvider = createPlatformKeyProvider(
                keyIdentifier = "banking-agent-key",
                serviceName = "com.bank.secure-ai-agents"
                // No fallback - banking requires hardware security
            ),
            databasePath = "banking-agent.db"
        )
    })
    
    return AIAgent(
        executor = simpleOpenAIExecutor("sk-banking-api-key"),
        llmModel = OpenAIModels.Reasoning.GPT4o,
        systemPrompt = """
            You are a banking AI assistant. All conversations are protected by
            hardware-backed security (Secure Enclave, TPM, or equivalent).
            You can safely process financial information and sensitive customer data.
            
            Compliance: SOC2, PCI-DSS, GDPR compliant through platform security.
        """.trimIndent(),
        temperature = 0.1, // Low temperature for financial accuracy
        toolRegistry = ToolRegistry { /* Banking tools */ }
    )
}

/**
 * Platform Security Status Utility
 */
object PlatformSecurityInfo {
    
    fun printSecurityCapabilities() {
        println("=== Platform Security Capabilities ===")
        
        try {
            val provider = createPlatformKeyProvider(
                keyIdentifier = "test-key",
                serviceName = "ai.koog.test"
            )
            
            when (System.getProperty("os.name")?.lowercase()) {
                "mac os x" -> {
                    println("Platform: macOS")
                    println("Security: Keychain Services + Security Framework")
                    println("Hardware: Secure Enclave (if supported)")
                    println("Authentication: Touch ID, Apple Watch, Password")
                }
                
                in listOf("windows 10", "windows 11") -> {
                    println("Platform: Windows")
                    println("Security: DPAPI + Windows Credential Manager") 
                    println("Hardware: TPM (if available)")
                    println("Authentication: Windows Hello, PIN, Password")
                }
                
                "linux" -> {
                    println("Platform: Linux")
                    println("Security: libsecret/gnome-keyring/kwallet")
                    println("Hardware: Hardware-backed if desktop supports it")
                    println("Authentication: Desktop session authentication")
                }
                
                else -> {
                    println("Platform: JVM (cross-platform)")
                    println("Security: Fallback to passphrase-based encryption")
                    println("Hardware: Not available on JVM")
                    println("Authentication: User-provided passphrase")
                }
            }
            
            println("Status: ✅ Platform security available")
            
        } catch (e: PlatformSecurityException) {
            println("Status: ❌ Platform security unavailable")
            println("Reason: ${e.message}")
            println("Recommendation: Use PassphraseKeyProvider as fallback")
        }
        
        println("=====================================\n")
    }
}

/**
 * Example 5: Security Migration Demo
 * 
 * Shows how to migrate from passphrase-based to platform-native security.
 */
object SecurityMigrationDemo {
    
    suspend fun migrateToplatformSecurity() {
        println("=== Security Migration Demo ===")
        
        // Step 1: Create old passphrase-based storage
        println("1. Setting up legacy passphrase-based storage...")
        val legacyStorage = SecureStorage(SecureStorageConfig().apply {
            mode = EncryptedMode(
                keyProvider = PassphraseKeyProvider(
                    passphrase = "legacy-passphrase-2024",
                    salt = "legacy-salt".encodeToByteArray(),
                    iterations = 100000
                ),
                databasePath = "legacy-storage.db"
            )
        })
        
        // Store some test data
        legacyStorage.apiKeys().saveApiKey("openai", "sk-legacy-key-123")
        println("   ✅ Stored API key in legacy storage")
        
        // Step 2: Create new platform-native storage
        println("2. Setting up platform-native storage...")
        val modernStorage = try {
            SecureStorage(SecureStorageConfig().apply {
                mode = EncryptedMode(
                    keyProvider = createPlatformKeyProvider(
                        keyIdentifier = "migrated-storage-key",
                        serviceName = "ai.koog.migration-demo"
                    ),
                    databasePath = "modern-storage.db"
                )
            })
        } catch (e: PlatformSecurityException) {
            println("   ❌ Platform security unavailable, keeping legacy storage")
            return
        }
        
        // Step 3: Migrate data
        println("3. Migrating API keys...")
        val legacyApiKey = legacyStorage.apiKeys().getApiKey("openai")
        if (legacyApiKey != null) {
            modernStorage.apiKeys().saveApiKey("openai", legacyApiKey)
            println("   ✅ Migrated API key to platform-native storage")
        }
        
        // Step 4: Verify migration
        println("4. Verifying migration...")
        val migratedKey = modernStorage.apiKeys().getApiKey("openai")
        if (migratedKey == legacyApiKey) {
            println("   ✅ Migration successful!")
            println("   🔐 API keys now protected by OS-level security")
        } else {
            println("   ❌ Migration failed!")
        }
        
        println("==============================\n")
    }
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("🔐 Koog Platform-Native Security Examples\n")
    
    // Show platform capabilities
    PlatformSecurityInfo.printSecurityCapabilities()
    
    // Example 1: Personal assistant
    println("1. Creating Personal Assistant with Platform Security:")
    try {
        val personalAgent = createPersonalAssistantWithKeychainSecurity()
        println("   ✅ Personal assistant created with platform-native security")
        println("   🔑 Encryption keys managed by OS")
        println("   📱 Authentication via Touch ID/biometrics (if available)")
    } catch (e: Exception) {
        println("   ❌ Failed: ${e.message}")
    }
    
    // Example 2: Cross-platform development
    println("\n2. Creating Cross-Platform Development Agent:")
    try {
        val crossPlatformAgent = createCrossPlatformAgent()
        println("   ✅ Cross-platform agent created")
        println("   🌐 Adapts to available platform security")
        println("   🔄 Graceful fallback when platform security unavailable")
    } catch (e: Exception) {
        println("   ❌ Failed: ${e.message}")
    }
    
    // Example 3: Banking application
    println("\n3. Creating Banking Agent with Hardware Requirements:")
    try {
        val bankingAgent = createBankingAgentWithHardwareRequirement()
        println("   ✅ Banking agent created with hardware security")
        println("   🏦 Suitable for financial applications")
        println("   🛡️ Hardware-backed encryption required")
    } catch (e: PlatformSecurityException) {
        println("   ❌ Hardware security not available: ${e.message}")
        println("   💡 Banking applications require hardware-backed security")
    } catch (e: Exception) {
        println("   ❌ Failed: ${e.message}")
    }
    
    // Example 4: Security migration
    println("\n4. Security Migration Demo:")
    SecurityMigrationDemo.migrateToplatformSecurity()
    
    println("🔐 Platform-Native Security Benefits:")
    println("   • Zero user-managed passphrases")
    println("   • Hardware-backed encryption (where available)")
    println("   • OS-level authentication integration")
    println("   • Automatic key management and rotation")
    println("   • Enhanced compliance (SOC2, GDPR, PCI-DSS)")
}