package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.utils.buildPromptAsXml
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable
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
 * A history compression strategy for retrieving and incorporating factual knowledge about specific concepts from past
 * session activity or stored memory.
 *
 * This class leverages a list of `Concept` objects, each encapsulating a specific domain or unit of knowledge, to
 * extract and organize related facts within the session history. These facts are structured into messages for
 * inclusion in the session prompt.
 *
 * @param concepts A list of `Concept` objects that define the domains of knowledge for which facts need to be retrieved.
 */
public class ConceptBasedHistoryCompressionStrategy(public val concepts: List<Concept>) : HistoryCompressionStrategy() {
    /**
     * Secondary constructor for `ConceptBasedHistoryCompressionStrategy` that initializes the instance
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
            // No useful facts were extracted — fall back to WholeHistory compression
            // which preserves a TL;DR of the entire conversation instead.
            WholeHistory.compress(llmSession, memoryMessages)
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
public suspend fun AIAgentLLMWriteSession.retrieveFactsFromHistory(
    concept: Concept,
    llmModel: LLModel? = null,
    clock: Clock = Clock.System,
): Fact {
    // Snapshot the original prompt and model BEFORE any mutations
    val initialPrompt = this.prompt
    val initialModel = this.model

    val systemInstruction = when (concept.factType) {
        FactType.SINGLE -> ConceptBasedPrompts.singleFactPrompt(concept)
        FactType.MULTIPLE -> ConceptBasedPrompts.multipleFactsPrompt(concept)
    }

    // Combine all history into one message with XML tags, excluding unresolved trailing
    // tool calls (they have no result and are not discovered information).
    val messagesForExtraction = initialPrompt.messages.dropLastWhile { it is Message.Tool.Call }
    this.prompt = buildPromptAsXml(messagesForExtraction, systemInstruction, initialPrompt.id, ConceptBasedPrompts.historyWrapperTag)
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
