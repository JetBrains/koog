package ai.koog.agents.memory.feature.history

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.memory.feature.retrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

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
public class RetrieveFactsFromHistory(public val concepts: List<Concept>) : HistoryCompressionStrategy() {
    /**
     * Secondary constructor for `RetrieveFactsFromHistory` that initializes the instance
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
     * @param preserveMemory A flag indicating whether to preserve existing memory-related messages in the session.
     * @param memoryMessages A list of existing memory-related messages to be included in the prompt.
     */
    override suspend fun compress(
        llmSession: AIAgentLLMWriteSession,
        preserveMemory: Boolean,
        memoryMessages: List<Message>
    ) {
        val iterationsCount = llmSession.prompt.messages.count { it is Message.Tool.Result }

        val factsString = concepts.associateWith { concept -> llmSession.retrieveFactsFromHistory(concept) }
            .map { (concept, fact) ->
                buildString {
                    appendLine("## KNOWN FACTS ABOUT `${concept.keyword}` (${concept.description})")
                    when (fact) {
                        is MultipleFacts -> fact.values.forEach { appendLine("- $it") }
                        is SingleFact -> appendLine("- ${fact.value}")
                    }
                    appendLine()
                }
            }.joinToString("\n")

        val assistantMessage =
            """[CONTEXT RESTORATION INITIATED]

            I was in the middle of working on the task when I needed to compress the conversation history due to context limits. Here's my understanding of where we are:
            
            **Compressed Working Memory:**
            <compressed_facts>
            {${factsString.trimIndent()}
            </compressed_facts>
            
            **Current Status:**
            I've been actively working on this task through approximately $iterationsCount tool interactions. 
            The above summary represents the key findings, attempted approaches, and intermediate results from my work so far.
            
            I'm ready to continue from this point. Let me quickly orient myself to the current state and proceed with the next logical step based on my previous findings.""".trimIndent()

        val userMessage =
            """Yes, that's correct. Your memory compression accurately captures your progress. Please continue your work from where you left off. 
            For context, you still have access to all the same tools, and the task requirements remain unchanged. Focus on building upon what you've already discovered rather than re-exploring completed paths.
            Continue with your analysis and implementation.""".trimIndent()

        val oldMessages = llmSession.prompt.messages
        val lastResult = oldMessages.filterIsInstance<Message.Tool.Result>().lastOrNull()
        val lastToolCallMessages = lastResult?.let { result -> oldMessages.filterIsInstance<Message.Tool.Call>().find { it.id == result.id }?.let { listOf(it, result) } } ?: emptyList()

        val newMessages = Prompt.build(llmSession.prompt.id) {
            assistant(assistantMessage)
            user(userMessage)
        }.messages

        composePromptWithRequiredMessages(llmSession, newMessages, preserveMemory, memoryMessages)
    }
}
