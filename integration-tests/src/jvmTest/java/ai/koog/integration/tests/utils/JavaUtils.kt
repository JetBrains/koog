package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
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

    // Storage helpers for Java interop
    @JvmStatic
    fun <T : Any> storageSet(storage: AIAgentStorage, key: AIAgentStorageKey<T>, value: T): Unit = runBlocking {
        storage.set(key, value)
    }

    @JvmStatic
    fun <T : Any> storageGet(storage: AIAgentStorage, key: AIAgentStorageKey<T>): T? = runBlocking {
        storage.get(key)
    }
}
