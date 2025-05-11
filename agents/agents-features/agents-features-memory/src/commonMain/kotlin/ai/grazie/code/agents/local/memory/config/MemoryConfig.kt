package ai.grazie.code.agents.local.memory.config

import ai.grazie.code.agents.local.memory.model.MemoryScope
import kotlinx.serialization.Serializable

@Serializable
enum class MemoryScopeType {
    PRODUCT, AGENT, FEATURE, ORGANIZATION
}

/**
 * Profile containing scopes for memory operations
 */
@Serializable
data class MemoryScopesProfile(
    val names: MutableMap<MemoryScopeType, String> =  mutableMapOf()
) {
    constructor(vararg scopeNames: Pair<MemoryScopeType, String>) : this(
        scopeNames.toMap().toMutableMap()
    )

    fun nameOf(type: MemoryScopeType): String? = names[type]

    fun getScope(type: MemoryScopeType): MemoryScope? {
        val name = nameOf(type) ?: return null
        return when (type) {
            MemoryScopeType.PRODUCT -> MemoryScope.Product(name)
            MemoryScopeType.AGENT -> MemoryScope.Agent(name)
            MemoryScopeType.FEATURE -> MemoryScope.Feature(name)
            MemoryScopeType.ORGANIZATION -> MemoryScope.CrossProduct
        }
    }
}