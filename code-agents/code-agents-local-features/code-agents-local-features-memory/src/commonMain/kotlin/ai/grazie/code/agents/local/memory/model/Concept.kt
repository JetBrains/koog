package ai.grazie.code.agents.local.memory.model

import kotlinx.serialization.Serializable

/**
 * Defines how information should be stored and retrieved for a concept in the memory system.
 * This type system helps organize and structure the knowledge representation in the agent's memory.
 */
@Serializable
enum class FactType {
    /**
     * Used when a concept should store exactly one piece of information.
     * Example: Current project's primary programming language or build system type.
     */
    SINGLE,

    /**
     * Used when a concept can have multiple related pieces of information.
     * Example: Project dependencies, coding style rules, or environment variables.
     */
    MULTIPLE
}

/**
 * Represents a distinct piece of knowledge that an agent can remember and recall.
 * Concepts are the fundamental building blocks of the agent's memory system, allowing
 * structured storage and retrieval of information across different contexts and time periods.
 *
 * Use cases:
 * - Storing project configuration details (dependencies, build settings)
 * - Remembering user preferences and previous interactions
 * - Maintaining environment information (OS, tools, SDKs)
 * - Tracking organizational knowledge and practices
 *
 * @property keyword A unique identifier for the concept, used for storage and retrieval
 * @property description A natural language description or question that helps the agent
 *                      understand what information to extract or store for this concept
 * @property factType Determines whether this concept stores single or multiple facts
 */
@Serializable
data class Concept(
    val keyword: String,
    val description: String,
    val factType: FactType
)

/**
 * Represents stored information about a specific concept at a point in time.
 * Facts are the actual data points stored in the memory system, always associated
 * with their originating concept and creation timestamp for temporal reasoning.
 */
@Serializable
sealed interface Fact {
    val concept: Concept
    val timestamp: Long
}

/**
 * Stores a single piece of information about a concept.
 * Used when the concept represents a singular, atomic piece of knowledge
 * that doesn't need to be broken down into multiple components.
 *
 * Example: "The project uses Gradle as its build system"
 */
@Serializable
data class SingleFact(
    override val concept: Concept,
    override val timestamp: Long,
    val value: String
) : Fact

/**
 * Stores multiple related pieces of information about a concept.
 * Used when the concept represents a collection of related facts that
 * should be stored and retrieved together.
 *
 * Example: List of project dependencies, coding style rules, or environment variables
 */
@Serializable
data class MultipleFacts(
    override val concept: Concept,
    override val timestamp: Long,
    val values: List<String>
) : Fact

/**
 * Defines the contextual domain of stored memory facts, determining
 * the visibility and relevance scope of the stored information.
 *
 * This helps organize memories into logical containers and ensures
 * that information is accessed at the appropriate level of context.
 */
@Serializable
enum class MemorySubject {
    /**
     * Information specific to the local machine environment
     * Examples: Installed tools, SDKs, OS configuration, available commands
     */
    MACHINE,

    /**
     * Information specific to the current user
     * Examples: Preferences, settings, authentication tokens
     */
    USER,

    /**
     * Information specific to the current project
     * Examples: Build configuration, dependencies, code style rules
     */
    PROJECT,

    /**
     * Information shared across an organization
     * Examples: Coding standards, shared configurations, team practices
     */
    ORGANIZATION,
}

/**
 * Defines the operational boundary for memory storage and retrieval.
 * Memory scope determines how information is shared and isolated between
 * different components of the system.
 */
@Serializable
sealed interface MemoryScope {
    /**
     * Scope for memories specific to a single agent instance
     * Used when information should be isolated to a particular agent's context
     */
    @Serializable
    data class Agent(val name: String) : MemoryScope

    /**
     * Scope for memories specific to a particular feature
     * Used when information should be shared across agent instances but only within a feature
     */
    @Serializable
    data class Feature(val id: String) : MemoryScope

    /**
     * Scope for memories shared within a specific product
     * Used when information should be available across features within a product
     */
    @Serializable
    data class Product(val name: String) : MemoryScope

    /**
     * Scope for memories shared across all products
     * Used for global information that should be available everywhere
     */
    @Serializable
    object CrossProduct : MemoryScope
}
