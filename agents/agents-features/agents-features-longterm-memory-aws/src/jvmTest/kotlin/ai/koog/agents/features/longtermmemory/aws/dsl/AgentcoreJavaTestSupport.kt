package ai.koog.agents.features.longtermmemory.aws.dsl

import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import io.mockk.mockk

/**
 * Java-friendly helpers for [AgentcoreRetrievalJavaTest].
 *
 * The MockK Java surface is awkward (vararg `KClass`, default params), so these helpers
 * encapsulate the couple of Kotlin-shaped calls we need from Java test code.
 */
internal object AgentcoreJavaTestSupport {
    /**
     * Returns a relaxed [BedrockAgentCoreClient] mock. We never call any method on it
     * in the Java builder tests — only identity is asserted.
     */
    @JvmStatic
    fun mockClient(): BedrockAgentCoreClient = mockk(relaxed = true)
}
