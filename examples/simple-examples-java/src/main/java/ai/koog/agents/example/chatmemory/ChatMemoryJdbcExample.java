package ai.koog.agents.example.chatmemory;

import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider;
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProviderJvm;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Demonstrates AIAgent with ChatMemory backed by a pure JDBC PostgreSQL provider.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>PostgreSQL running on localhost:5432 with database "koog"</li>
 *   <li>Environment variable OPENAI_API_KEY set</li>
 * </ul>
 *
 * <p>Quick setup with Docker:
 * <pre>
 * docker run -d --name koog-pg \
 *   -e POSTGRES_DB=koog \
 *   -e POSTGRES_PASSWORD=postgres \
 *   -p 5432:5432 \
 *   postgres:16
 * </pre>
 *
 * <p>Type {@code /bye} to exit the chat loop.
 */
public class ChatMemoryJdbcExample {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/koog";
    private static final String JDBC_USER = "postgres";
    private static final String JDBC_PASSWORD = "postgres";

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

        // 1. Set up a simple DataSource wrapping DriverManager
        DataSource dataSource = simpleDataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD);

        // 2. Create a JDBC-backed chat history provider with 24h TTL
        PostgresJdbcChatHistoryProvider historyProvider = new PostgresJdbcChatHistoryProvider(
                dataSource,
                "chat_history",
                86_400L // conversations expire after 24 hours
        );

        // 3. Run schema migration (creates table + indexes if they don't exist)
        SQLChatHistoryProviderJvm.migrateBlocking(historyProvider);

        // 4. Build the agent with ChatMemory feature
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(new MultiLLMPromptExecutor(new OpenAILLMClient(apiKey)))
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a friendly assistant. Keep your answers concise.")
                .install(ChatMemory.Feature, config -> {
                    config.chatHistoryProvider(historyProvider);
                    config.windowSize(50);
                })
                .build();

        // Chat loop
        System.out.println("Chat with JDBC-backed memory started (Java example).");
        System.out.println("History persists across restarts. Type /bye to quit.\n");

        String sessionId = "jdbc-java-conversation";
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("You: ");
            String input = scanner.nextLine().trim();
            if ("/bye".equals(input)) break;
            if (input.isEmpty()) continue;


            String reply = agent.run(input, sessionId);
            System.out.println("Assistant: " + reply + "\n");
        }

        System.out.println("Goodbye!");
    }

    /**
     * Creates a minimal {@link DataSource} backed by {@link DriverManager}.
     * For production use, prefer a connection pool like HikariCP.
     */
    private static DataSource simpleDataSource(String url, String user, String password) {
        return new DataSource() {
            @Override public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, user, password);
            }
            @Override public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(url, u, p);
            }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getLogger("DataSource"); }
            @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }
}
