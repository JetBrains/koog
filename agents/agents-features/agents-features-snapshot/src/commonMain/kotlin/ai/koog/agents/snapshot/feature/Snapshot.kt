@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import kotlin.uuid.ExperimentalUuidApi

public class Snapshot {
    public companion object Feature : AIAgentFeature<SnapshotFeatureConfig, Snapshot> {
        override val key: AIAgentStorageKey<Snapshot>
            get() = AIAgentStorageKey("agents-features-snapshot")

        override fun createInitialConfig(): SnapshotFeatureConfig = SnapshotFeatureConfig()

        @OptIn(ExperimentalUuidApi::class)
        override fun install(
            config: SnapshotFeatureConfig,
            pipeline: AIAgentPipeline
        ) {
            val featureImpl = Snapshot()
            val interceptContext = InterceptContext(this, featureImpl)

            pipeline.interceptAfterNode(interceptContext) { node: AIAgentNodeBase<*, *>,
                                                            context: AIAgentContextBase,
                                                            input: Any?,
                                                            output: Any? ->
                val history = context.getHistory()
                val snapshot = AgentSnapshot(
                    agentId = context.sessionUuid.toString(),
                    snapshotId = "${AgentSnapshot.SNAPSHOT_ID_PREFIX}${node.name}",
                    lastExecutedNodeId = node.name,
                    lastOutput = output,
                    messages = history
                )

                config.snapshotProvider.saveSnapshot(snapshot)
                println("Snapshot feature intercepting after node: ${node.name}")
            }
        }
    }
}