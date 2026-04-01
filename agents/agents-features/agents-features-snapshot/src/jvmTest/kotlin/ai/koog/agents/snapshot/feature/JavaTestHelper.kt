package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import straightForwardGraphNoCheckpoint

object JavaTestHelper {
    @JvmStatic
    fun straightForwardGraph(): AIAgentGraphStrategy<String, String> =
        straightForwardGraphNoCheckpoint()
}
