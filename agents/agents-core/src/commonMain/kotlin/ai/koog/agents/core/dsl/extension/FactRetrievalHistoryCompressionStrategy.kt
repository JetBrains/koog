package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.utils.buildPromptAsXml
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock

/**
 * Structured representation of a single fact extracted from the chat history.
 *
 * @property fact A concise factual statement about the target concept.
 */
@Serializable
@LLMDescription("A single extracted fact about a concept")
internal data class FactStructure(
    @property:LLMDescription("Concise factual statement about the concept")
    val fact: String,
)

/**
 * Structured representation of multiple facts extracted from the chat history.
 *
 * @property facts All distinct facts found about the target concept.
 */
@Serializable
@LLMDescription("List of extracted facts about a concept")
internal data class FactListStructure(
    @property:LLMDescription("All distinct facts found in the history about the concept")
    val facts: List<FactStructure>,
)

private const val NO_FACTS_EXTRACTED = "No facts extracted"

/**
 * Tag to wrap history.
 */
private const val historyWrapperTag: String = "conversation_to_extract_facts"

/**
 * Single fact prompt.
 */
private fun singleFactPrompt(concept: Concept): String =
    """You are a specialized information extractor for compressing agent conversation histories.

        You will receive a conversation history enclosed in <$historyWrapperTag> tags. Your task is to extract THE SINGLE MOST IMPORTANT fact about "${concept.keyword}" (${concept.description}).
        
        Critical extraction rules:
        1. Focus on THE MOST ESSENTIAL OUTCOME or ESTABLISHED INFORMATION
        2. When you see tool results/observations, extract only the most crucial discovered fact
        3. The fact must be self-contained - assume it will be the only available context later
        4. Choose the fact with the broadest impact on understanding this concept
        
        Output constraints:
        - Exactly one fact
        - Must be a complete, self-contained statement
        
        Respond with a JSON object containing a single "fact" field.
    """.trimIndent()

/**
 * Multiple facts prompt.
 */
private fun multipleFactsPrompt(concept: Concept): String =
    """You are a specialized information extractor for compressing agent conversation histories.
        
        You will receive a conversation history enclosed in <$historyWrapperTag> tags. Your task is to extract ONLY the essential facts about "${concept.keyword}" (${concept.description}).
        
        Critical extraction rules:
        1. Focus on OUTCOMES and ESTABLISHED INFORMATION, not actions taken
        2. When you see tool results/observations, extract only the discovered facts, not the process
        3. Each fact must be self-contained - assume it will be the only available context later
        4. Combine related information into single, comprehensive facts when possible
        
        Output constraints:
        - Facts must be complete statements that stand alone
        - Skip any fact that just describes what was attempted or checked
        
        Respond with a JSON object containing a "facts" array, where each element has a "fact" field.
    """.trimIndent()

/**
 * A history compression strategy for retrieving and incorporating factual knowledge about specific concepts from past
 * session activity or stored memory.
 *
 * This class leverages a list of `Concept` objects, each encapsulating a specific domain or unit of knowledge, to
 * extract and organize related facts within the session history. These facts are structured into messages for
 * inclusion in the session prompt.
 *
 * @param concepts A list of `Concept` objects that define the domains of knowledge for which facts need to be retrieved.
 */
public class FactRetrievalHistoryCompressionStrategy(public val concepts: List<Concept>) : HistoryCompressionStrategy() {
    /**
     * Secondary constructor for `FactRetrievalHistoryCompressionStrategy` that initializes the instance
     * with a variable number of `Concept` objects, converting them into a list.
     *
     * @param concepts A variable number of `Concept` objects to be used for fact retrieval.
     */
    public constructor(vararg concepts: Concept) : this(concepts.toList())

