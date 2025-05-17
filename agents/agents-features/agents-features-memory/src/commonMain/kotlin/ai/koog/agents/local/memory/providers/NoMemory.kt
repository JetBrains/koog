package ai.koog.agents.local.memory.providers

import ai.koog.agents.local.memory.model.Concept
import ai.koog.agents.local.memory.model.Fact
import ai.koog.agents.local.memory.model.MemoryScope
import ai.koog.agents.local.memory.model.MemorySubject
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger

/**
 * Implementation of [AgentMemoryProvider] that does nothing and logs that memory feature is not enabled
 */
public object NoMemory : AgentMemoryProvider {
    private val logger: MPPLogger = LoggerFactory.create("ai.koog.agents.local.memory.feature.NoMemory")


    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        logger.info { "Memory feature is not enabled in the agent. Skipping saving fact for concept '${fact.concept.keyword}'" }
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        logger.info { "Memory feature is not enabled in the agent. No facts will be loaded for concept '${concept.keyword}'" }
        return emptyList()
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        logger.info { "Memory feature is not enabled in the agent. No facts will be loaded" }
        return emptyList()
    }

    override suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        logger.info { "Memory feature is not enabled in the agent. No facts will be loaded for question: '$description'" }
        return emptyList()
    }
}
