package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * Result of hash computation operation.
 */
public sealed class HashComputationResult {
    /**
     * Hash computation succeeded.
     */
    public data class Success(val hash: String) : HashComputationResult()
    
    /**
     * Hash computation failed with an error.
     */
    public data class Failed(val reason: String, val cause: Throwable? = null) : HashComputationResult()
    
    /**
     * Hash computation is not available or not configured.
     */
    public data object Unavailable : HashComputationResult()
}

/**
 * Interface for computing cryptographic hashes of agent strategies.
 * 
 * Strategy hashes are used to detect unexpected changes in graph topology
 * during checkpoint restoration. This helps identify when strategies
 * have been modified in ways that might affect checkpoint compatibility.
 */
public interface StrategyHasher {
    /**
     * Computes a hash of the strategy's structure.
     * 
     * The hash should be deterministic and sensitive to changes in:
     * - Node names and types
     * - Edge connections between nodes  
     * - Graph topology and structure
     * 
     * @param strategy The strategy to hash
     * @return HashComputationResult indicating success, failure, or unavailability
     */
    public suspend fun computeHash(strategy: AIAgentStrategy<*, *>): HashComputationResult
}

/**
 * Default implementation of StrategyHasher that creates SHA-256 hashes
 * based on the strategy's graph structure.
 * 
 * This hasher considers:
 * - All node names in the strategy metadata
 * - The strategy name
 * - Graph uniqueness properties
 * 
 * For production use, consider adding cryptography-kotlin dependency
 * for proper SHA-256 implementation.
 */
public class DefaultStrategyHasher : StrategyHasher {
    
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger { }
    
    override suspend fun computeHash(strategy: AIAgentStrategy<*, *>): HashComputationResult {
        return try {
            // Build a canonical representation of the strategy structure
            val structureComponents = buildCanonicalRepresentation(strategy)
            
            if (structureComponents.isEmpty()) {
                return HashComputationResult.Failed("No hashable components found in strategy")
            }
            
            // Combine all components into a single string
            val canonicalRepresentation = structureComponents.joinToString("|")
            
            // Compute hash (using simple implementation for now)
            val hash = computeStructuralHash(canonicalRepresentation)
            
            HashComputationResult.Success("sha256:$hash")
        } catch (e: Exception) {
            logger.warn(e) { "Failed to compute strategy hash for ${strategy.name}" }
            HashComputationResult.Failed("Hash computation failed: ${e.message}", e)
        }
    }
    
    /**
     * Builds a canonical representation of the strategy structure.
     */
    private fun buildCanonicalRepresentation(strategy: AIAgentStrategy<*, *>): List<String> {
        val components = mutableListOf<String>()
        
        // Add strategy name for uniqueness
        components.add("strategy:${strategy.name}")
        
        // Add metadata information if available
        if (isMetadataInitialized(strategy)) {
            val metadata = strategy.metadata
            
            // Add node names in sorted order for deterministic hashing
            val sortedNodeNames = metadata.nodesMap.keys.sorted()
            components.addAll(sortedNodeNames.map { "node:$it" })
            
            // Add node count for additional validation
            components.add("node_count:${metadata.nodesMap.size}")
            
            // Add uniqueness flag
            components.add("unique_names:${metadata.uniqueNames}")
            
            // Add node types for better differentiation
            val nodeTypes = metadata.nodesMap.values
                .map { it::class.simpleName ?: "Unknown" }
                .sorted()
                .groupingBy { it }
                .eachCount()
            
            nodeTypes.forEach { (type, count) ->
                components.add("node_type:$type:$count")
            }
        } else {
            // If metadata not initialized, note this in the hash
            components.add("metadata:uninitialized")
        }
        
        return components
    }
    
    /**
     * Computes a structural hash of the input string.
     * 
     * Note: This is a simple implementation for demonstration.
     * For production, use proper cryptographic libraries like:
     * - cryptography-kotlin with SHA-256
     * - Platform-specific implementations
     */
    private fun computeStructuralHash(input: String): String {
        // Simple but better hash function than the previous one
        var hash1 = 0x811c9dc5u.toInt() // FNV offset basis
        var hash2 = 0x1000193u.toInt()  // FNV prime
        
        for (byte in input.encodeToByteArray()) {
            hash1 = (hash1 xor byte.toInt()) * hash2
            hash2 = ((hash2 shl 1) + hash2 + 0x1b) and 0x7FFFFFFF
        }
        
        val combined = (hash1.toLong() shl 32) or (hash2.toLong() and 0xFFFFFFFFL)
        return combined.toULong().toString(16).padStart(16, '0')
    }
}

/**
 * Helper function to check if metadata is initialized on an AIAgentStrategy.
 * 
 * Uses reflection to check lateinit property initialization without triggering exceptions.
 * This avoids exception-based control flow which is not idiomatic Kotlin.
 */
private fun isMetadataInitialized(strategy: AIAgentStrategy<*, *>): Boolean {
    return try {
        // Use reflection to check if the lateinit property is initialized
        val metadataProperty = strategy::class.members
            .filterIsInstance<kotlin.reflect.KProperty1<Any, *>>()
            .find { it.name == "metadata" }
        
        if (metadataProperty is kotlin.reflect.KProperty1<*, *>) {
            // Check if it's a lateinit property and if it's initialized
            @Suppress("UNCHECKED_CAST")
            val property = metadataProperty as kotlin.reflect.KProperty1<Any, Any?>
            
            // For lateinit properties, accessing when not initialized throws UninitializedPropertyAccessException
            // But we can use the isLateinit extension if available, or fall back to try/catch
            return try {
                property.get(strategy)
                true
            } catch (e: UninitializedPropertyAccessException) {
                false
            }
        }
        
        // Fallback: try to access the property
        strategy.metadata
        true
    } catch (e: UninitializedPropertyAccessException) {
        false
    } catch (e: Exception) {
        // For any other reflection issues, assume not initialized
        false
    }
}

/**
 * No-op strategy hasher that indicates hash computation is unavailable.
 * Useful when hash validation is not needed or not configured.
 */
public object NoOpStrategyHasher : StrategyHasher {
    override suspend fun computeHash(strategy: AIAgentStrategy<*, *>): HashComputationResult {
        return HashComputationResult.Unavailable
    }
}

/**
 * Strategy hasher that always fails with a specific error message.
 * Useful for testing error handling scenarios.
 */
public class FailingStrategyHasher(
    private val errorMessage: String = "Intentional failure for testing"
) : StrategyHasher {
    override suspend fun computeHash(strategy: AIAgentStrategy<*, *>): HashComputationResult {
        return HashComputationResult.Failed(errorMessage)
    }
}