    /**
     * Compresses historical memory and retrieves facts about predefined concepts to construct
     * a prompt containing the relevant information. This method generates fact messages for
     * each concept and appends them to the composed prompt.
     *
     * @param llmSession The local LLM write session used to retrieve facts and manage prompts.
     * @param memoryMessages A list of existing memory-related messages to be included in the prompt.
     */
    override suspend fun compress(
        llmSession: AIAgentLLMWriteSession,
        memoryMessages: List<Message>
    ) {
        // Snapshot original messages BEFORE any extraction (preserves trailing tool calls)
        val originalMessages = llmSession.prompt.messages
        val iterationsCount = originalMessages.count { it is Message.Tool.Result }

        val factsString = concepts
            .associateWith { concept -> llmSession.retrieveFactsFromHistory(concept) }
            .entries
            .filter { (_, fact) ->
                when (fact) {
                    is SingleFact -> fact.value != NO_FACTS_EXTRACTED
                    is MultipleFacts -> fact.values.isNotEmpty()
                }
            }
            .joinToString("\n") { (concept, fact) ->
                buildString {
                    appendLine("## KNOWN FACTS ABOUT `${concept.keyword}` (${concept.description})")
                    when (fact) {
                        is MultipleFacts -> fact.values.forEach { appendLine("- $it") }
                        is SingleFact -> appendLine("- ${fact.value}")
                    }
                }
            }

        val assistantMessage = buildString {
            appendLine("[CONTEXT RESTORATION]")
            appendLine()
            appendLine(
                "The conversation history was compressed due to context limits. " +
                    "Below are the extracted facts about configured concepts."
            )
            appendLine()
            if (factsString.isNotEmpty()) {
                appendLine("**Extracted Facts:**")
                appendLine("<compressed_facts>")
                append(factsString)
                appendLine("</compressed_facts>")
                appendLine()
            }
            appendLine("**Current Status:**")
            append(
                "Approximately $iterationsCount tool interactions occurred before compression. " +
                    "Note: only facts about configured concepts were preserved; active task state, pending steps, and recent conversation flow may not be fully captured."
            )
        }

        val newMessages = Prompt.build(llmSession.prompt.id) {
            assistant(assistantMessage)
        }.messages

        if (factsString.isEmpty()) {
            return
        }

        val compressedMessages = composeMessageHistory(originalMessages, newMessages, memoryMessages)
        llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }
    }
}

/**
 * Extracts facts about a specific concept from the LLM chat history.
 *
 * This function:
 * 1. Snapshots the current session [prompt] and [model] before any mutations.
 * 2. Excludes unresolved trailing tool calls from the XML history (they have no result and are not
 *    discovered information).
 * 3. Rewrites the session prompt as a system instruction (the fact-extraction task) plus a single
 *    user message containing the previous conversation wrapped in XML tags. This prevents the LLM
 *    from continuing in a `tool_call -> tool_result` pattern.
 * 4. Optionally switches to a cheaper [llmModel] for extraction.
 * 5. Asks for a structured response (auto-selecting native/manual mode), with few-shot examples
 *    and a [StructureFixingParser] for robustness.
 * 6. Restores the original prompt and model before returning.
 *
 * Structured-output failures are handled gracefully: if parsing fails, a sentinel value is returned
 * ([NO_FACTS_EXTRACTED] for single facts, empty list for multiple facts). The caller ([compress])
 * filters out these sentinel values before rendering.
 *
 * @param concept The concept to extract facts about.
 * @param llmModel Optional model to use for extraction (defaults to the session's current model).
 * @param clock Clock used to timestamp the produced [Fact].
 * @return A [Fact] (either [SingleFact] or [MultipleFacts]) containing the extracted information.
 */
@OptIn(InternalAgentsApi::class)
internal suspend fun AIAgentLLMWriteSession.retrieveFactsFromHistory(
    concept: Concept,
    llmModel: LLModel? = null,
    clock: Clock = Clock.System,
): Fact {
    // Snapshot the original prompt and model BEFORE any mutations
    val initialPrompt = this.prompt
    val initialModel = this.model

    val systemInstruction = when (concept.factType) {
        FactType.SINGLE -> singleFactPrompt(concept)
        FactType.MULTIPLE -> multipleFactsPrompt(concept)
    }

    // Combine all history into one message with XML tags, excluding unresolved trailing
    // tool calls (they have no result and are not discovered information).
    val messagesForExtraction = initialPrompt.messages.dropLastWhile { it is Message.Tool.Call }
    this.prompt = buildPromptAsXml(messagesForExtraction, systemInstruction, initialPrompt.id, historyWrapperTag)
    if (llmModel != null) {
        this.model = llmModel
    }

    val fixingParser = StructureFixingParser(
        model = llmModel ?: this.model,
        retries = 3,
    )

    val timestamp = clock.now().toEpochMilliseconds()

    val facts: Fact = try {
        when (concept.factType) {
            FactType.SINGLE -> {
                val response = requestLLMStructured<FactStructure>(
                    examples = listOf(
                        FactStructure(fact = "Example fact about the concept")
                    ),
                    fixingParser = fixingParser,
                )

                SingleFact(
                    concept = concept,
                    value = response.getOrNull()?.data?.fact ?: NO_FACTS_EXTRACTED,
                    timestamp = timestamp,
                )
            }

            FactType.MULTIPLE -> {
                val response = requestLLMStructured<FactListStructure>(
                    examples = listOf(
                        FactListStructure(
                            facts = listOf(
                                FactStructure(fact = "Example fact A"),
                                FactStructure(fact = "Example fact B"),
                            )
                        )
                    ),
                    fixingParser = fixingParser,
                )
                val factsList = response.getOrNull()?.data?.facts ?: emptyList()

                MultipleFacts(
                    concept = concept,
                    values = factsList.map { it.fact },
                    timestamp = timestamp,
                )
            }
        }
    } finally {
        // Restore the original prompt and model (including any trailing tool calls)
        this.prompt = initialPrompt
        this.model = initialModel
    }

    return facts
}

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
