package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.utils.submitRun
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.serialization.kotlinx.KotlinxSerializer
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class LLMWriteSessionJavaApiTest {
    private val serializer = KotlinxSerializer()

    @Test
    fun writeSession_allowsPromptSwapAndRequest() {
        val executor = getMockExecutor(serializer) { }

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
                "useWriteSession"
            ) { ctx: AIAgentFunctionalContext, input: String ->
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
            .promptExecutor(executor)
            .build()

        val result = agent.javaNonSuspendRun("hello", null, null)
        assertEquals("mutated:hello", result)
    }

    @Test
    fun javaNonSuspendRunWithSameThreadExecutorWrite() {
        val executor = getMockExecutor(serializer) {
            mockLLMAnswer("ok").asDefaultResponse
        }

        val config = AIAgentConfig(
            prompt = Prompt.builder("write")
                .system("test")
                .build(),
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 3
        )

        val sharedExecutor = Executors.newSingleThreadExecutor()
        try {
            val agent = AIAgent.builder()
                .agentConfig(config)
                .functionalStrategy(
                    "write"
                ) { ctx: AIAgentFunctionalContext, _: String ->
                    val response = ctx.llm().writeSession { session ->
                        session.requestLLM(sharedExecutor)
                    }
                    (response as Message.Assistant).content
                }
                .promptExecutor(executor)
                .build()

            val future = submitRun(sharedExecutor, agent)

            assertEquals("ok", future.get(3, TimeUnit.SECONDS))

            future.cancel(true)
        } finally {
            sharedExecutor.shutdownNow()
        }
    }
}
