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

class LLMReadSessionJavaApiTest {
    private val serializer = KotlinxSerializer()

    @Test
    fun javaNonSuspendRunWithSameThreadExecutorRead() {
        val executor = getMockExecutor(serializer) {
            mockLLMAnswer("ok").asDefaultResponse
        }

        val config = AIAgentConfig(
            prompt = Prompt.builder("read")
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
                    "read"
                ) { ctx: AIAgentFunctionalContext, _: String ->
                    val response = ctx.llm().readSession { session ->
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
