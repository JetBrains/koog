package ai.koog.agents.core.agent;

import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.agent.entity.AIAgentNodeBase;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.dsl.builder.AIAgentEdgeBuilderIntermediate;
import ai.koog.agents.core.dsl.builder.SimpleTransformation;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.agents.testing.tools.MockPromptExecutor;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphStrategyJavaTest {
    private static final JacksonSerializer serializer = new JacksonSerializer();

    @Test
    public void testMinimalGraph() {
        AIAgent<String, String> agent = (AIAgent<String, String>) AIAgent.builder()
            .promptExecutor(MockPromptExecutor.builder(serializer).mockLLMAnswer("ok").asDefaultResponse().build())
            .llmModel(OpenAIModels.Chat.GPT4o)
            .graphStrategy("minimal", b -> {
                var graph = b
                    .withInput(String.class)
                    .withOutput(String.class);

                var node = AIAgentNode.builder("node")
                    .withInput(String.class)
                    .withOutput(String.class)
                    .withAction((input, ctx) -> "echo: " + input)
                    .build();

                graph.edge(graph.nodeStart, node);
                graph.edge(node, graph.nodeFinish);

                return graph.build();
            })
            .build();

        String result = agent.run("hello", null);
        assertEquals("echo: hello", result);
    }

    @Test
    public void testLLMRequestNode() {
        AIAgent<String, String> agent = (AIAgent<String, String>) AIAgent.builder()
            .promptExecutor(MockPromptExecutor.builder(serializer).mockLLMAnswer("llm-response").asDefaultResponse().build())
            .llmModel(OpenAIModels.Chat.GPT4o)
            .<String, String>graphStrategy("llm", b -> {
                var graph = b
                    .withInput(String.class)
                    .withOutput(String.class);

                var llmNode = AIAgentNode.llmRequest(false, "llm");

                graph.edge(graph.nodeStart, llmNode);
                graph.edge(AIAgentEdge.builder()
                    .from(llmNode)
                    .to(graph.nodeFinish)
                    .transformed(response -> {
                        if (response instanceof Message.Assistant) {
                            return ((Message.Assistant) response).getContent();
                        }
                        return "error";
                    })
                    .build()
                );

                return graph.build();
            })
            .build();

        String result = agent.run("hello", null);
        assertEquals("llm-response", result);
    }

    @Test
    public void testCompressionAndJudge() {
        AIAgent<String, Boolean> agent = (AIAgent<String, Boolean>) AIAgent.builder()
            .promptExecutor(MockPromptExecutor.builder(serializer)
                .mockLLMAnswer("{\"isCorrect\": true, \"feedback\": \"all good\"}")
                .asDefaultResponse().build())
            .llmModel(OpenAIModels.Chat.GPT4o)
            .graphStrategy("complex", b -> {
                var graph = b
                    .withInput(String.class)
                    .withOutput(Boolean.class);

                var compress = AIAgentNode
                    .llmCompressHistory("compress")
                    .withInput(String.class)
                    .compressionStrategy(HistoryCompressionStrategy.WholeHistory)
                    .preserveMemory(true)
                    .build();

                var judge = AIAgentNode.builder("judge")
                    .withInput(String.class)
                    .llmAsAJudge("test task");

                graph.edge(graph.nodeStart, compress);
                graph.edge(compress, judge);
                graph.edge(AIAgentEdge.builder()
                    .from(judge)
                    .to(graph.nodeFinish)
                    .transformed(criticResult -> criticResult.isSuccessful())
                    .build());

                return graph.build();
            })
            .build();

        Boolean result = agent.run("test input", null);

        assertTrue(result);
    }

    @Test
    public void testFinishToolSubgraph() {
        AIAgent<String, Long> agent = (AIAgent<String, Long>) AIAgent.builder()
            .promptExecutor(MockPromptExecutor.builder(serializer)
                .mockLLMAnswer("not used")
                .asDefaultResponse().build())
            .llmModel(OpenAIModels.Chat.GPT4o)
            .toolRegistry(ToolRegistry.builder().build())
            .graphStrategy("finish-tool", b -> {
                var graph = b
                    .withInput(String.class)
                    .withOutput(Long.class);

                var sub = AIAgentSubgraph.builder("with-finish-tool")
                    .withInput(String.class)
                    .withOutput(Long.class)
                    .limitedTools(Collections.emptyList())
                    .withTask(input -> "Use my_tool to return a value")
                    .build();

                graph.edge(graph.nodeStart, sub);
                graph.edge(sub, graph.nodeFinish);

                return graph.build();
            })
            .build();

        assertNotNull(agent);
    }

    public static class MyTools implements ToolSet {
        @ai.koog.agents.core.tools.annotations.Tool
        public long my_tool(int arg) {
            return (long) arg;
        }
    }
}
