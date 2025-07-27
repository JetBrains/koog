# SecureStorage with API Key Management

## Overview

The SecureStorage feature has been extended with comprehensive API key management capabilities, enabling production-ready agent deployments with user-provided API keys. This addresses the real-world need for agents to securely store and manage user-specific secrets in SaaS applications, enterprise environments, and development workflows.

## Key Features

### 🔐 Secure API Key Storage
- **AES-256-GCM encryption** for all stored API keys
- **Context scoping** for user/agent/tenant isolation
- **Hierarchical fallback** system with environment variable support
- **Key rotation** and lifecycle management

### 🏢 Multi-Deployment Support
- **SaaS applications**: Users provide their own API keys
- **Enterprise**: Department/tenant-scoped key management
- **Development**: Environment variable fallback for easy local dev

### 🔄 Flexible Integration
- **Agent constructor patterns** for different deployment scenarios
- **Feature-based architecture** with `install(SecureStorage)`
- **Automatic encryption** - only works in encrypted mode for security

## Quick Start

### 1. Basic Setup

```kotlin
// Configure SecureStorage with encrypted mode
val agents = AIAgents {
    install(SecureStorage) {
        mode = EncryptedMode(
            keyProvider = PassphraseKeyProvider(
                passphrase = "secure-master-passphrase", 
                salt = "unique-salt-bytes".encodeToByteArray(),
                iterations = 600000
            ),
            databasePath = "secure-storage.db"
        )
    }
}

val secureStorage = agents.getFeature(SecureStorage)
```

### 2. Store User API Keys

```kotlin
// Store user's API keys with context scoping
val userId = "user:alice"
secureStorage.apiKeys(userId).apply {
    saveApiKey("openai", "sk-user-provided-openai-key")
    saveApiKey("anthropic", "sk-ant-user-anthropic-key")
}
```

### 3. Agent Constructor Patterns

```kotlin
// Personal Assistant Agent (SaaS pattern)
class PersonalAssistantAgent(
    private val userId: String,
    private val secureStorage: SecureStorage
) : AIAgent {
    
    suspend fun chatWithUser(message: String): String {
        val resolver = secureStorage.apiKeyResolver()
        val apiKey = resolver.resolveApiKey(
            service = "openai",
            userContext = "user:$userId"
        ) ?: throw SecurityException("No OpenAI API key available")
        
        // Use the API key for OpenAI requests
        return processMessage(message, apiKey)
    }
    
    companion object {
        suspend fun createForUser(
            userId: String,
            userApiKey: String,
            secureStorage: SecureStorage
        ): PersonalAssistantAgent {
            // Store user's API key securely
            secureStorage.apiKeys("user:$userId").saveApiKey("openai", userApiKey)
            return PersonalAssistantAgent(userId, secureStorage)
        }
    }
}
```

## API Reference

### SecureApiKeyStorage Interface

```kotlin
interface SecureApiKeyStorage {
    suspend fun saveApiKey(service: String, apiKey: String, context: String? = null)
    suspend fun getApiKey(service: String, context: String? = null): String?
    suspend fun deleteApiKey(service: String, context: String? = null): Boolean
    suspend fun listServices(context: String? = null): List<String>
    suspend fun rotateApiKey(service: String, newApiKey: String, context: String? = null)
    suspend fun hasApiKey(service: String, context: String? = null): Boolean
}
```

### ApiKeyResolver (Hierarchical Fallback)

```kotlin
class ApiKeyResolver {
    suspend fun resolveApiKey(
        service: String,
        userContext: String? = null,
        agentContext: String? = null
    ): String?
    
    suspend fun getApiKeySource(
        service: String,
        userContext: String? = null,
        agentContext: String? = null
    ): ApiKeySource?
}
```

### SecureStorage Feature Extensions

```kotlin
class SecureStorage {
    // Basic API key storage (requires encrypted mode)
    fun apiKeys(): SecureApiKeyStorage
    
    // Context-scoped API key storage  
    fun apiKeys(context: String): SecureApiKeyStorage
    
    // Multi-level resolver with environment fallback
    fun apiKeyResolver(): ApiKeyResolver
}
```

## Deployment Patterns

### 1. SaaS Application Pattern

**Use Case**: Users provide their own API keys in a web application

```kotlin
suspend fun main() {
    val secureStorage = setupProductionStorage()
    
    // User signs up and provides API key via UI
    val agent = PersonalAssistantAgent.createForUser(
        userId = "alice",
        userApiKey = userProvidedKeyFromUI,
        secureStorage = secureStorage
    )
    
    val response = agent.chatWithUser("Hello!")
}
```

### 2. Enterprise Multi-Tenant Pattern

**Use Case**: Admin configures API keys per department/tenant

