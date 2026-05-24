package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.serialization.kotlinx.KotlinxSerializer
import org.junit.jupiter.api.Test
import java.util.function.BiFunction
import kotlin.test.assertEquals

class JavaAPIAgentCreationAndRunTest {
    private val serializer = KotlinxSerializer()

    @Test
    fun buildFunctionalAgentAndRun_viaJavaAPIOverloads() {
        val config = AIAgentConfig(
            prompt = Prompt.builder("java-api-agent")
                .system("You echo input")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 5
        )

        val executor = getMockExecutor(serializer) { }
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

        // Use Java-facing overloads
        val result = agent.runBlocking("xyz", null)
        assertEquals("Echo: xyz", result)
    }
}
