@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.utils.io.Closeable

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual abstract class AIAgent<Input, Output> : Closeable {
    public actual abstract val id: String
    public actual abstract val agentConfig: AIAgentConfig

    public actual abstract suspend fun run(agentInput: Input, sessionId: String?): Output

    public actual abstract fun createSession(sessionId: String?): AIAgentRunSession<Input, Output, out AIAgentContext>

    public actual companion object {
        public actual fun builder(): AIAgentBuilder = AIAgentBuilder()
    }
}
