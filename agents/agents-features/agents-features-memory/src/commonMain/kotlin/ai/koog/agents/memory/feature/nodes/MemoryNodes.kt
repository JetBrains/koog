package ai.koog.agents.memory.feature.nodes

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.withMemory
import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.prompts.MemoryPrompts
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concept: Concept,
    subject: MemorySubject,
    scope: MemoryScopeType = MemoryScopeType.AGENT
): AIAgentNodeDelegateBase<T, T> = nodeLoadFromMemory(name, listOf(concept), listOf(subject), listOf(scope))

/**
 * Node that loads facts from memory for a given concept
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concepts A list of concepts to load facts for
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concepts: List<Concept>,
    subject: MemorySubject,
    scope: MemoryScopeType = MemoryScopeType.AGENT
): AIAgentNodeDelegateBase<T, T> = nodeLoadFromMemory(name, concepts, listOf(subject), listOf(scope))


/**
 * Node that loads facts from memory for a given concept
 *
 * @param concepts A list of concepts to load facts for
 * @param scopes List of memory scopes (Agent, Feature, etc.). By default all scopes would be chosen
 * @param subjects List of subjects (user, project, organization, etc.) to look for. By default all subjects would be chosen
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadFromMemory(
    name: String? = null,
    concepts: List<Concept>,
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    scopes: List<MemoryScopeType> = MemoryScopeType.entries
): AIAgentNodeDelegateBase<T, T> = node(name) { input ->
    withMemory {
        concepts.forEach { concept ->
            loadFactsToAgent(concept, scopes, subjects)
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLoadAllFactsFromMemory(
    name: String? = null,
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    scopes: List<MemoryScopeType> = MemoryScopeType.entries
): AIAgentNodeDelegateBase<T, T> = node(name) { input ->
    withMemory {
        loadAllFactsToAgent(scopes, subjects)
    }

    input
}

/**
 * Node that saves a fact to memory
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scope The scope of the memory (Agent, Feature, etc.)
 * @param concepts List of concepts to save in memory
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemory(
    name: String? = null,
    subject: MemorySubject,
    scope: MemoryScopeType,
    concepts: List<Concept>,
): AIAgentNodeDelegateBase<T, T> = node(name) { input ->
    withMemory {
        concepts.forEach { concept ->
            saveFactsFromHistory(
                concept = concept,
                subject = subject,
                scope = scopesProfile.getScope(scope) ?: return@forEach,
                preserveQuestionsInLLMChat = true
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
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemory(
    name: String? = null,
    concept: Concept,
    subject: MemorySubject,
    scope: MemoryScopeType,
): AIAgentNodeDelegateBase<T, T> = nodeSaveToMemory(name, subject, scope, listOf(concept))

/**
 * Node that automatically detects and extracts facts from the chat history and saves them to memory.
 * It uses LLM to identify concepts about user, organization, project, etc.
 *
 * @param subject The subject scope of the memory (USER, PROJECT, etc.)
 * @param scopes List of memory scopes (Agent, Feature, etc.). By default only Agent scope would be chosen
 * @param subjects List of subjects (user, project, organization, etc.) to look for.
 * By default, all subjects will be included and looked for.
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeSaveToMemoryAutoDetectFacts(
    name: String? = null,
    scopes: List<MemoryScopeType> = listOf(MemoryScopeType.AGENT),
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects
): AIAgentNodeDelegateBase<T, T> = node(name) { input ->
    llm.writeSession {
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

private fun parseFactsFromResponse(content: String): List<Pair<MemorySubject, Fact>> {
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
