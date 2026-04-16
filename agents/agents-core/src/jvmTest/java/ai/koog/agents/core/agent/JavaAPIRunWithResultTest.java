package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.testing.tools.MockExecutorBuilder;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.JSONSerializer;
import ai.koog.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java tests for AIAgent.runWithResult() API.
 */
public class JavaAPIRunWithResultTest {
    private static final JSONSerializer serializer = new JacksonSerializer();

    private static AIAgentConfig baseConfig() {
        return AIAgentConfig.builder()
            .model(OpenAIModels.Chat.GPT4_1)
            .prompt(
                Prompt.builder("id")
                    .system("system")
                    .user("user")
                    .build()
            )
            .maxAgentIterations(100)
            .llmRequestExecutorService(Executors.newSingleThreadExecutor())
            .strategyExecutorService(Executors.newSingleThreadExecutor())
            .build();
    }

    @Test
    public void testRunWithResultFromJava() {
        var executor = new MockExecutorBuilder(serializer)
            .mockLLMAnswer("assistant-reply").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(baseConfig())
            .build();

        AIAgentResult<String> result = agent.runWithResult("input");
        assertNotNull(result);
        assertEquals("assistant-reply", result.getOutput());
    }

    @Test
    public void testRunFromJavaStillReturnsStringDirectly() {
        var executor = new MockExecutorBuilder(serializer)
            .mockLLMAnswer("direct-reply").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(baseConfig())
            .build();

        String output = agent.run("input");
        assertEquals("direct-reply", output);
    }

    @Test
    public void testRunWithResultWithFunctionalStrategyFromJava() {
        var executor = new MockExecutorBuilder(serializer)
            .mockLLMAnswer("functional-reply").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(baseConfig())
            .functionalStrategy("myStrategy", (AIAgentFunctionalContext context, String userInput) -> {
                Message.Response resp = context.requestLLM(userInput);
                if (resp instanceof Message.Assistant) {
                    return ((Message.Assistant) resp).getContent();
                }
                return "";
            })
            .build();

        AIAgentResult<String> result = agent.runWithResult("input");
        assertNotNull(result);
        assertEquals("functional-reply", result.getOutput());
    }

    @Test
    public void testRunWithResultIsInstanceOfContextualResult() {
        var executor = new MockExecutorBuilder(serializer)
            .mockLLMAnswer("ok").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(baseConfig())
            .build();

        AIAgentResult<String> result = agent.runWithResult("input");
        assertInstanceOf(AIAgentContextualResult.class, result);
    }
}
