@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import kotlinx.datetime.Clock

public actual abstract class AIAgentPipeline actual constructor(
    agentConfig: AIAgentConfig,
    clock: Clock
) : AIAgentPipelineAPI by AIAgentPipelineImpl(agentConfig, clock)
