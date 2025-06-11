package ai.koog.agents.memory.config

import ai.koog.agents.memory.model.MemoryScope
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
    /**
     * Secondary constructor for `MemoryScopesProfile` that initializes the profile with a variable
     * number of `MemoryScopeType` to String mappings. This constructor converts the provided pairs
     * into a mutable map and passes it to the primary constructor.
     *
     * @param scopeNames Variable number of pairs where the key is a `MemoryScopeType` and the value
     *   is the associated scope name.
     */
    public constructor(vararg scopeNames: Pair<MemoryScopeType, String>) : this(
        scopeNames.toMap().toMutableMap()
    )

    /**
     * Retrieves the name associated with a specific memory scope type.
     *
     * @param type The memory scope type for which the name is required.
     * @return The name corresponding to the given memory scope type, or null if no name is associated.
     */
    public fun nameOf(type: MemoryScopeType): String? = names[type]

    /**
     * Retrieves the memory scope associated with the specified memory scope type.
     *
     * @param type The type of memory scope to retrieve.
     * @return The corresponding memory scope if the type is valid and has a name,
     *         or null if no name is associated with the provided type.
     */
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
