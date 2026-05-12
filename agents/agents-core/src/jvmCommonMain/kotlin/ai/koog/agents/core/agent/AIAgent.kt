@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runBlockingOnLLMDispatcher
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.io.Closeable
import java.util.concurrent.ExecutorService

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual abstract class AIAgent<Input, Output> : Closeable {
    public actual abstract val id: String
    public actual abstract val agentConfig: AIAgentConfig

    // JAVA Unique methods:

    /**
     * Executes the AI agent task based on the provided input and an optional executor service.
     *
     * @param agentInput The input data required for the AI agent to perform its task.
     * @param sessionId An optional session ID for the agent run.
     * @param executorService An optional [ExecutorService] to provide a coroutine context for execution.
     *                        If not provided, the default coroutine context is used.
     * @return The output resulting from the execution of the AI agent with the given input.
     */
    @OptIn(InternalKoogUtils::class)
    @JavaAPI
    @JvmOverloads
    @JvmName("run")
    public final fun javaNonSuspendRun(
        agentInput: Input,
        sessionId: String? = null,
        executorService: ExecutorService? = null
    ): Output = agentConfig.runBlockingOnLLMDispatcher(executorService) {
        run(agentInput, sessionId)
    }

    // Common (multiplatform) methods:
    public actual abstract suspend fun run(agentInput: Input, sessionId: String?): Output

    public actual abstract fun createSession(sessionId: String?): AIAgentRunSession<Input, Output, out AIAgentContext>

    public actual companion object {
        @JvmStatic
        public actual fun builder(): AIAgentBuilder = AIAgentBuilder()
    }
}
