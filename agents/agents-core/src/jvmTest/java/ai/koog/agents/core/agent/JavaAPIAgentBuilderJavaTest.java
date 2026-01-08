package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.agents.testing.tools.MockExecutor;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java tests for AIAgent builder API and functional strategies (lambda and custom class).
 */
public class JavaAPIAgentBuilderJavaTest {

    private static AIAgentConfig baseConfig() {
        return AIAgentConfig.builder(OpenAIModels.Chat.GPT4_1)
            .prompt(
                Prompt.builder("id")
                    .system("system")
                    .user("user")
                    .assistant("assistant")
                    .user("user")
                    .assistant("assistant")
                    .toolCall("id-1", "tool-1", "args-1")
                    .toolResult("id-1", "tool-1", "result-1")
                    .toolCall("id-2", "tool-2", "args-2")
                    .toolResult("id-2", "tool-2", "result-2")
                    .build()
            )
            .maxAgentIterations(100)
            .llmRequestExecutorService(Executors.newSingleThreadExecutor())
            .strategyExecutorService(Executors.newSingleThreadExecutor())
            .build();
    }

    @Test
    public void testBuilderWithAgentConfigAndEventInstall() {
        ToolRegistry registry = ToolRegistry.builder().build();

        var agent = AIAgent.builder()
            .promptExecutor(
                MockExecutor.builder()
                    .toolRegistry(registry)
                    .mockLLMAnswer("ok").asDefaultResponse()
                    .build()
            )
            .agentConfig(baseConfig())
            .toolRegistry(registry)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> {
                });
                config.onAgentClosing(ctx -> {
                });
            })
            .build();

        assertNotNull(agent);
        assertEquals(OpenAIModels.Chat.GPT4_1, agent.getAgentConfig().getModel());
        assertEquals(100, agent.getAgentConfig().getMaxAgentIterations());
        assertEquals("id", agent.getAgentConfig().getPrompt().getId());
    }

    @Test
    public void testFunctionalStrategyWithLambda() {
        var executor = MockExecutor.builder()
            .mockLLMAnswer("assistant-reply").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(
                AIAgentConfig.builder(OpenAIModels.Chat.GPT4o)
                    .prompt(Prompt.builder("p").user("hi").build())
                    .maxAgentIterations(3)
                    .build()
            )
            .functionalStrategy("myStrategy", (AIAgentFunctionalContext context, String userInput) -> {
                // just echo last LLM answer to ensure the pipeline works
                Message.Response resp = context.requestLLM(userInput);
                if (resp instanceof Message.Assistant) {
                    return ((Message.Assistant) resp).getContent();
                }
                return "";
            })
            .build();

        String out = agent.run("input");
        assertEquals("assistant-reply", out);
    }

    static class MyJavaStrategy extends NonSuspendAIAgentFunctionalStrategy<String, String> {
        public MyJavaStrategy() {
            super("my");
        }

        @Override
        public String executeStrategy(AIAgentFunctionalContext context, String input) {
            // Use a writeSession to temporarily change prompt, then restore
            String content = context.llm().writeSession(session -> {
                var original = session.getPrompt();
                session.setPrompt(Prompt.builder("tmp").user("q").build());
                Message.Response r = session.requestLLM();
                // restore and return assistant content
                session.setPrompt(original);
                if (r instanceof Message.Assistant) return ((Message.Assistant) r).getContent();
                return "";
            });
            return content + ":" + input;
        }
    }

    @Test
    public void testFunctionalStrategyWithClass() {
        var executor = MockExecutor.builder()
            .mockLLMAnswer("class-reply").asDefaultResponse()
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(executor)
            .agentConfig(
                AIAgentConfig.builder(OpenAIModels.Chat.GPT4o)
                    .prompt(Prompt.builder("p").user("hi").build())
                    .maxAgentIterations(3)
                    .build()
            )
            .functionalStrategy(new MyJavaStrategy())
            .build();

        String out = agent.run("u");
        assertEquals("class-reply:u", out);
    }
}
