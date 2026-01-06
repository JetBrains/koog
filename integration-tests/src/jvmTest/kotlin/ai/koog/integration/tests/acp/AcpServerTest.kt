package ai.koog.integration.tests.acp

import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AcpServerTest {
    companion object {
        @JvmStatic
        fun getModels() = listOf(
            OpenAIModels.Chat.GPT5_2,
            AnthropicModels.Haiku_4_5,
            GoogleModels.Gemini2_5Pro,
        )
    }

    @ParameterizedTest
    @MethodSource("getModels")
    fun integration_testACPWithTools(model: LLModel) = runTest(timeout = 1.minutes) {
        SingleLLMPromptExecutor(getLLMClientForProvider(model.provider)).use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(30.seconds) {
                    val result = runAcpAgent(promptExecutor, model)
                    result.shouldNotBeBlank()
                    result.shouldContain("random")
                }
            }
        }
    }

    private suspend fun runAcpAgent(
        promptExecutor: PromptExecutor,
        model: LLModel
    ): String = coroutineScope {
        val randomNumberTool = RandomNumberTool()
        val setup = setupAcpClient(this, promptExecutor, model, randomNumberTool)

        try {
            val promptContent = listOf(
                ContentBlock.Text("Provide a random number using the ${randomNumberTool.name} tool. YOU MUST USE TOOLS!")
            )

            val events = mutableListOf<Event>()
            setup.session!!.prompt(promptContent).collect { events.add(it) }

            val toolCalls = events.filterIsInstance<Event.SessionUpdateEvent>()
                .map { it.update }
                .filterIsInstance<SessionUpdate.ToolCall>()

            toolCalls.any { it.status == ToolCallStatus.IN_PROGRESS } shouldBe true
            toolCalls.any { it.status == ToolCallStatus.COMPLETED } shouldBe true

            "Tool ${randomNumberTool.name} generated: ${randomNumberTool.last}"
        } finally {
            setup.cleanup()
        }
    }
}
