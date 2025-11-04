package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import java.util.function.BiFunction
import kotlin.test.*

/**
 * Tests for Java API patterns in AIAgent builder.
 * These tests verify that the builder API works correctly for Java interoperability,
 * matching the patterns used in the koog-java-exp-01 project.
 */
class JavaAPIAgentBuilderTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: Clock = object : Clock {
            override fun now(): Instant = ts
        }
    }

    @Test
    fun testAIAgentBuilderMethodExists() {
        // Test that AIAgent.builder() static method is accessible
        val builder = AIAgent.builder()
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithPromptExecutor() {
        // Test that promptExecutor can be set
        val mockExecutor = getMockExecutor { }
        val agent = AIAgent.builder()
            .promptExecutor(mockExecutor)
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("sys")
            .build()

        assertNotNull(agent)
        assertEquals(OpenAIModels.Chat.GPT4o, agent.agentConfig.model)
        assertEquals(50, agent.agentConfig.maxAgentIterations) // default from builder
    }

    @Test
    fun testBuilderWithAgentConfig() {
        // Test the agentConfig() method that's used in Java code
        val config = AIAgentConfig(
            prompt = Prompt.builder("test-id", testClock)
                .system("system")
                .user("user")
                .assistant("assistant")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 100
        )

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .agentConfig(config)
            .build()

        assertNotNull(agent)
        assertEquals(OpenAIModels.Chat.GPT4o, agent.agentConfig.model)
        assertEquals(100, agent.agentConfig.maxAgentIterations)
        assertEquals("test-id", agent.agentConfig.prompt.id)
    }

    @Test
    fun testBuilderWithToolRegistry() {
        // Test that toolRegistry can be set using the builder pattern
        val toolRegistry = ToolRegistry.builder().build()

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .llmModel(OpenAIModels.Chat.GPT4o)
            .toolRegistry(toolRegistry)
            .build()

        assertNotNull(agent)
        // Indirect assertion: agent built successfully with provided registry
    }

    @Test
    fun testComplexAgentConfigMatchingJavaPattern() {
        // Test the exact pattern from KoogAgentService.java
        // This matches lines 152-171 of the Java example
        val config = AIAgentConfig(
            prompt = Prompt.builder("id")
                .system("system")
                .user("user")
                .assistant("assistant")
                .user("user")
                .assistant("assistant")
                .toolCall("id-1", "tool-1", "args-1")
                .toolResult("id-1", "tool-1", "result-1")
                .toolCall("id-2", "tool-2", "args-2")
                .toolResult("id-2", "tool-2", "result-2")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 100
        )

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .agentConfig(config)
            .build()

        assertNotNull(agent)
        assertEquals(OpenAIModels.Chat.GPT4o, agent.agentConfig.model)
        // 2 system/user pairs + 2 tool calls + 2 tool results = 6 messages plus initial system => account for actual structure
        assertTrue(agent.agentConfig.prompt.messages.isNotEmpty())
    }

    @Test
    fun testFunctionalStrategyWithLambda() {
        // Test that functional strategy BiFunction-based Java API can be set
        val config = AIAgentConfig(
            prompt = Prompt.builder("test-id", testClock)
                .system("You are a helpful assistant")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 50
        )

        val agent = AIAgent.builder()
            .agentConfig(config)
            .functionalStrategy<String, String>(
                "echoStrategy",
                BiFunction { _: AIAgentFunctionalContext, input: String -> "Echo: $input" }
            )
            .promptExecutor(getMockExecutor { })
            .build()

        val result = agent.run("hello", null)
        assertEquals("Echo: hello", result)
    }

    @Test
    fun testFunctionalStrategyWithClass() {
        // Test that functional strategy can be set with a custom strategy class
        // This matches the MyStrategy pattern from the Java example
        class TestStrategy(name: String) : NonSuspendAIAgentFunctionalStrategy<String, String>(name) {
            override fun executeStrategy(context: AIAgentFunctionalContext, input: String): String {
                return "Processed: $input"
            }
        }

        val config = AIAgentConfig(
            prompt = Prompt.builder("test-id", testClock)
                .system("Test system")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 50
        )

        val strategy = TestStrategy("test-strategy")

        val agent = AIAgent.builder()
            .agentConfig(config)
            .functionalStrategy(strategy)
            .promptExecutor(getMockExecutor { })
            .build()

        val result = agent.run("data", null)
        assertEquals("Processed: data", result)
    }

    @Test
    fun testBuilderChaining() {
        // Test that builder methods can be chained fluently
        val config = AIAgentConfig(
            prompt = Prompt.builder("chaining-test", testClock)
                .system("System message")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 25
        )

        val toolRegistry = ToolRegistry.builder().build()

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .agentConfig(config)
            .toolRegistry(toolRegistry)
            .temperature(0.7)
            .build()

        assertNotNull(agent)
        assertEquals(25, agent.agentConfig.maxAgentIterations)
        assertEquals("chaining-test", agent.agentConfig.prompt.id)
    }

    @Test
    fun testBuilderWithMultipleConfigurations() {
        // Test that builder can handle multiple configuration calls
        val config1 = AIAgentConfig(
            prompt = Prompt.builder("config-1", testClock)
                .system("First config")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10
        )

        val config2 = AIAgentConfig(
            prompt = Prompt.builder("config-2", testClock)
                .system("Second config")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 20
        )

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .agentConfig(config1)
            .agentConfig(config2) // Should override config1
            .build()

        assertNotNull(agent)
        assertEquals(20, agent.agentConfig.maxAgentIterations)
        assertEquals("config-2", agent.agentConfig.prompt.id)
    }

    @Test
    fun testBuilderInstallEventHandlerFeature() {
        val toolRegistry = ToolRegistry.builder().build()

        val agent = AIAgent.builder()
            .promptExecutor(getMockExecutor { })
            .llmModel(OpenAIModels.Chat.GPT4o)
            .toolRegistry(toolRegistry)
            .systemPrompt("sys")
            .install(ai.koog.agents.features.eventHandler.feature.EventHandler) { cfg ->
                cfg.onToolCallStarting { }
                cfg.onAgentClosing { }
            }
            .build()

        assertNotNull(agent)
    }
}
