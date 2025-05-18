package ai.koog.agents.local.memory.config

import ai.koog.agents.local.memory.model.MemoryScope
import kotlinx.serialization.Serializable

/**
 * Defines the type of memory scope used for memory operations.
 * This enum represents different boundaries or contexts within
 * which memory can be stored and retrieved. Each type corresponds
 * to a specific operational scope for memory sharing and isolation.
 */
@Serializable
public enum class MemoryScopeType {
    /**
     * Represents a memory scope type associated with a product that populated some memory fact.
     */
    PRODUCT,

    /**
     * Represents a type of memory scope specifically for the "Agent" context.
     */
    AGENT,

    /**
     * Represents a memory scope type associated with a feature of your product (ex: within a scope of some feature
     * multiple independent agents might populate different facts and store them in the shared memory)
     */
    FEATURE,

    /**
     * Represents a memory scope type associated with the whole organization of yours (ex: within a scope of
     * your organization multiple products might have multiple features with different agents that populate facts and
     * store them in a shared memory)
     */
    ORGANIZATION
}

/**
 * Profile containing scopes for memory operations
 */
@Serializable
public data class MemoryScopesProfile(
    val names: MutableMap<MemoryScopeType, String> =  mutableMapOf()
) {
    public constructor(vararg scopeNames: Pair<MemoryScopeType, String>) : this(
        scopeNames.toMap().toMutableMap()
    )

    public fun nameOf(type: MemoryScopeType): String? = names[type]

    public fun getScope(type: MemoryScopeType): MemoryScope? {
        val name = nameOf(type) ?: return null
        return when (type) {
            MemoryScopeType.PRODUCT -> MemoryScope.Product(name)
            MemoryScopeType.AGENT -> MemoryScope.Agent(name)
            MemoryScopeType.FEATURE -> MemoryScope.Feature(name)
            MemoryScopeType.ORGANIZATION -> MemoryScope.CrossProduct
        }
    }
}
