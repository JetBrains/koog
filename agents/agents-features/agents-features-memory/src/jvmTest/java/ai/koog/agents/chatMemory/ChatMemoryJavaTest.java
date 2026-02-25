package ai.koog.agents.chatMemory;

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider;
import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.chatMemory.feature.ChatMemoryConfig;
import ai.koog.agents.chatMemory.feature.ChatMemoryPreProcessor;
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider;
import ai.koog.agents.chatMemory.feature.WindowSizePreProcessor;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.testing.tools.MockLLMBuilder;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.message.Message;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java integration tests for the ChatMemory feature.
 * <p>
 * Demonstrates how to use ChatMemory, WindowSizePreProcessor,
 * filterMessages, and custom ChatMemoryPreProcessor implementations from Java.
 */
class ChatMemoryJavaTest {

    /**
     * Helper to call a suspend function from Java using runBlocking.
     */
    @SuppressWarnings("unchecked")
    private List<Message> loadHistory(ChatHistoryProvider provider, String sessionId) {
        try {
            return (List<Message>) BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> provider.load(sessionId, continuation)
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a mock PromptExecutor from Java using MockLLMBuilder directly.
     */
    private PromptExecutor createMockExecutor(java.util.function.Consumer<MockLLMBuilder> configure) {
        return JavaTestHelpers.createMockExecutor(configure);
    }

    /**
     * Creates an agent with ChatMemory installed using the builder API.
     */
    private AIAgent<String, String> createAgentWithChatMemory(
            PromptExecutor executor,
            ChatHistoryProvider historyProvider,
            java.util.function.Consumer<ChatMemoryConfig> configureMemory
    ) {
        return AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(OpenAIModels.Chat.GPT4oMini)
                .systemPrompt("You are a helpful assistant.")
                .maxIterations(10)
                .install(ChatMemory.Feature, config -> {
                    config.chatHistoryProvider(historyProvider);
                    configureMemory.accept(config);
                })
                .build();
    }

    // ---- Basic usage ----

    @Test
    void testBasicChatMemoryFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Paris is the capital.").onRequestContains("France");
            builder.mockLLMAnswer("Berlin is the capital.").onRequestContains("Germany");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider, config -> {});

        String firstResult = agent.run("What is the capital of France?", "session-1");
        assertEquals("Paris is the capital.", firstResult);

        String secondResult = agent.run("What is the capital of Germany?", "session-1");
        assertEquals("Berlin is the capital.", secondResult);
    }

    // ---- Window size via config DSL ----

