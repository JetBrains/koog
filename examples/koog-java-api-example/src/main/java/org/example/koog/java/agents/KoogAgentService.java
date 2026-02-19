package org.example.koog.java.agents;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.AIAgentState;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.example.koog.java.structs.OrderSupportRequest;
import org.example.koog.java.structs.OrderUpdateSummary;
import org.example.koog.java.tools.UserTools;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Java conversion of KoogAgentService.
 * <p>
 * Notes:
 * - Kotlin suspend interop: the original methods were suspend. This Java version runs the agent
 * logic asynchronously on a fixed thread pool and returns immediately where applicable.
 * - AIAgent construction uses a Kotlin-first DSL in the original code. The creation block is marked
 * with TODOs so you can connect it to the Java-accessible API (constructor or builder).
 * - createPostgresStorage() is a Kotlin top-level function. Replace the "Kt" class name below with
 * the actual one generated from the Kotlin file name that defines createPostgresStorage().
 */
@Service
public class KoogAgentService {

    private final MultiLLMPromptExecutor promptExecutor;
    private final List<SpanExporter> spanExporters;

    // userId -> set of agentIds (Boolean.TRUE to emulate Kotlin's Unit placeholder)
    private final ConcurrentMap<String, ConcurrentMap<String, Boolean>> agentIdsByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AIAgent<String, OrderUpdateSummary>> agentsById = new ConcurrentHashMap<>();

    // Lazily initialized Postgres storage created by Kotlin top-level function.
    // Replace the type with the concrete storage interface/class used by Persistence.storage.
    private volatile Object postgresStorage;

    // Fixed thread pool similar to Kotlin newFixedThreadPoolContext
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r);
        t.setName("agents");
        t.setDaemon(true);
        return t;
    });

    public KoogAgentService(
            MultiLLMPromptExecutor promptExecutor,
            List<SpanExporter> spanExporters
    ) {
        this.promptExecutor = Objects.requireNonNull(promptExecutor, "promptExecutor");
        this.spanExporters = Objects.requireNonNull(spanExporters, "spanExporters");
    }

    private AIAgent<String, OrderUpdateSummary> createAgent(String userId) {
        UserTools userTools = new UserTools(userId);

        List<Tool<?, ?>> readingTools = List.of(
                userTools.getTool("readUserAccount"),
                userTools.getTool("readUserOrders")
        );

        var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .agentConfig(
                        AIAgentConfig.builder(OpenAIModels.Chat.GPT4_1)
                                .prompt(Prompt.builder("id")
                                        .system("system")
                                        .user("user")
                                        .assistant("assistant")
                                        .user("user")
                                        .assistant("assistant")
                                        .toolCall("id-1", "tool-1", "args-1")
                                        .toolResult("id-1", "tool-1", "result-1")
                                        .toolCall("id-2", "tool-2", "args--2")
                                        .toolResult("id-2", "tool-2", "result-2")
                                        .build()
                                )
                                .maxAgentIterations(100)
                                .strategyExecutorService(Executors.newSingleThreadExecutor()) // TODO: use better executor service
                                .llmRequestExecutorService(Executors.newSingleThreadExecutor()) // TODO: use better executor service
                                .build()
                )
                .functionalStrategy((AIAgentFunctionalContext context, String userInput) -> {
                    var orderRequest = context
                            .subtask("Identify the user's problem. Input: " + userInput + ".")
                            .withInput(userInput)
                            .withOutput(OrderSupportRequest.class)
                            .withTools(readingTools)
                            .useLLM(OpenAIModels.Chat.GPT4o)
                            .run();

                    if (orderRequest.isResolved()) {
                        return orderRequest.emptyUpdate();
                    }

                    var update = context
                            .subtask("Solve the identified user's problem")
                            .withInput(orderRequest)
                            .withOutput(OrderUpdateSummary.class)
                            .withTools(userTools.asTools())
                            .useLLM(AnthropicModels.Sonnet_4)
                            .run();

                    while (true) {
                        var verification = context
                                .subtask("Verify if the initial user's problem was solved")
                                .withInput(update)
                                .withVerification()
                                .withTools(readingTools)
                                .useLLM(OpenAIModels.Chat.O3)
                                .run();

                        if (verification.getSuccessful()) {
                            return update;
                        } else {
                            update = context
                                    .subtask("You must resolve the following issues: " + verification.getFeedback())
                                    .withInput(orderRequest)
                                    .withOutput(OrderUpdateSummary.class)
                                    .useLLM(AnthropicModels.Sonnet_4_5)
                                    .withTools(userTools.asTools())
                                    .run();
                        }
                    }

                })
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(userTools)
                                .build()
                )
//                TODO: migrate to graphs
//                .install(Persistence.Feature, config -> {
//                    config.setEnableAutomaticPersistence(true);
//                    config.setRollbackToolRegistry(
//                            RollbackToolRegistry.builder()
//                                    .registerRollback(new MyTool(), new MyTool())
//                                    .registerRollbacks(userTools, rollbackTools)
//                                    .build()
//                    );
//                })
//                .install(OpenTelemetry.Feature, config -> {
//                    config.setVerbose(true);
//                    config.addSpanExporter(null);
//                })
                .install(EventHandler.Feature, config -> {
                    config.onToolCallStarting(ctx -> {
                        System.out.println("tool called: " + ctx.getToolName());
                    });
                    config.onAgentClosing(ctx -> {
                        System.out.println("agent finishing: " + ctx.getAgentId());
                    });
                })
                .build();

        agentIdsByUser
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(agent.getId(), Boolean.TRUE);

        agentsById.put(agent.getId(), agent);
        return agent;
    }

    /**
     * Starts an agent asynchronously and returns its id immediately.
     */
    public String launchSupportAgent(String userId, String question) {
        AIAgent<String, OrderUpdateSummary> agent = createAgent(userId);
        parallelExecutor.submit(() -> {
            try {
                // If agent.run is suspend in Kotlin, wrap it with runBlocking in a small Kotlin helper,
                // or expose a Java-friendly run method.
                agent.run(question);
            } catch (Throwable t) {
                // Log the failure as appropriate for your project
            }
        });
        return agent.getId();
    }

    public AIAgentState<OrderUpdateSummary> getState(String agentId) throws Exception {
        AIAgent<String, OrderUpdateSummary> agent = agentsById.get(agentId);
        if (agent == null) {
            throw new Exception("Agent with id = `" + agentId + "` not found");
        }
        // If getState is suspend, expose a Java-friendly wrapper or call via runBlocking.
        return agent.getState();
    }

    public List<String> getAgentIds(String userId) {
        ConcurrentMap<String, Boolean> ids = agentIdsByUser.get(userId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(ids.keySet());
    }
}