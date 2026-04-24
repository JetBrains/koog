package ai.koog.prompt.executor.llms

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Test class for verifying the hook lifecycle of [SingleLLMPromptExecutor].
 * Note: this test IS used - use cases are defined in [PromptExecutorHooksTestBase]
 */
@Suppress("unused")
class SingleLLMPromptExecutorHooksTest : PromptExecutorHooksTestBase() {

    override val model = LLModel(provider = LLMProvider.OpenAI, id = "test-model")

    override fun createExecutor(client: LLMClient): HookablePromptExecutor = SingleLLMPromptExecutor(client)
}
