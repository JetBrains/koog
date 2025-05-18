package ai.koog.agents.local.memory.feature.history

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.local.memory.feature.retrieveFactsFromHistory
import ai.koog.agents.local.memory.model.Concept
import ai.koog.agents.local.memory.model.MultipleFacts
import ai.koog.agents.local.memory.model.SingleFact
import ai.koog.prompt.markdown.markdown
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
        val factMessages = concepts.associateWith { concept ->
            llmSession.retrieveFactsFromHistory(concept, preserveQuestionsInLLMChat = false)
        }.map { (concept, fact) ->
            Message.Assistant(markdown {
                h2("KNOWN FACTS ABOUT `${concept.keyword}`")
                br()
                blockquote("Note: `${concept.keyword}` can be described as:\n${concept.description}")
                br()
                bulleted {
                    when (fact) {
                        is MultipleFacts -> fact.values.forEach(::item)
                        is SingleFact -> item(fact.value)
                    }
                }
            })
        }

        composePromptWithRequiredMessages(llmSession, factMessages, preserveMemory, memoryMessages)
    }
}
