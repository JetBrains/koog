package ai.koog.agents.example.snapshot;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider;
import ai.koog.agents.snapshot.feature.Persistence;
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
 * Demonstrates AIAgent with the Persistence feature backed by a pure JDBC PostgreSQL provider.
 *
 * <p>The Persistence feature automatically creates checkpoints after each node execution,
 * allowing the agent to resume from where it left off across restarts.
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
public class PersistenceJdbcExample {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/koog";
    private static final String JDBC_USER = "postgres";
    private static final String JDBC_PASSWORD = "postgres";

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

        PostgresJdbcPersistenceStorageProvider storageProvider = new PostgresJdbcPersistenceStorageProvider(
            simpleDataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD)
        );

        storageProvider.migrateBlocking();

        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(new MultiLLMPromptExecutor(new OpenAILLMClient(apiKey)))
                .llmModel(OpenAIModels.Chat.GPT4o)
                .systemPrompt("You are a friendly assistant. Keep your answers concise.")
                .install(Persistence.Feature, config -> {
                    config.setStorage(storageProvider);
                    config.setEnableAutomaticPersistence(true);
                })
                .build();

        System.out.println("Agent with JDBC persistence started (Java example).");
        System.out.println("Checkpoints are saved to PostgreSQL. Type /bye to quit.\n");

        String sessionId = "jdbc-persistent-agent";
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
