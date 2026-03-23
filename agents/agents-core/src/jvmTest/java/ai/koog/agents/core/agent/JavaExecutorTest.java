package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.testing.tools.MockExecutorBuilder;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.serialization.JSONSerializer;
import ai.koog.serialization.jackson.JacksonSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaExecutorTest {
    private static final JSONSerializer serializer = new JacksonSerializer();

    @Test
    public void sharedExecutorUsingSameExecutorsTest() throws Exception {
        var mockExecutor = new MockExecutorBuilder(serializer)
            .mockLLMAnswer("ok").asDefaultResponse()
            .build();

        ExecutorService sharedExecutor = Executors.newSingleThreadExecutor();

        try {
            AIAgentConfig config = new AIAgentConfig(
                Prompt.builder("deadlock-prompt").system("system").build(),
                OpenAIModels.Chat.GPT4o,
                3,
                sharedExecutor,
                sharedExecutor
            );

            AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(mockExecutor)
                .agentConfig(config)
                .functionalStrategy(new NonSuspendAIAgentFunctionalStrategy<String, String>("same-executor-run") {
                    @Override
                    public String executeStrategy(ai.koog.agents.core.agent.context.@NotNull AIAgentFunctionalContext context, String input) {
                        return input;
                    }
                })
                .build();

            Future<String> future = sharedExecutor.submit(() ->
                agent.run("ok", null, sharedExecutor)
            );

            assertEquals("ok", future.get(2, TimeUnit.SECONDS));
            future.cancel(true);
        } finally {
            sharedExecutor.shutdownNow();
        }
    }
}