    @Test
    void testWindowSizeFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("Reply 3").onRequestContains("Q3");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Use windowSize(4) to keep only the last 4 messages
        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config.windowSize(4));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");
        agent.run("Q3", "session-1");

        // After 3 runs (6 messages), window of 4 should drop Q1 + Reply 1
        List<Message> saved = loadHistory(historyProvider, "session-1");
        assertEquals(4, saved.size(), "Window should keep exactly 4 messages");

        List<String> contents = saved.stream().map(Message::getContent).collect(Collectors.toList());
        assertTrue(contents.stream().noneMatch(c -> c.contains("Q1")), "Q1 should be outside the window");
        assertTrue(contents.stream().anyMatch(c -> c.contains("Q2")), "Q2 should be within the window");
        assertTrue(contents.stream().anyMatch(c -> c.contains("Q3")), "Q3 should be within the window");
    }

    // ---- WindowSizePreProcessor directly ----

    @Test
    void testWindowSizePreProcessorDirectlyFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Install WindowSizePreProcessor explicitly via addPreProcessor
        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config.addPreProcessor(new WindowSizePreProcessor(2)));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");

        List<Message> saved = loadHistory(historyProvider, "session-1");
        assertEquals(2, saved.size(), "Only the last 2 messages should be stored");
    }

    // ---- Custom preprocessor ----

    @Test
    void testCustomPreProcessorFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Custom preprocessor that filters out assistant messages containing "Reply 1"
        ChatMemoryPreProcessor filterProcessor = new ChatMemoryPreProcessor() {
            @NotNull
            @Override
            public List<Message> preprocess(@NotNull List<? extends Message> messages) {
                return messages.stream()
                        .filter(m -> !m.getContent().contains("Reply 1"))
                        .collect(Collectors.toList());
            }
        };

        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config.addPreProcessor(filterProcessor));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");

        List<Message> saved = loadHistory(historyProvider, "session-1");
        List<String> contents = saved.stream().map(Message::getContent).collect(Collectors.toList());
        assertTrue(contents.stream().noneMatch(c -> c.contains("Reply 1")),
                "Reply 1 should have been filtered out by the custom preprocessor");
        assertTrue(contents.stream().anyMatch(c -> c.contains("Q2")),
                "Q2 should still be present");
    }

    // ---- filterMessages DSL ----

    @Test
    void testFilterMessagesFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // filterMessages with a Java lambda — MessageFilter is a functional interface
        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config.filterMessages(message -> message instanceof Message.User));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");

        List<Message> saved = loadHistory(historyProvider, "session-1");
        assertTrue(saved.stream().allMatch(m -> m instanceof Message.User),
                "Only user messages should remain after filterMessages");
    }

    @Test
    void testFilterMessagesThenWindowSizeFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("Reply 3").onRequestContains("Q3");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Filter to user-only first, then window of 2 from those
        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config
                        .filterMessages(message -> message instanceof Message.User)
                        .windowSize(2));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");
        agent.run("Q3", "session-1");

        List<Message> saved = loadHistory(historyProvider, "session-1");
        assertEquals(2, saved.size(), "Exactly 2 messages after filter-then-window");
        assertTrue(saved.stream().allMatch(m -> m instanceof Message.User),
                "All should be User messages");
        assertTrue(saved.stream().noneMatch(m -> m.getContent().contains("Q1")),
                "Q1 should be outside the window");
        assertTrue(saved.stream().anyMatch(m -> m.getContent().contains("Q2")),
                "Q2 should be in the window");
        assertTrue(saved.stream().anyMatch(m -> m.getContent().contains("Q3")),
                "Q3 should be in the window");
    }

    @Test
    void testWindowSizeThenFilterMessagesFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Reply 1").onRequestContains("Q1");
            builder.mockLLMAnswer("Reply 2").onRequestContains("Q2");
            builder.mockLLMAnswer("Reply 3").onRequestContains("Q3");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Window of 4 first, then filter to user-only
        // Since preprocessors run at both load AND store, the filter at store-time
        // means only User messages are persisted, keeping the list short enough
        // that the window rarely trims.
        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider,
                config -> config
                        .windowSize(4)
                        .filterMessages(message -> message instanceof Message.User));

        agent.run("Q1", "session-1");
        agent.run("Q2", "session-1");
        agent.run("Q3", "session-1");

        List<Message> saved = loadHistory(historyProvider, "session-1");
        // All 3 user messages fit because filtering keeps the list short
        assertTrue(saved.stream().allMatch(m -> m instanceof Message.User),
                "All saved messages should be User");
        assertEquals(3, saved.size(), "All 3 user messages should be kept");
    }

    // ---- Session isolation ----

    @Test
    void testSessionIsolationFromJava() {
        InMemoryChatHistoryProvider historyProvider = new InMemoryChatHistoryProvider();
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("Paris").onRequestContains("France");
            builder.mockLLMAnswer("Tokyo").onRequestContains("Japan");
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        AIAgent<String, String> agent = createAgentWithChatMemory(executor, historyProvider, config -> {});

        agent.run("France?", "session-A");
        agent.run("Japan?", "session-B");

        List<Message> savedA = loadHistory(historyProvider, "session-A");
        List<Message> savedB = loadHistory(historyProvider, "session-B");

        assertTrue(savedA.stream().anyMatch(m -> m.getContent().contains("France")),
                "Session A should contain France");
        assertTrue(savedA.stream().noneMatch(m -> m.getContent().contains("Japan")),
                "Session A should not contain Japan");

        assertTrue(savedB.stream().anyMatch(m -> m.getContent().contains("Japan")),
                "Session B should contain Japan");
        assertTrue(savedB.stream().noneMatch(m -> m.getContent().contains("France")),
                "Session B should not contain France");
    }

    // ---- Builder API with install ----

    @Test
    void testBuilderApiInstallFromJava() {
        PromptExecutor executor = createMockExecutor(builder -> {
            builder.mockLLMAnswer("mock reply").getAsDefaultResponse();
        });

        // Demonstrate the full builder API chain from Java with fluent config
        AIAgent<String, String> agent = AIAgent.builder()
                .promptExecutor(executor)
                .llmModel(OpenAIModels.Chat.GPT4oMini)
                .systemPrompt("You are a helpful assistant.")
                .maxIterations(10)
                .install(ChatMemory.Feature, config -> config
                        .chatHistoryProvider(new InMemoryChatHistoryProvider())
                        .windowSize(20))
                .build();

        String result = agent.run("Hello", "session-1");
        assertEquals("mock reply", result);
    }
}
