@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlin.time.Clock

public actual abstract class AIAgentPipeline internal constructor(
    agentConfig: AIAgentConfig,
    clock: Clock,
    internal val pipelineDelegate: AIAgentPipelineImpl
) : AIAgentPipelineAPI by pipelineDelegate {
    public actual constructor(agentConfig: AIAgentConfig, clock: Clock) : this(
        agentConfig,
        clock,
        AIAgentPipelineImpl(agentConfig, clock)
    )

    @InternalAgentsApi
    public actual override suspend fun prepareFeatures() {
        pipelineDelegate.prepareFeatures(this)
    }
}
