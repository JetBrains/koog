@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline

/**
 * Base class for functional/planner context implementations across all targets.
 *
 * It inherits all shared behavior from [AIAgentFunctionalContextBaseCommon].
 */
public expect abstract class AIAgentFunctionalContextBase<Pipeline : AIAgentPipeline> internal constructor(
    environment: AIAgentEnvironment,
    agentId: String,
    runId: String,
    agentInput: Any?,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    strategyName: String,
    pipeline: Pipeline,
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentContext? = null,
    storeMap: MutableMap<AIAgentStorageKey<*>, Any> = mutableMapOf()
) : AIAgentFunctionalContextBaseCommon<Pipeline>
