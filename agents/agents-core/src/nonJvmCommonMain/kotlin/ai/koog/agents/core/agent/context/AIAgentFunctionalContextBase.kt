@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline

@Suppress("MissingKDocForPublicAPI")
public actual abstract class AIAgentFunctionalContextBase<Pipeline : AIAgentPipeline> internal actual constructor(
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
    parentContext: AIAgentContext?,
    storeMap: MutableMap<AIAgentStorageKey<*>, Any>
) : AIAgentFunctionalContextBaseCommon<Pipeline>(
    environment = environment,
    agentId = agentId,
    runId = runId,
    agentInput = agentInput,
    config = config,
    llm = llm,
    stateManager = stateManager,
    storage = storage,
    strategyName = strategyName,
    pipeline = pipeline,
    executionInfo = executionInfo,
    storeMap = storeMap,
    parentContext = parentContext
)
