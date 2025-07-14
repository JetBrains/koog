package ai.koog.agents.memory.model

import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Defines how information should be stored and retrieved for a concept in the memory system.
 * This type system helps organize and structure the knowledge representation in the agent's memory.
 */
@Serializable
public enum class FactType {
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
public data class Concept(
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
public sealed interface Fact {
    /**
     * The `concept` property represents the distinct piece of knowledge associated with this fact.
     *
     * Each fact is linked to a specific concept, which acts as the central reference point for
     * storing, retrieving, and managing structured information. This allows for organizing
     * and maintaining relationships between individual data points in the memory system.
     */
    public val concept: Concept
    /**
     * The timestamp indicating when the fact was created or stored, expressed as the number of
     * milliseconds elapsed since the Unix epoch (January 1, 1970, 00:00:00 UTC).
     *
     * This property is crucial for enabling temporal reasoning within the memory system,
     * allowing the system to associate facts with specific moments in time. It is used for:
     * - Ordering facts chronologically
     * - Supporting time-based queries and operations
     * - Tracking data validity or freshness based on creation time
     *
     * This value is typically generated using a platform-specific implementation of
     * the TimeProvider interface to ensure precision and consistency across different platforms.
     */
    public val timestamp: Long
}

/**
 * Stores a single piece of information about a concept.
 * Used when the concept represents a singular, atomic piece of knowledge
 * that doesn't need to be broken down into multiple components.
 *
 * Example: "The project uses Gradle as its build system"
 */
@Serializable
public data class SingleFact(
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
public data class MultipleFacts(
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
@Serializable(with = MemorySubject.Serializer::class)
public abstract class MemorySubject() {
    /**
     * Name of the memory subject (ex: "user", or "project")
     * */
    public abstract val name: String

    /**
     * Description of what type of information is related to the memory subject, that will be sent to the LLM.
     *
     * Ex: for the "user" memory subject it could be:
     *      "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
     * */
    public abstract val promptDescription: String

    /**
     * Indicates how important this memory subject is compared to others.
     * Higher numbers mean lower importance.
     *
     * Information from higher-priority subjects
     * takes precedence over lower-priority ones.
     *
     * For example, if a higher-priority memory subject states that the user prefers red,
     * and a lower-priority one says white, red will be chosen as the preferred color.
     */
    public abstract val priorityLevel: Int

    /**
     * Companion object
     */
    @InternalAgentsApi
    public companion object {
        /**
         * A mutable collection of all registered subjects.
         */
        public val registeredSubjects: MutableList<MemorySubject> = mutableListOf()
    }

    init {
        @OptIn(InternalAgentsApi::class)
        registeredSubjects.add(this)
    }

    internal object Serializer : KSerializer<MemorySubject> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MemorySubject", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: MemorySubject) {
            encoder.encodeString(value.name)
        }

        @OptIn(InternalAgentsApi::class)
        override fun deserialize(decoder: Decoder): MemorySubject {
            val name = decoder.decodeString()
            return registeredSubjects.find { it.name == name }
                ?: throw IllegalArgumentException("No MemorySubject found with name: $name")
        }
    }


    /**
     * Represents a memory subject with the broadest scope, encompassing all important
     * information and meaningful facts. The purpose of this object is to serve as a
     * global context for information that doesn't fit within narrower, more specific
     * memory subjects.
     *
     * Key characteristics:
     * - Name: Identifies the subject as "everything".
     * - Prompt Description: Provides a description indicating that it contains
     *   all significant information and meaningful facts.
     * - Priority Level: Assigned the lowest priority level, indicating that
     *   information from this subject is considered only when higher-priority
     *   subjects do not provide the needed context.
     *
     * This memory subject can be useful for scenarios where a comprehensive
     * or fallback information source is required.
     */
    @Serializable
    public data object Everything : MemorySubject() {
        override val name: String = "everything"
        override val promptDescription: String = "All important information and meaningful facts"

        // The highest number means the lowest priority
        override val priorityLevel: Int = Int.MAX_VALUE
    }
}

/**
 * Defines the operational boundary for memory storage and retrieval.
 * Memory scope determines how information is shared and isolated between
 * different components of the system.
 */
public sealed interface MemoryScope {
    /**
     * Scope for memories specific to a single agent instance
     * Used when information should be isolated to a particular agent's context
     */
    @Serializable
    public data class Agent(val name: String) : MemoryScope

    /**
     * Scope for memories specific to a particular feature
     * Used when information should be shared across agent instances but only within a feature
     */
    @Serializable
    public data class Feature(val id: String) : MemoryScope

    /**
     * Scope for memories shared within a specific product
     * Used when information should be available across features within a product
     */
    @Serializable
    public data class Product(val name: String) : MemoryScope

    /**
     * Scope for memories shared across all products
     * Used for global information that should be available everywhere
     */
    @Serializable
    public object CrossProduct : MemoryScope
}
