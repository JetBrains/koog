package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FoundationModelsAgentTest {

    @Test
    fun testAgentRunsToCompletionThroughFmClient() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession(response = "42"))
        val executor = MultiLLMPromptExecutor(mapOf(AppleLLMProvider to client))

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = AppleLLModels.SystemDefault,
            systemPrompt = "be brief",
        )

        val result = agent.run("what is 6 times 7?")
        assertEquals("42", result)
    }

    @Test
    fun testAgentTerminatesOnEmptyResponse() = runTest {
        val client = FoundationModelsLLMClient(FakeFoundationModelsSession(response = ""))
        val executor = MultiLLMPromptExecutor(mapOf(AppleLLMProvider to client))
        val agent = AIAgent(promptExecutor = executor, llmModel = AppleLLModels.SystemDefault)

        val result = agent.run("hello")
        assertEquals("", result)
    }
}
