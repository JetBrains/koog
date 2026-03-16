@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import kotlin.time.Clock

public actual abstract class AIAgentPipeline actual constructor(
    config: AIAgentConfig,
    clock: Clock
) : AIAgentPipelineCommon(config, clock)
