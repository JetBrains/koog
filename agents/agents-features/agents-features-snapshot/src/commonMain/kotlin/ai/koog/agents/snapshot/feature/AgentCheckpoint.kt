@file:Suppress("MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.snapshot.providers.AgentCheckpointStorageProvider
import ai.koog.prompt.message.Message
import kotlin.uuid.ExperimentalUuidApi

public class AgentCheckpoint(private val agentCheckpointStorageProvider: AgentCheckpointStorageProvider) {
    public var currentNodeId: String? = null

    public companion object Feature : AIAgentFeature<AgentCheckpointFeatureConfig, AgentCheckpoint> {
        override val key: AIAgentStorageKey<AgentCheckpoint>
            get() = AIAgentStorageKey("agents-features-snapshot")

        override fun createInitialConfig(): AgentCheckpointFeatureConfig = AgentCheckpointFeatureConfig()


        @OptIn(ExperimentalUuidApi::class)
        override fun install(
            config: AgentCheckpointFeatureConfig,
            pipeline: AIAgentPipeline
        ) {
            val featureImpl = AgentCheckpoint(config.agentCheckpointStorageProvider)
            val interceptContext = InterceptContext(this, featureImpl)

            pipeline.interceptContextAgentFeature(this) {
                featureImpl
            }

            pipeline.interceptBeforeNode(interceptContext) { node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                featureImpl.currentNodeId = node.id
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

    public suspend fun createCheckpoint(
        checkpointId: String,
        agentContext: AIAgentContextBase,
        nodeId: String,
        lastInput: Any?
    ): AgentCheckpointData {
        return agentContext.llm.readSession {
            return@readSession AgentCheckpointData(
                checkpointId = checkpointId,
                messageHistory = prompt.messages,
                nodeId = nodeId,
                lastInput = lastInput
            )
        }
    }

    public suspend fun getCheckpoint(agentId: String, checkpointId: String): AgentCheckpointData? {
        return agentCheckpointStorageProvider.getCheckpoint(agentId, checkpointId)
    }

    public fun setExecutionPoint(agentContext: AIAgentContextBase, nodeId: String, messageHistory: List<Message>, input: Any?) {
        agentContext.forcedContextData = AgentContextData(messageHistory, nodeId, input)
    }

    public suspend fun rollbackToCheckpoint(
        agentId: String,
        checkpointId: String,
        agentContext: AIAgentContextBase
    ): AgentCheckpointData? {
        val checkpoint: AgentCheckpointData? = getCheckpoint(agentId, checkpointId)
        if (checkpoint != null) {
            agentContext.forcedContextData = checkpoint.toAgentContextData()
        }
        return checkpoint
    }
}

public fun AIAgentContextBase.checkpoint(): AgentCheckpoint = featureOrThrow(AgentCheckpoint.Feature)

public suspend fun <T> AIAgentContextBase.withCheckpoints(action: suspend AgentCheckpoint.() -> T): T = checkpoint().action()