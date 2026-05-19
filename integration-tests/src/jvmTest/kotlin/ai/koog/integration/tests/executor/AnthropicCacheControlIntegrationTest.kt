package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.PromptUtils
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for Anthropic cache control.
 *
 * Caching requires a minimum prompt length (usually ≥ 1024 tokens).
 * https://platform.claude.com/docs/en/build-with-claude/prompt-caching#cache-limitations
 * Tests use [ai.koog.integration.tests.utils.PromptUtils.assistantPromptOfAtLeastLength] to ensure
 * the prompt is long enough for the API to accept the cache breakpoint.
 */
@OptIn(InternalLLMClientApi::class)
class AnthropicCacheControlIntegrationTest {

    companion object {
        private val model = AnthropicModels.Sonnet_4_5
        private val client = getLLMClientForProvider(model.provider)
        private val executor = MultiLLMPromptExecutor(client)

        /**
         * Asserts that the response metadata shows cache was used (write or read).
         * On the first cached request `cacheCreationInputTokens` > 0.
         * On a subsequent request hitting the same prefix `cacheReadInputTokens` > 0.
         */
        private fun JsonObject.assertCacheWasUsed() {
            val cacheWrite = this["cacheCreationInputTokens"]?.jsonPrimitive?.intOrNull ?: 0
            val cacheRead = this["cacheReadInputTokens"]?.jsonPrimitive?.intOrNull ?: 0
            withClue("Expected cacheCreationInputTokens or cacheReadInputTokens > 0 in metadata $this") {
                (cacheWrite > 0 || cacheRead > 0).shouldBeTrue()
            }
        }

        private suspend fun testCacheControl(
            executor: MultiLLMPromptExecutor,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor> = emptyList()
        ) {
            executor.execute(prompt, model, tools)
                .let { message ->
                    message.metaInfo.metadata
                        .shouldNotBeNull()
                        .assertCacheWasUsed()
                }
        }

        @JvmStatic
        fun cacheControlType(): Stream<AnthropicCacheControl> = Stream.of(
            AnthropicCacheControl.Default,
            AnthropicCacheControl.OneHour,
        )
    }

    @ParameterizedTest
    @MethodSource("cacheControlType")
    fun integration_testAutomaticCacheControl(cacheControl: AnthropicCacheControl) = runTest(timeout = 120.seconds) {
        val params = AnthropicParams(cacheControl = cacheControl)
        val prompt = Prompt.build("test-auto-cache-1h", params = params) {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is the capital of Italy?")
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testAutomaticCacheControl"
        ) {
            testCacheControl(executor, prompt, model)
        }
    }

    @Retry
    @Test
    fun integration_testCacheControlOnSystemMessage() = runTest(timeout = 120.seconds) {
        val prompt = Prompt.build("test-cache-system-msg") {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200), AnthropicCacheControl.Default)
            user("What is the capital of France?")
        }
        testCacheControl(executor, prompt, model)
    }

    @Retry
    @Test
    fun integration_testCacheControlOnUserMessage() = runTest(timeout = 120.seconds) {
        val prompt = Prompt.build("test-cache-user-msg") {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is the capital of France?", AnthropicCacheControl.Default)
        }
        testCacheControl(executor, prompt, model)
    }

    @Retry
    @Test
    fun integration_testCacheControlOnToolDefinition() = runTest(timeout = 120.seconds) {
        val cachedTool = ToolDescriptor(
            name = "calculator",
            description = PromptUtils.assistantPromptOfAtLeastLength(1600, "A calculator tool"),
            requiredParameters = listOf(
                ToolParameterDescriptor("expression", "Math expression to evaluate", ToolParameterType.String)
            ),
            cacheControl = AnthropicCacheControl.Default
        )
        val prompt = Prompt.build("test-cache-tool") {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is 2 + 2?")
        }

        testCacheControl(executor, prompt, model, listOf(cachedTool))
    }
}
