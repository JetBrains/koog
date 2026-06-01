package ai.koog.example.foundationmodels

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.foundationmodels.AppleLLMProvider
import ai.koog.prompt.executor.clients.foundationmodels.AppleLLModels
import ai.koog.prompt.executor.clients.foundationmodels.FoundationModelsLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor

/**
 * On-device smoke test: builds a [FoundationModelsLLMClient]-backed agent and runs one prompt.
 *
 * Swift-callable: Kotlin/Native exposes this `suspend` function to Swift as a completion-handler
 * method; the `@Throws` makes [ai.koog.prompt.executor.clients.foundationmodels.FoundationModelsException]
 * (e.g. Unavailable when Apple Intelligence is off) surface as an `NSError` instead of crashing.
 */
@Throws(Throwable::class)
public suspend fun runFoundationModelsSmokeTest(
    prompt: String = "Say hello in exactly three words.",
): String {
    val client = FoundationModelsLLMClient()
    val executor = MultiLLMPromptExecutor(mapOf(AppleLLMProvider to client))
    val agent = AIAgent(
        promptExecutor = executor,
        llmModel = AppleLLModels.SystemDefault,
    )
    return agent.run(prompt)
}
