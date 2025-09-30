package ai.koog.spring.prompt.executor.clients

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.spring.RetryConfigKoogProperties
import kotlin.time.toKotlinDuration

internal fun LLMClient.toRetryingClient(properties: RetryConfigKoogProperties?): LLMClient {
    val self = this
    return if (properties?.enabled == true) {
        val defaultConfig = RetryConfig()
        val retryConfig = RetryConfig(
            maxAttempts = properties.maxAttempts ?: defaultConfig.maxAttempts,
            initialDelay = properties.initialDelay?.toKotlinDuration() ?: defaultConfig.initialDelay,
            maxDelay = properties.maxDelay?.toKotlinDuration() ?: defaultConfig.maxDelay,
            backoffMultiplier = properties.backoffMultiplier ?: defaultConfig.backoffMultiplier,
            jitterFactor = properties.jitterFactor ?: defaultConfig.jitterFactor
        )
        RetryingLLMClient(
            delegate = self,
            config = retryConfig
        )
    } else {
        self
    }
}
