package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Test class for verifying the behavior of the [SingleLLMPromptExecutor] with executor hooks.
 * Note: this test IS used - use cases are defined in [PromptExecutorHooksTestBase]
 */
@Suppress("unused")
class SingleLLMPromptExecutorHooksTest : PromptExecutorHooksTestBase() {

    override val model = LLModel(provider = LLMProvider.OpenAI, id = "test-model")

    override fun createExecutor(client: LLMClient): PromptExecutor = SingleLLMPromptExecutor(client)
}
