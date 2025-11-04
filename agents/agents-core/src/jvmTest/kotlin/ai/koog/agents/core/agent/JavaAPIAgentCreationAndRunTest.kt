package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.function.BiFunction
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class JavaAPIAgentCreationAndRunTest {

    @Test
    fun buildFunctionalAgentAndRun_viaJavaAPIOverloads() {
        val config = AIAgentConfig(
            prompt = Prompt.builder("java-api-agent")
                .system("You echo input")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 5
        )

        val executor = getMockExecutor { }
        val toolRegistry = ToolRegistry.builder().build()

        val agent = AIAgent.builder()
            .agentConfig(config)
            .functionalStrategy<String, String>(
                "echo",
                BiFunction { _: AIAgentFunctionalContext, input: String -> "Echo: $input" }
            )
            .toolRegistry(toolRegistry)
            .promptExecutor(executor)
            .build()

        val pool = Executors.newSingleThreadExecutor()
        try {
            // Use Java-facing overloads
            val result = agent.run("xyz", pool)
            assertEquals("Echo: xyz", result)

            val state = agent.getState(pool)
            assertIs<AIAgentState.Finished<String>>(state)
            assertEquals("Echo: xyz", state.result)
        } finally {
            pool.shutdownNow()
        }
    }
}
