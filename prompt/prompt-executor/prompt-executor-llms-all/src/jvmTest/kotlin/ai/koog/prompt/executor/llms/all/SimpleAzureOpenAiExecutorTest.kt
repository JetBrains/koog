package ai.koog.prompt.executor.llms.all

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIServiceVersion
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleAzureOpenAiExecutorTest {
    @Test
    fun shouldFormProperUrlFromAzureSettings() {
        val mockResponses = mapOf(
            "https://azure-resource-name.openai.azure.com/openai/deployments/azure-deployment-name/chat/completions?api-version=2025-01-01-preview" to
                MockResponse(
                    content = """{
                    "choices": [
                        {
                            "finish_reason": "stop",
                            "index": 0,
                            "logprobs": null,
                            "message": {
                                "annotations": [],
                                "content": "The capital of France is **Paris**.",
                                "refusal": null,
                                "role": "assistant"
                            }
                        }
                    ],
                    "created": 1753772779,
                    "id": "chatcmpl-jaskjvasbjvkbsga",
                    "model": "gpt-4o-2024-11-20",
                    "object": "chat.completion",
                    "prompt_filter_results": [
                        {
                            "prompt_index": 0
                        }
                    ]
                }""",
                    status = HttpStatusCode.OK,
                )
        )
        val mockClient = createMockHttpClient(mockResponses)

        val settings = AzureOpenAIClientSettings(
            AZURE_RESOURCE_NAME,
            AZURE_DEPLOYMENT_NAME,
            AzureOpenAIServiceVersion.V2025_01_01_PREVIEW,
        )

        val llmClient = OpenAILLMClient(AZURE_API_TOKEN, settings, mockClient)
        val agent = AIAgent(SingleLLMPromptExecutor(llmClient), OpenAIModels.Chat.GPT4o)

        val response = runBlocking { agent.run("What is the capital of France?") }
        assertEquals(
            "The capital of France is **Paris**.",
            response,
            "Response should be from mocked Azure client call"
        )
    }

    private companion object {
        private const val AZURE_API_TOKEN = "azure-api-token"
        private const val AZURE_RESOURCE_NAME = "azure-resource-name"
        private const val AZURE_DEPLOYMENT_NAME = "azure-deployment-name"
    }
}
