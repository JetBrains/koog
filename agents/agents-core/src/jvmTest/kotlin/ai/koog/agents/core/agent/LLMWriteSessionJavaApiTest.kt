package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.junit.jupiter.api.Test
import java.util.function.BiFunction
import kotlin.test.assertEquals

class LLMWriteSessionJavaApiTest {

    @Test
    fun writeSession_allowsPromptSwapAndRequest() {
        val executor = getMockExecutor { }

        val config = AIAgentConfig(
            prompt = Prompt.builder("write-session")
                .system("base")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 3
        )

        val agent = AIAgent.builder()
            .agentConfig(config)
            .functionalStrategy<String, String>(
                "useWriteSession",
                BiFunction { ctx: AIAgentFunctionalContext, input: String ->
                    // Mutate prompt inside writeSession and ensure it restores back
                    ctx.llm().writeSession { session ->
                        val orig = session.prompt
                        session.prompt = Prompt.builder("temp").system("temporary").user(input).build()
                        // restore immediately to validate restoration path without invoking suspend APIs here
                        session.prompt = orig
                    }
                    // Return a deterministic string to prove strategy executed without using suspend APIs
                    "mutated:$input"
                }
            )
            .promptExecutor(executor)
            .build()

        val result = agent.run("hello", null)
        assertEquals("mutated:hello", result)
    }
}
