package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import kotlinx.coroutines.runBlocking

object JavaUtils {
    @JvmStatic
    fun <T : Any> requestLLMStructuredBlocking(
        context: AIAgentFunctionalContext,
        message: String,
        outputType: Class<T>
    ): T = runBlocking {
        context.requestLLMStructured(message, outputType.kotlin, emptyList(), null).getOrThrow().data
    }
}
