package ai.grazie.code.agents.local.memory.feature

import ai.grazie.code.agents.core.agent.entity.createStorageKey
import ai.grazie.code.agents.core.agent.entity.stage.AgentLLMContext
import ai.grazie.code.agents.core.agent.entity.stage.AgentLLMWriteSession
import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext
import ai.grazie.code.agents.core.feature.AgentPipeline
import ai.grazie.code.agents.core.feature.AgentFeature
import ai.grazie.code.agents.core.feature.config.FeatureConfig
import ai.grazie.code.agents.local.memory.config.MemoryScopeType
import ai.grazie.code.agents.local.memory.config.MemoryScopesProfile
import ai.grazie.code.agents.local.memory.model.*
import ai.grazie.code.agents.local.memory.model.DefaultTimeProvider.getCurrentTimestamp
import ai.grazie.code.agents.local.memory.prompts.MemoryPrompts
import ai.grazie.code.agents.local.memory.providers.AgentMemoryProvider
import ai.grazie.code.agents.local.memory.providers.NoMemory
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger


/**
 * Memory implementation for LocalAIAgent that combines LLM context with memory feature
 */
class MemoryFeature(
    internal val agentMemory: AgentMemoryProvider,
    internal val llm: AgentLLMContext,
    internal val scopesProfile: MemoryScopesProfile
) {
    private val logger: MPPLogger = LoggerFactory.create("ai.grazie.code.agents.local.memory.LocalAIAgentMemory")

    private fun getCurrentTimestamp(): Long = DefaultTimeProvider.getCurrentTimestamp()

    class Config : FeatureConfig() {
        var memoryProvider: AgentMemoryProvider = NoMemory
        internal var scopesProfile: MemoryScopesProfile = MemoryScopesProfile()

        var agentName: String
            get() = scopesProfile.names[MemoryScopeType.AGENT] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.AGENT] = value
            }

        var featureName: String
            get() = scopesProfile.names[MemoryScopeType.FEATURE] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.FEATURE] = value
            }

        var organizationName: String
            get() = scopesProfile.names[MemoryScopeType.ORGANIZATION] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.ORGANIZATION] = value
            }

        var productName: String
            get() = scopesProfile.names[MemoryScopeType.PRODUCT] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.PRODUCT] = value
            }

        private companion object {
            const val UNKNOWN_NAME = "unknown"
        }
    }

    companion object Feature : AgentFeature<Config, MemoryFeature> {
        override val key = createStorageKey<MemoryFeature>("local-ai-agent-memory-feature")

        override fun createInitialConfig(): Config = Config()

        override fun install(config: Config, pipeline: AgentPipeline) {
            pipeline.interceptContextStageFeature(this) { stageContext ->
                config.agentName = stageContext.strategyId

                MemoryFeature(config.memoryProvider, stageContext.llm, config.scopesProfile)
            }
        }
    }

    /**
     * Save facts from LLM chat history based on the provided concept
     *
     * @param concept The concept to extract facts about
     * @param subject The subject scope for the facts
     * @param scope The memory scope for the facts
     * @param preserveQuestionsInLLMChat If true, keeps the fact extraction messages in the chat history
     */
    suspend fun saveFactsFromHistory(
        concept: Concept,
        subject: MemorySubject,
        scope: MemoryScope,
        preserveQuestionsInLLMChat: Boolean = false
    ) {
        llm.writeSession {
            val facts = retrieveFactsFromHistory(concept, preserveQuestionsInLLMChat)

            // Save facts to memory
            agentMemory.save(facts, subject, scope)
            logger.info { "Saved fact for concept '${concept.keyword}' in scope $scope: $facts" }
        }
    }

    /**
     * Load facts about a concept from memory and add them to LLM chat history
     *
     * @param concept The concept to load facts about
     * @param scopes List of memory scopes (Agent, Feature, etc.). By default all scopes would be chosen
     * @param subjects List of subjects (user, project, organization, etc.) to look for. By default all subjects would be chosen
     */
    suspend fun loadFactsToAgent(
        concept: Concept,
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.entries,
    ) = loadFactsToAgentImpl(scopes, subjects) { subject, scope ->
        agentMemory.load(concept, subject, scope)
    }

    /**
     * Load all available facts (with all concepts) about from memory and add them to LLM chat history
     *
     * @param scopes List of memory scopes (Agent, Feature, etc.). By default all scopes would be chosen
     * @param subjects List of subjects (user, project, organization, etc.) to look for. By default all subjects would be chosen
     */
    suspend fun loadAllFactsToAgent(
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.entries,
    ) = loadFactsToAgentImpl(scopes, subjects, agentMemory::loadAll)

    private suspend fun loadFactsToAgentImpl(
        scopes: List<MemoryScopeType>,
        subjects: List<MemorySubject>,
        loadFacts: suspend (subject: MemorySubject, scope: MemoryScope) -> List<Fact>
    ) {
        // Load facts for all matching scopes
        val facts = mutableListOf<Fact>()

        // Sort subjects by specificity (MACHINE -> USER -> PROJECT -> ORGANIZATION)
        val sortedSubjects = subjects.sortedByDescending { it.ordinal }

        // Track single facts by concept keyword and subject specificity
        val singleFactsByKeyword = mutableMapOf<String, Pair<MemorySubject, SingleFact>>()

        // Get all possible scopes based on the profile
        logger.info { "Using scopes: $scopes" }

        for (scope in scopes) {
            for (subject in sortedSubjects) {
                logger.info { "Loading facts for scope: $scope, subject: $subject" }
                val subjectFacts = loadFacts(subject, scopesProfile.getScope(scope) ?: continue)
                logger.info { "Loaded ${subjectFacts.size} facts" }

                for (fact in subjectFacts) {
                    when (fact) {
                        is SingleFact -> {
                            val existingFact = singleFactsByKeyword[fact.concept.keyword]
                            logger.info { "Processing single fact: ${fact.value}, existing: ${existingFact?.second?.value}" }
                            // Replace fact only if current subject is more specific (lower ordinal)
                            if (existingFact == null || subject.ordinal < existingFact.first.ordinal) {
                                logger.info { "Using fact from subject $subject (ordinal: ${subject.ordinal})" }
                                singleFactsByKeyword[fact.concept.keyword] = subject to fact
                            }
                        }

                        is MultipleFacts -> {
                            logger.info { "Adding multiple facts: ${fact.values.joinToString()}" }
                            facts.add(fact)
                        }
                    }
                }
            }
        }

        logger.info { "Single facts by keyword: ${singleFactsByKeyword.mapValues { it.value.second.value }}" }
        // Add the most specific single facts to the result
        facts.addAll(singleFactsByKeyword.values.map { it.second })

        val factsByConcept = facts.groupBy { it.concept }

        logger.info { "Found ${facts.size} facts for ${factsByConcept.size} concepts" }

        // Add facts to LLM chat history
        if (factsByConcept.isNotEmpty()) {
            factsByConcept.forEach { (concept, facts) ->
                llm.writeSession {
                    val message = buildString {
                        appendLine("Here are the relevant facts from memory about [${concept.keyword}](${concept.description.shortened()}):")
                        facts.forEach { fact ->
                            when (fact) {
                                is SingleFact -> appendLine(
                                    "- [${fact.concept.keyword}]: ${fact.value}"
                                )

                                is MultipleFacts -> {
                                    appendLine("- [${fact.concept.keyword}]:")
                                    fact.values.forEach { value ->
                                        appendLine("  - $value")
                                    }
                                }
                            }
                        }
                    }
                    logger.info { "Built message for LLM: $message" }
                    logger.info { "Updating prompt with message" }
                    updatePrompt { user(message) }
                    logger.info { "Prompt updated" }
                }
            }
            logger.info { "Loaded ${facts.size} facts into LLM memory" }
        }
    }
}

