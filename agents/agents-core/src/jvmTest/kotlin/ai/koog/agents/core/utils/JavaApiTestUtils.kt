package ai.koog.agents.core.utils

import ai.koog.agents.core.agent.AIAgent
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

fun submitRun(
    sharedExecutor: ExecutorService,
    agent: AIAgent<String, String>
): Future<String> {
    return sharedExecutor.submit<String> {
        try {
            agent.javaNonSuspendRun("hello", null, sharedExecutor)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
