package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.integration.tests.utils.PromptUtils
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.TestCredentials
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.anthropic.AnthropicCacheControl
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for Anthropic cache control.
 *
 * These tests verify that cache control directives are correctly serialized and accepted
 * by the Anthropic API on system messages, user messages, tool definitions, and at the
 * request level via [ai.koog.prompt.executor.clients.anthropic.AnthropicParams.cacheControl].
 *
 * Caching requires a minimum prompt length (usually ≥ 1024 tokens). Tests use
 * [ai.koog.integration.tests.utils.PromptUtils.assistantPromptOfAtLeastLength] to ensure
 * the prompt is long enough for the API to accept the cache breakpoint.
 */
@OptIn(InternalLLMClientApi::class)
class AnthropicCacheControlIntegrationTest {

    companion object {
        private lateinit var client: AnthropicLLMClient
        private lateinit var executor: MultiLLMPromptExecutor

        @BeforeAll
        @JvmStatic
        fun setup() {
            val apiKey = try {
                TestCredentials.readTestAnthropicKeyFromEnv()
            } catch (_: Exception) {
                ""
            }
            Assumptions.assumeTrue(
                apiKey.isNotBlank(),
                "ANTHROPIC_API_TEST_KEY is not set; skipping cache control integration tests"
            )
            client = AnthropicLLMClient(apiKey)
            executor = MultiLLMPromptExecutor(client)
        }

        private val model = AnthropicModels.Sonnet_4_5

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
    }

    @Test
    fun integration_testAutomaticCacheControlWithDefaultTtl() = runTest(timeout = 120.seconds) {
        val params = AnthropicParams(cacheControl = AnthropicCacheControl.Default)
        val prompt = Prompt.build("test-auto-cache-1h", params = params) {
            // Minimum prompt length for Anthropic to trigger cache
            // https://platform.claude.com/docs/en/build-with-claude/prompt-caching#cache-limitations
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is the capital of Italy?")
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testAutomaticCacheControlWithOneHourTtlWritesCacheMetadata"
        ) {
            val result = executor.execute(prompt, model)
            result.shouldNotBeNull()
            result.shouldNotBeEmpty()
            result.filterIsInstance<Message.Assistant>().firstOrNull().shouldNotBeNull {
                content.lowercase().shouldContain("rome")
                metaInfo.metadata.shouldNotBeNull().assertCacheWasUsed()
            }
        }
    }

    @Test
    fun integration_testAutomaticCacheControlWithOneHourTtl() = runTest(timeout = 120.seconds) {
        val params = AnthropicParams(cacheControl = AnthropicCacheControl.OneHour)
        val prompt = Prompt.build("test-auto-cache-1h", params = params) {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is the capital of Italy?")
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testAutomaticCacheControlWithOneHourTtlWritesCacheMetadata"
        ) {
            val result = executor.execute(prompt, model)
            result.shouldNotBeNull()
            result.shouldNotBeEmpty()
            result.filterIsInstance<Message.Assistant>().firstOrNull().shouldNotBeNull {
                content.lowercase().shouldContain("rome")
                metaInfo.metadata.shouldNotBeNull().assertCacheWasUsed()
            }
        }
    }

    @Test
    fun integration_testCacheControlOnSystemMessageWritesCacheMetadata() = runTest(timeout = 120.seconds) {
        val prompt = Prompt.build("test-cache-system-1h") {
            // Caching requires a minimum prompt length to work.
            system(PromptUtils.assistantPromptOfAtLeastLength(1200), AnthropicCacheControl.Default)
            user("What is the capital of France?")
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testCacheControlOnSystemMessageWritesCacheMetadata"
        ) {
            val result = executor.execute(prompt, model)
            result.shouldNotBeNull()
            result.shouldNotBeEmpty()
            result.filterIsInstance<Message.Assistant>().firstOrNull().shouldNotBeNull {
                content.lowercase().shouldContain("paris")
                metaInfo.metadata.shouldNotBeNull().assertCacheWasUsed()
            }
        }
    }

    @Test
    fun integration_testCacheControlOnUserMessageWritesCacheMetadata() = runTest(timeout = 120.seconds) {
        val prompt = Prompt.build("test-cache-user-1h") {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is the capital of France?", AnthropicCacheControl.Default)
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testCacheControlOnUserMessageWritesCacheMetadata"
        ) {
            val result = executor.execute(prompt, model)
            result.shouldNotBeNull()
            result.shouldNotBeEmpty()
            result.filterIsInstance<Message.Assistant>().firstOrNull().shouldNotBeNull {
                content.lowercase().shouldContain("paris")
                metaInfo.metadata.shouldNotBeNull().assertCacheWasUsed()
            }
        }
    }

    @Test
    fun integration_testCacheControlOnToolDefinitionWritesCacheMetadata() = runTest(timeout = 120.seconds) {
        val cachedTool = ToolDescriptor(
            name = "calculator",
            // Caching requires a minimum prompt length — for tools this applies to the tool section.
            description = PromptUtils.assistantPromptOfAtLeastLength(1600, "A calculator tool"),
            requiredParameters = listOf(
                ToolParameterDescriptor("expression", "Math expression to evaluate", ToolParameterType.String)
            ),
            cacheControl = AnthropicCacheControl.Default
        )
        val prompt = Prompt.build("test-cache-tool-1h") {
            system(PromptUtils.assistantPromptOfAtLeastLength(1200))
            user("What is 2 + 2?")
        }

        RetryUtils.withRetry(
            times = 3,
            testName = "integration_testCacheControlOnToolDefinitionWritesCacheMetadata"
        ) {
            val result = executor.execute(prompt, model, listOf(cachedTool))
            result.shouldNotBeNull()
            result.shouldNotBeEmpty()
            // Tool call response — check any message for cache metadata
            result.first().metaInfo.metadata.shouldNotBeNull().assertCacheWasUsed()
        }
    }
}
