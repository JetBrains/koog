# Module agents-features-secure-storage

Provides **secure, encrypted SQLite-based storage providers** for AI agent memory and persistency features using SQLDelight with AES-256-GCM encryption.

## Overview

The agents-features-secure-storage module addresses a critical security gap in AI agent frameworks by providing:

- **Encrypted memory storage** - Drop-in replacement for LocalFileMemoryProvider with AES-256-GCM encryption
- **Encrypted persistency storage** - Secure checkpoint storage for agent state with value-level encryption  
- **Enterprise-grade security** - GDPR/SOC2 compliant data protection for sensitive AI workloads
- **Flexible key management** - Pluggable key providers (environment variables, passphrases, keystores)
- **SQLite performance** - High-performance indexed storage with encrypted data at rest using SQLDelight

### Key Security Benefits

- **Data protection at rest** - All values encrypted with AES-256-GCM before storage
- **Protection against device access** - Encrypted data is unreadable without proper encryption keys
- **Compliance ready** - Meets enterprise security requirements (GDPR Article 32, SOC2 CC6.1)
- **Competitive advantage** - First AI agent framework with built-in encryption support

### Using in your project

To use secure storage in your project, add the following dependency:

```kotlin
dependencies {
    implementation("ai.koog.agents:agents-features-secure-storage:$version")
}
```

### Example: Secure Memory Provider

Replace your existing LocalFileMemoryProvider with encrypted storage:

```kotlin
val myAgent = AIAgents(
    // other configuration parameters
) {
    install(AgentMemory) {
        memoryProvider = KottageAgentMemoryProvider(
            config = SecureMemoryConfig("encrypted-memory"),
            keyProvider = EnvVarKeyProvider("KOOG_MEMORY_KEY"),
            databasePath = "memory/secure.db"
        )
        featureName = "my-feature"
        organizationName = "my-organization"
    }
}
```

### Example: Secure Persistency Provider

Encrypt agent checkpoints and state:

```kotlin  
val myAgent = AIAgents(
    // other configuration parameters
) {
    install(Persistency) {
        persistencyProvider = KottagePersistencyStorageProvider(
            config = SecurePersistencyConfig("encrypted-checkpoints"),
            keyProvider = PassphraseKeyProvider(userPassphrase),
            databasePath = "checkpoints/secure.db",
            persistenceId = "my-agent-v1"
        )
    }
}
```

### Key Management Options

Choose the appropriate key provider for your deployment:

```kotlin
// Development: Environment variable
val envKeyProvider = EnvVarKeyProvider("KOOG_ENCRYPTION_KEY")

// Production: User-provided passphrase  
val passphraseProvider = PassphraseKeyProvider("secure-passphrase-123")

// Enterprise: Future keystore integration
val keystoreProvider = KeystoreKeyProvider(keystoreConfig)
```

### Security Architecture

The module uses a layered security approach:

1. **Application Layer** - Same AgentMemoryProvider and PersistencyStorageProvider interfaces
2. **Koog Abstraction Layer** - Secure configuration and key management abstractions
3. **Encryption Layer** - AES-256-GCM value-level encryption via KottageEncoder
4. **Storage Layer** - Kottage SQLite-based key-value storage with encrypted values

### Enterprise Compliance

This implementation enables compliance with:

- **GDPR Article 32** - "Appropriate technical measures" including encryption of personal data
- **SOC2 CC6.1** - Logical and physical access security including encrypted data storage  
- **HIPAA Technical Safeguards** - Encryption of sensitive health information at rest

### Performance Benefits

Compared to JSON file storage:

- **Indexed queries** - SQLite B-tree indexes for faster fact retrieval
- **Atomic operations** - ACID compliance for concurrent agent operations
- **Efficient storage** - Binary storage format reduces disk space usage
- **Scalable architecture** - Handles large memory datasets without performance degradation

### Migration Path

Existing agents can migrate incrementally:

```kotlin
// Before: Plaintext JSON storage
memoryProvider = LocalFileMemoryProvider(...)

// After: Encrypted SQLite storage  
memoryProvider = KottageAgentMemoryProvider(...)
```

### Storage Layer Architecture

The module provides clean, well-organized architecture:

**Core abstractions:**
```kotlin
ai.koog.agents.secure.storage/
├── LocalKVStorage         // Main storage interface
└── LocalKVBackend         // Backend abstraction
```

**Implementations:**
```kotlin
ai.koog.agents.secure.storage.impl/
├── PlainKVStorage         // Plain (unencrypted) storage
├── EncryptedKVStorage     // AES-256-GCM encrypted storage
└── kottage/               // Kottage-specific implementations
    ├── KottageLocalKVBackend
    ├── KottageAgentMemoryProvider
    └── KottagePersistencyStorageProvider
```

**Usage:**
```kotlin
// Plain storage (no encryption)
val plainStorage = PlainKVStorage(backend)

// Encrypted storage (AES-256-GCM)
val encryptedStorage = EncryptedKVStorage(backend, keyProvider)
```

No changes to agent logic or memory operations are required.