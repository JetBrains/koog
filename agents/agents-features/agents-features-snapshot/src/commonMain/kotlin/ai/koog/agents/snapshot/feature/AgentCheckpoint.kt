@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.snapshot.providers.AgentCheckpointProvider
import ai.koog.prompt.message.Message
import kotlin.uuid.ExperimentalUuidApi

public class AgentCheckpoint(private val agentCheckpointProvider: AgentCheckpointProvider) {
    public companion object Feature : AIAgentFeature<AgentCheckpointFeatureConfig, AgentCheckpoint> {
        override val key: AIAgentStorageKey<AgentCheckpoint>
            get() = AIAgentStorageKey("agents-features-snapshot")

        override fun createInitialConfig(): AgentCheckpointFeatureConfig = AgentCheckpointFeatureConfig()

        @OptIn(ExperimentalUuidApi::class)
        override fun install(
            config: AgentCheckpointFeatureConfig,
            pipeline: AIAgentPipeline
        ) {
            val featureImpl = AgentCheckpoint(config.agentCheckpointProvider)
            val interceptContext = InterceptContext(this, featureImpl)

            pipeline.interceptContextAgentFeature(this) {
                featureImpl
            }

//            pipeline.interceptAfterNode(interceptContext) { node: AIAgentNodeBase<*, *>,
//                                                            context: AIAgentContextBase,
//                                                            input: Any?,
//                                                            output: Any? ->
//                val history = context.getHistory()
//                val snapshot = AgentCheckpointData(
//                    agentId = context.sessionUuid.toString(),
//                    snapshotId = "${AgentCheckpointData.SNAPSHOT_ID_PREFIX}${node.name}",
//                    lastExecutedNodeId = node.name,
//                    lastOutput = output,
//                    messages = history
//                )
//
//                config.agentCheckpointProvider.saveCheckpoint(snapshot)
//                println("Snapshot feature intercepting after node: ${node.name}")
//            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    public suspend fun createCheckpoint(
        agentContext: AIAgentContextBase,
        nodeId: String,
        lastInput: Any?
    ): AgentCheckpointData {
        return agentContext.llm.readSession {
            return@readSession AgentCheckpointData(
                messageHistory = prompt.messages,
                nodeId = nodeId,
                lastInput = lastInput
            )
        }
    }
}

public fun AIAgentContextBase.checkpoint(): AgentCheckpoint = featureOrThrow(AgentCheckpoint.Feature)

public suspend fun <T> AIAgentContextBase.withCheckpoints(action: suspend AgentCheckpoint.() -> T): T = checkpoint().action()