internal suspend fun AgentLLMWriteSession.retrieveFactsFromHistory(
    concept: Concept,
    preserveQuestionsInLLMChat: Boolean
): Fact {
    // Add a message asking to retrieve facts about the concept
    val prompt = when (concept.factType) {
        FactType.SINGLE -> MemoryPrompts.singleFactPrompt(concept)
        FactType.MULTIPLE -> MemoryPrompts.multipleFactsPrompt(concept)
    }

    updatePrompt { user(prompt) }
    val response = requestLLMWithoutTools()

    val timestamp = getCurrentTimestamp()
    // Parse the response into facts
    val facts = when (concept.factType) {
        FactType.SINGLE -> {
            SingleFact(concept = concept, value = response.content.trim(), timestamp = timestamp)
        }

        FactType.MULTIPLE -> {
            val factsList = response.content
                .split("\n")
                .filter { it.isNotBlank() }
                .map { it.trim().removePrefix("-").trim() }
            MultipleFacts(concept = concept, values = factsList, timestamp = timestamp)
        }
    }

    // Remove the fact extraction messages if not preserving them
    if (!preserveQuestionsInLLMChat) {
        rewritePrompt { oldPrompt ->
            oldPrompt.withUpdatedMessages { dropLast(2) }
        }
    }
    return facts
}

private fun String.shortened() = lines().first().take(100) + "..."

fun AgentStageContext.memory(): MemoryFeature = feature(MemoryFeature.Feature)!!

suspend fun <T> AgentStageContext.withMemory(action: suspend MemoryFeature.() -> T) = memory().action()