```kotlin
suspend fun setupEnterprise() {
    val secureStorage = setupEnterpriseStorage()
    
    // Admin configures department keys
    val enterpriseAgent = EnterpriseAgent.setupForDepartment(
        tenantId = "acme-corp",
        departmentId = "marketing",
        departmentApiKey = adminProvidedKey,
        secureStorage = secureStorage
    )
}
```

### 3. Development Pattern

**Use Case**: Local development with environment fallback

```kotlin
suspend fun development() {
    // Set OPENAI_API_KEY environment variable
    val secureStorage = setupDevelopmentStorage()
    val devAgent = DevelopmentAgent(secureStorage)
    
    // Automatically falls back to environment variables
    val response = devAgent.testFeature("new-feature")
}
```

## Security Features

### 🔒 Encryption
- **AES-256-GCM**: Industry-standard authenticated encryption
- **Unique IVs**: Every encryption operation uses a fresh initialization vector
- **Authentication tags**: Prevents tampering with encrypted data

### 🏷️ Context Isolation
- **User scoping**: `user:alice` keys are isolated from `user:bob`
- **Agent scoping**: `agent:assistant` keys are separate from `agent:analyzer`
- **Tenant scoping**: `tenant:acme` keys are isolated from `tenant:globex`

### 🔄 Fallback Hierarchy
1. **User + Agent specific**: `user:alice:agent:assistant`
2. **User specific**: `user:alice`
3. **Agent specific**: `agent:assistant`
4. **Global**: No context
5. **Environment**: `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, etc.

### ⚠️ Security Enforcement
- **Encrypted mode required**: API key storage only works with encrypted storage
- **No plain mode**: Prevents accidental storage of API keys in unencrypted form
- **Secure deletion**: Keys are properly cleaned up when deleted

## Environment Variable Support

The system automatically recognizes common API key environment variables:

| Service | Environment Variables |
|---------|----------------------|
| OpenAI | `OPENAI_API_KEY` |
| Anthropic | `ANTHROPIC_API_KEY` |
| GitHub | `GITHUB_API_KEY`, `GITHUB_TOKEN` |
| Slack | `SLACK_API_KEY`, `SLACK_BOT_TOKEN` |
| Discord | `DISCORD_API_KEY`, `DISCORD_BOT_TOKEN` |
| Google | `GOOGLE_API_KEY` |
| HuggingFace | `HUGGINGFACE_API_KEY`, `HF_TOKEN` |
| Others | `{SERVICE}_API_KEY` (standard format) |

## Examples

See the complete examples in:
- [`SecureStorageApiKeyExample.kt`](SecureStorageApiKeyExample.kt) - Agent constructor patterns
- [`SecureStorageSetupExample.kt`](SecureStorageSetupExample.kt) - Configuration examples

## Migration Guide

### From Basic SecureStorage

If you're already using SecureStorage, adding API key management is simple:

```kotlin
// Before: Basic secure storage
val secureStorage = agents.getFeature(SecureStorage)

// After: Add API key management
val apiKeys = secureStorage.apiKeys("user:alice")
apiKeys.saveApiKey("openai", userProvidedKey)

val resolver = secureStorage.apiKeyResolver()
val key = resolver.resolveApiKey("openai", "user:alice")
```

### Security Requirements

- **Must use EncryptedMode**: API key features require encrypted storage
- **Strong master keys**: Use secure passphrases or environment-based keys
- **Proper salt management**: Use unique salts per deployment
- **Key rotation**: Implement regular API key rotation policies

## Production Checklist

- [ ] Use EncryptedMode with strong master passphrase/key
- [ ] Configure unique salt per deployment environment
- [ ] Set up proper key rotation procedures
- [ ] Implement audit logging for key access
- [ ] Test fallback hierarchy in your deployment environment
- [ ] Document key management procedures for your team
- [ ] Set up monitoring for API key usage and failures

## Architecture

The API key management system builds on the existing SecureStorage infrastructure:

```
┌─────────────────────────────────────────────────────────────────┐
│                     SecureStorage Feature                      │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐    │
│ │ SecureApiKey    │ │ ApiKeyResolver  │ │ ContextScoped   │    │
│ │ Storage         │ │                 │ │ ApiKeyStorage   │    │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                    EncryptedKVStorage                          │
├─────────────────────────────────────────────────────────────────┤
│                  AES-256-GCM Encryption                        │
├─────────────────────────────────────────────────────────────────┤
│                    KottageLocalKVBackend                       │
├─────────────────────────────────────────────────────────────────┤
│                      SQLite Database                           │
└─────────────────────────────────────────────────────────────────┘
```

This extension transforms SecureStorage from basic encrypted storage into a complete secrets management solution for production agent deployments.