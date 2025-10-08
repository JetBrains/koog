package ai.koog.agents.memory.feature.nodes

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.withMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.DefaultTimeProvider
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.prompts.MemoryPrompts
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ==========
// Memory nodes
// ==========

/**
 * Node that loads facts from memory for a given concept
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concept A concept to load facts for
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concept: Concept,
    subject: MemorySubject,
    scope: MemoryScopeType = MemoryScopeType.AGENT
): AIAgentNodeDelegate<T, T> = nodeLoadFromMemory(name, listOf(concept), listOf(subject), listOf(scope))

/**
 * Node that loads facts from memory for a given concept
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concepts A list of concepts to load facts for
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concepts: List<Concept>,
    subject: MemorySubject,
    scope: MemoryScopeType = MemoryScopeType.AGENT
): AIAgentNodeDelegate<T, T> = nodeLoadFromMemory(name, concepts, listOf(subject), listOf(scope))

/**
 * Node that loads facts from memory for a given concept
 *
 * @param concepts A list of concepts to load facts for
 * @param scopes List of memory scopes (Agent, Feature, etc.). By default all scopes would be chosen
 * @param subjects List of subjects (user, project, organization, etc.) to look for. By default all subjects would be chosen
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concepts: List<Concept>,
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    scopes: List<MemoryScopeType> = MemoryScopeType.entries
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    withMemory {
        concepts.forEach { concept ->
            loadFactsToAgent(llm, concept, scopes, subjects)
        }
    }

    input
}

/**
 * Node that loads all facts about the subject from memory for a given concept
 *
 * @param scopes List of memory scopes (Agent, Feature, etc.). By default only Agent scope would be chosen
 * @param subjects List of subjects (user, project, organization, etc.) to look for.
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadAllFactsFromMemory(
    name: String? = null,
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    scopes: List<MemoryScopeType> = MemoryScopeType.entries
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    withMemory {
        loadAllFactsToAgent(llm, scopes, subjects)
    }

    input
}

/**
 * Node that saves a fact to memory
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concepts List of concepts to save in memory
 * @param retrievalModel LLM that will be used for fact retrieval from the history (by default, the same model as the current one will be used)
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemory(
    name: String? = null,
    subject: MemorySubject,
    scope: MemoryScopeType,
    concepts: List<Concept>,
    retrievalModel: LLModel? = null
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    withMemory {
        concepts.forEach { concept ->
            saveFactsFromHistory(
                llm = llm,
                concept = concept,
                subject = subject,
                scope = scopesProfile.getScope(scope) ?: return@forEach,
                retrievalModel = retrievalModel
            )
        }
    }

    input
}

/**
 * Node that saves a fact to memory
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concept The concept to save in memory
 * @param retrievalModel LLM that will be used for fact retrieval from the history (by default, the same model as the current one will be used)
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemory(
    name: String? = null,
    concept: Concept,
    subject: MemorySubject,
    scope: MemoryScopeType,
    retrievalModel: LLModel? = null
): AIAgentNodeDelegate<T, T> = nodeSaveToMemory(name, subject, scope, listOf(concept), retrievalModel)

/**
 * Node that automatically detects and extracts facts from the chat history and saves them to memory.
 * It uses LLM to identify concepts about user, organization, project, etc.
 *
 * @param subjects The subject scope of the memory (USER, PROJECT, etc.)
 * @param scopes List of memory scopes (Agent, Feature, etc.). By default only Agent scope would be chosen
 * @param subjects List of subjects (user, project, organization, etc.) to look for.
 * By default, all subjects will be included and looked for.
 * @param retrievalModel LLM that will be used for fact retrieval from the history (by default, the same model as the current one will be used)
 */
@OptIn(InternalAgentsApi::class)
@AIAgentBuilderDslMarker
public inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemoryAutoDetectFacts(
    name: String? = null,
    scopes: List<MemoryScopeType> = listOf(MemoryScopeType.AGENT),
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    retrievalModel: LLModel? = null
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    llm.writeSession {
        val initialModel = model
        val initialPrompt = prompt.copy()
        if (retrievalModel != null) {
            model = retrievalModel
        }
        updatePrompt {
            val prompt = MemoryPrompts.autoDetectFacts(subjects)
            user(prompt)
        }

        val response = requestLLMWithoutTools()

        withMemory {
            scopes.mapNotNull(scopesProfile::getScope).forEach { scope ->
                val facts = parseFactsFromResponse(response.content)
                facts.forEach { (subject, fact) ->
                    agentMemory.save(fact, subject, scope)
                }
            }
        }

        rewritePrompt { initialPrompt } // Revert the prompt to the original one
        if (retrievalModel != null) {
            model = initialModel
        }
    }

    input
}

@Serializable
internal data class SubjectWithFact(
    val subject: MemorySubject,
    val keyword: String,
    val description: String,
    val value: String
)

private fun getCurrentTimestamp(): Long = DefaultTimeProvider.getCurrentTimestamp()

/**
 * Parsing facts from response.
 */
@InternalAgentsApi
public fun parseFactsFromResponse(content: String): List<Pair<MemorySubject, Fact>> {
    val parsedFacts = Json.decodeFromString<List<SubjectWithFact>>(content)
    val groupedFacts = parsedFacts.groupBy { it.subject to it.keyword }

    return groupedFacts.map { (subjectWithKeyword, facts) ->
        when (facts.size) {
            1 -> {
                val singleFact = facts.single()
                subjectWithKeyword.first to SingleFact(
                    concept = Concept(
                        keyword = singleFact.keyword,
                        description = singleFact.description,
                        factType = FactType.SINGLE
                    ),
                    value = singleFact.value,
                    timestamp = getCurrentTimestamp()
                )
            }

            else -> {
                subjectWithKeyword.first to MultipleFacts(
                    concept = Concept(
                        keyword = subjectWithKeyword.second,
                        description = facts.first().description,
                        factType = FactType.MULTIPLE
                    ),
                    values = facts.map { it.value },
                    timestamp = getCurrentTimestamp()
                )
            }
        }
    }
}
