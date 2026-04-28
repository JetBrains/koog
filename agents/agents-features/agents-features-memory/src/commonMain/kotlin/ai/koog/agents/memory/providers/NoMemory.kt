package ai.koog.agents.memory.providers

import ai.koog.agents.core.dsl.extension.Concept
import ai.koog.agents.core.dsl.extension.Fact
import ai.koog.agents.core.dsl.extension.MemoryScope
import ai.koog.agents.core.dsl.extension.MemorySubject
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Implementation of [AgentMemoryProvider] that does nothing and logs that memory feature is not enabled
 */
public object NoMemory : AgentMemoryProvider {
    private val logger = KotlinLogging.logger { }

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        logger.info {
            "Memory feature is not enabled in the agent. Skipping saving fact for concept '${fact.concept.keyword}'"
        }
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        logger.info {
            "Memory feature is not enabled in the agent. No facts will be loaded for concept '${concept.keyword}'"
        }
        return emptyList()
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        logger.info { "Memory feature is not enabled in the agent. No facts will be loaded" }
        return emptyList()
    }

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        logger.info {
            "Memory feature is not enabled in the agent. No facts will be loaded for question: '$description'"
        }
        return emptyList()
    }
}
