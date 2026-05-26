@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.utils.time.KoogClock

public actual abstract class AIAgentPipeline actual constructor(
    agentConfig: AIAgentConfig,
    clock: KoogClock
) : AIAgentPipelineAPI by AIAgentPipelineImpl(agentConfig, clock)
