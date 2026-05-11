package org.example.koog.java.agents;

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider;
import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.ext.agent.CriticResult;
import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider;
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProviderJvm;
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider;
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory;
import ai.koog.agents.memory.model.Concept;
import ai.koog.agents.memory.model.FactType;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.example.koog.java.structs.AccountIssueSolution;
import org.example.koog.java.structs.AccountIssueSummary;
import org.example.koog.java.structs.UserResponse;
import org.example.koog.java.tools.AccountReadTools;
import org.example.koog.java.tools.AccountWriteTools;
import org.example.koog.java.tools.CommunicationTools;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

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

    public KoogAgentService(
            MultiLLMPromptExecutor promptExecutor,
            List<SpanExporter> spanExporters
    ) {
        this.promptExecutor = promptExecutor;
    }

    /**
     * Starts an agent asynchronously and returns its id immediately.
     */
    public String launchSupportAgent(String userSessionId, String question) {
        var agent = createAgent(userSessionId);

        return agent.run(question, userSessionId).actionsTaken();
    }

    // sessionId -> set of agentIds (Boolean.TRUE to emulate Kotlin's Unit placeholder)
    private final ConcurrentMap<String, AIAgent<String, AccountIssueSolution>> agentByUser = new ConcurrentHashMap<>();

    // Lazily initialized Postgres storage created by Kotlin top-level function.
    // Replace the type with the concrete storage interface/class used by Persistence.storage.
    private volatile Object postgresStorage;

    // Main executor service for agents and non-LLM strategy parts (tools, orchestration):
    private final ExecutorService agentStrategyExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r);
        t.setName("agents");
        t.setDaemon(true);
        return t;
    });

    // ExecutorService for IO/LLM operations:
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r);
        t.setName("llm");
        t.setDaemon(true);
        return t;
    });

    private static DataSource createDataSource(String url, String user, String password) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, user, password);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(url, u, p);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getLogger("DataSource");
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    private ChatHistoryProvider getHistoryProvider() {
        // 1. Set up a simple DataSource wrapping DriverManager
        DataSource dataSource = createDataSource("JDBC_URL", "JDBC_USER", "JDBC_PASSWORD");

        // 2. Create a JDBC-backed chat history provider with 24h TTL
        var historyProvider = new PostgresJdbcChatHistoryProvider(
                dataSource,
                "chat_history",
                86_400L // conversations expire after 24 hours
        );

        // 3. Run schema migration (creates table + indexes if they don't exist)
        SQLChatHistoryProviderJvm.migrateBlocking(historyProvider);

        return historyProvider;
    }

    private AIAgent<String, AccountIssueSolution> createAgent(String userId) {
        var existingAgent = agentByUser.get(userId);
        if (existingAgent != null) {
            return existingAgent;
        }

        var communicationTools = new CommunicationTools();
        var databaseReadTools = new AccountReadTools(userId);
        var databaseWriteTools = new AccountWriteTools(userId);

        var strategy = createStrategy(communicationTools, databaseReadTools, databaseWriteTools);

        var persistenceStorage = new PostgresJdbcPersistenceStorageProvider(
                createDataSource("JDBC_URL", "JDBC_USER", "JDBC_PASSWORD")
        );

        var historyProvider = getHistoryProvider();

        var toolRegistry = ToolRegistry.builder()
                .tools(communicationTools)
                .tools(databaseReadTools)
                .tools(databaseWriteTools)
                .build();

        var agent = AIAgent.builder()
                .promptExecutor(promptExecutor)
                .llmModel(OpenAIModels.Chat.GPT5_2)
                .toolRegistry(toolRegistry)
                .agentConfig(
                        AIAgentConfig.builder()
                                .model(OllamaModels.Meta.LLAMA_3_2)
                                .prompt(
                                        Prompt.builder("agent-prompt")
                                                .system("You are a banking assistant responsible for helping users with their orders")
                                                .build()
                                )
                                .maxAgentIterations(200)
                                .strategyExecutorService(agentStrategyExecutor)
                                .llmRequestExecutorService(ioExecutor)
                                .build()
                )
                .graphStrategy(strategy)
                .install(ChatMemory.Feature, config -> {
                    config.chatHistoryProvider(historyProvider);
                    config.windowSize(50);
                })
                .install(Persistence.Feature, config -> {
                    config.setStorage(persistenceStorage);
                    config.setEnableAutomaticPersistence(true);
                })
                .build();

        agentByUser.put(userId, agent);

        return agent;
    }

    private AIAgentGraphStrategy<String, AccountIssueSolution> createStrategy(
            ToolSet communicationTools,
            ToolSet databaseReadTools,
            ToolSet databaseWriteTools
    ) {
        var identifyProblem = AIAgentSubgraph.builder()
                .withInput(String.class)
                .withOutput(AccountIssueSummary.class)
                .limitedTools(communicationTools, databaseReadTools)
                .withTask(input -> "Identify the problem with the user and formulate a problem description")
                .usingLLM(OpenAIModels.Chat.GPT5_2)
                .build();

        var fixProblem = AIAgentSubgraph.builder()
                .withInput(AccountIssueSummary.class)
                .withOutput(AccountIssueSolution.class)
                .limitedTools(databaseReadTools, databaseWriteTools)
                .withTask(input -> "Now solve the user's problem")
                .usingLLM(AnthropicModels.Sonnet_4)
                .build();

        var verifySolution = AIAgentSubgraph.builder()
                .withInput(AccountIssueSolution.class)
                .withVerification(solution -> "Now verify that the problem is actually solved!")
                .limitedTools(communicationTools, databaseReadTools)
                .usingLLM(OpenAIModels.Chat.O3)
                .build();

        // TODO: Unused example node -- feel free to put anywhere in the strategy
        var askUserConfirmation = AIAgentNode.builder()
                .withInput(AccountIssueSolution.class)
                .withOutput(UserResponse.class)
                .withAction((input, ctx) -> {
                    // TODO: send via HTTTP and present solution in UI
                    System.out.println("What do you think about this solution:" + input.actionsTaken());
                    // TODO: read user's response and parse
                    return new UserResponse(true, "I think this solution is good");
                })
                .build();

        var compressHistory = AIAgentNode
                .llmCompressHistory()
                .withInput(AccountIssueSolution.class)
                .compressionStrategy(new RetrieveFactsFromHistory(
                        new Concept(
                                "important-steps",
                                "What steps were important to fix the problem",
                                FactType.MULTIPLE
                        ),
                        new Concept(
                                "suspicious-operation",
                                "What banking operations were suspicious",
                                FactType.MULTIPLE
                        )
                ))
                .build();

        var adjust = AIAgentSubgraph.builder()
                .withInput(String.class)
                .withOutput(AccountIssueSolution.class)
                .limitedTools(databaseReadTools, databaseWriteTools)
                .withTask(feedback ->
                        "Fix the solution based on the provided feedback: " + feedback)
                .usingLLM(AnthropicModels.Sonnet_4_5)
                .build();

        var graph = AIAgentGraphStrategy.builder()
                .withInput(String.class)
                .withOutput(AccountIssueSolution.class);

        graph.edge(graph.nodeStart, identifyProblem);
        graph.edge(identifyProblem, fixProblem);
        graph.edge(fixProblem, verifySolution);

        graph.edge(AIAgentEdge.builder()
                .from(verifySolution)
                .to(adjust)
                .onCondition(verification -> !verification.isSuccessful())
                .transformed(CriticResult::getFeedback)
                .build()
        );

        graph.edge(AIAgentEdge.builder()
                .from(verifySolution)
                .to(graph.nodeFinish)
                .onCondition(CriticResult::isSuccessful)
                .transformed(CriticResult::getInput)
                .build());

        graph.edge(
                AIAgentEdge.builder()
                        .from(adjust)
                        .to(compressHistory)
                        .onCondition(this::historyIsTooLong)
                        .build()
        );
        graph.edge(compressHistory, verifySolution);
        graph.edge(
                AIAgentEdge.builder()
                        .from(adjust)
                        .to(verifySolution)
                        .onCondition((input, ctx) -> !historyIsTooLong(input, ctx))
                        .build()
        );

        return graph.build();
    }

    private boolean historyIsTooLong(AccountIssueSolution input, AIAgentGraphContextBase context) {
        return context.getLlm().readSession(session ->
                session.getPrompt().latestTokenUsage() > 100500
        );
    }
}
