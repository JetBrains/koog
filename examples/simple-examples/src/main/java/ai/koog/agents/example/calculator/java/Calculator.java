package ai.koog.agents.example.calculator.java;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;
import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.GraphStrategyBuilder;
import ai.koog.agents.core.agent.TypedGraphStrategyBuilder;
import ai.koog.agents.core.agent.entity.AIAgentNodeBase;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.ext.tool.AskUser;
import ai.koog.agents.ext.tool.SayToUser;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaModels.Meta;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Demonstrates how to build a graph-based calculator agent in Java using the Koog Java API:
 * <ul>
 *   <li>Defining tools via {@link CalculatorTools} (a {@code ToolSet} implementation)</li>
 *   <li>Building a graph strategy with multiple nodes and typed edges</li>
 *   <li>Handling agent events via the {@code EventHandler} feature</li>
 * </ul>
 *
 * <p>Usage:
 * <ul>
 *   <li>Run with OpenAI GPT-4o (requires {@code OPENAI_API_KEY}): {@code ./gradlew runExampleCalculatorJava}</li>
 *   <li>Run with local Ollama Llama 3.2: {@code ./gradlew runExampleCalculatorJavaLocal}</li>
 * </ul>
 */
public class Calculator {

    private static final int MAX_TOKENS = 1000;

    enum RunMode {
        LOCAL_LLAMA_3_2,
        OPEN_AI_GPT_4o
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        RunMode runMode = args.length > 0 && "local".equalsIgnoreCase(args[0])
            ? RunMode.LOCAL_LLAMA_3_2
            : RunMode.OPEN_AI_GPT_4o;
        runCalculatorExample(runMode);
    }

    private static void runCalculatorExample(RunMode runMode) {
        PromptExecutor executor = chooseExecutor(runMode);
        LLModel model = chooseModel(runMode);

        // 1. Build the tool registry with calculator tools
        ToolRegistry toolRegistry = ToolRegistry.builder()
            .tool(AskUser.INSTANCE)
            .tool(SayToUser.INSTANCE)
            .tools(new CalculatorTools())
            .build();

        // 2. Build the agent with an inline graph strategy
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("You are a calculator.")
            .maxIterations(50)
            .toolRegistry(toolRegistry)
            .<String, String>graphStrategy("calculator", (GraphStrategyBuilder builder) -> {
                TypedGraphStrategyBuilder<String, String> graph =
                    builder.withInput(String.class).withOutput(String.class);

                // --- Node definitions ---
                AIAgentNodeBase<String, List<Message.Response>> nodeCallLLM =
                    graph.node().llmRequestMultiple();

                AIAgentNodeBase<List<Message.Tool.Call>, List<ReceivedToolResult>> nodeExecuteTools =
                    graph.node().executeMultipleTools(null, true);

                AIAgentNodeBase<List<ReceivedToolResult>, List<Message.Response>> nodeSendResults =
                    graph.node().llmSendMultipleToolResults();

                // Raw List is required because Java generics don't support Class<List<T>>
                AIAgentNodeBase<List<ReceivedToolResult>, List<ReceivedToolResult>> nodeCompress =
                    (AIAgentNodeBase<List<ReceivedToolResult>, List<ReceivedToolResult>>)
                        (AIAgentNodeBase<?, ?>) graph.node().llmCompressHistory()
                            .withInput(List.class)
                            .compressionStrategy(HistoryCompressionStrategy.WholeHistory)
                            .build();

                // --- Edge definitions ---

                // start → callLLM
                graph.edge(graph.nodeStart().forwardTo(nodeCallLLM));

                // callLLM → finish (when LLM returns an assistant message)
                graph.edge(
                    nodeCallLLM.forwardTo(graph.nodeFinish())
                        .onCondition((List<Message.Response> rs) ->
                            rs.stream().anyMatch(r -> r instanceof Message.Assistant))
                        .transformed((List<Message.Response> rs) ->
                            rs.stream()
                                .filter(r -> r instanceof Message.Assistant)
                                .map(r -> ((Message.Assistant) r).getContent())
                                .findFirst().orElse(""))
                );

                // callLLM → executeTools (when LLM returns tool calls)
                graph.edge(
                    nodeCallLLM.forwardTo(nodeExecuteTools)
                        .onCondition((List<Message.Response> rs) ->
                            rs.stream().anyMatch(r -> r instanceof Message.Tool.Call))
                        .transformed((List<Message.Response> rs) ->
                            rs.stream()
                                .filter(r -> r instanceof Message.Tool.Call)
                                .map(r -> (Message.Tool.Call) r)
                                .collect(Collectors.toList()))
                );

                // executeTools → compress (when token count exceeds threshold)
                graph.edge(
                    nodeExecuteTools.forwardTo(nodeCompress)
                        .onCondition((output, ctx) ->
                            ctx.getLlm().readSession(session ->
                                session.getPrompt().latestTokenUsage() > MAX_TOKENS))
                );

                // compress → sendResults
                graph.edge(nodeCompress.forwardTo(nodeSendResults));

                // executeTools → sendResults (when token count is within threshold)
                graph.edge(
                    nodeExecuteTools.forwardTo(nodeSendResults)
                        .onCondition((output, ctx) ->
                            ctx.getLlm().readSession(session ->
                                session.getPrompt().latestTokenUsage() <= MAX_TOKENS))
                );

                // sendResults → executeTools (when LLM requests more tool calls)
                graph.edge(
                    nodeSendResults.forwardTo(nodeExecuteTools)
                        .onCondition((List<Message.Response> rs) ->
                            rs.stream().anyMatch(r -> r instanceof Message.Tool.Call))
                        .transformed((List<Message.Response> rs) ->
                            rs.stream()
                                .filter(r -> r instanceof Message.Tool.Call)
                                .map(r -> (Message.Tool.Call) r)
                                .collect(Collectors.toList()))
                );

                // sendResults → finish (when LLM returns a final assistant message)
                graph.edge(
                    nodeSendResults.forwardTo(graph.nodeFinish())
                        .onCondition((List<Message.Response> rs) ->
                            rs.stream().anyMatch(r -> r instanceof Message.Assistant))
                        .transformed((List<Message.Response> rs) ->
                            rs.stream()
                                .filter(r -> r instanceof Message.Assistant)
                                .map(r -> ((Message.Assistant) r).getContent())
                                .findFirst().orElse(""))
                );

                return graph.build();
            })
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx ->
                    System.out.println("Tool called: " + ctx.getToolName()
                                       + ", args: " + ctx.getToolArgs()));
                config.onAgentExecutionFailed(ctx ->
                    System.out.println("An error occurred: " + ctx.getThrowable().getMessage()));
                config.onAgentCompleted(ctx ->
                    System.out.println("Result: " + ctx.getResult()));
            })
            .build();

        // 3. Run the agent (blocking call via @JvmName("run"))
        String result = agent.run("(10 + 20) * (5 + 5) / (2 - 11)");
        System.out.println("Agent result: " + result);
    }

    private static PromptExecutor chooseExecutor(RunMode mode) {
        return switch (mode) {
            case LOCAL_LLAMA_3_2 -> simpleOllamaAIExecutor("http://localhost:11434");
            case OPEN_AI_GPT_4o -> {
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
                }
                yield simpleOpenAIExecutor(apiKey);
            }
        };
    }

    private static LLModel chooseModel(RunMode mode) {
        return switch (mode) {
            case LOCAL_LLAMA_3_2 -> Meta.LLAMA_3_2;
            case OPEN_AI_GPT_4o -> Chat.GPT4o;
        };
    }
}
