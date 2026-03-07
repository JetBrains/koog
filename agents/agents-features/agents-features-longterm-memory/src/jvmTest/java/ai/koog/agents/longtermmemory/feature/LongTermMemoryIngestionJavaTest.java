package ai.koog.agents.longtermmemory.feature;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.annotation.ExperimentalAgentsApi;
import ai.koog.agents.longtermmemory.ingestion.IngestionTiming;
import ai.koog.agents.longtermmemory.ingestion.extraction.MemoryRecordExtractor;
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest;
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage;
import ai.koog.agents.testing.tools.MockExecutorBuilder;
import ai.koog.agents.testing.tools.MockPromptExecutor;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.serialization.JSONSerializer;
import ai.koog.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java tests for configuring {@link LongTermMemory} ingestion settings from Java code.
 * Each test case demonstrates a different ingestion configuration using builders.
 */
@ExperimentalAgentsApi
public class LongTermMemoryIngestionJavaTest {
    private static final JSONSerializer serializer = new JacksonSerializer();

    @Test
    public void testIngestionWithFilteringExtractorAndOnLlmCallTiming() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var agent = AIAgent.builder()
            .promptExecutor(
                MockPromptExecutor.builder(serializer)
                    .mockLLMAnswer("The capital of France is Paris.").asDefaultResponse()
                    .build()
            )
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("You are a helpful assistant.")
            .install(LongTermMemory.Feature, config ->
                config.ingestion(
                    new LongTermMemory.IngestionSettingsBuilder()
                        .withStorage(storage)
                        .withExtractor(
                            MemoryRecordExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                                .withLastMessageOnly(false)
                                .build()
                        )
                        .withTiming(IngestionTiming.ON_LLM_CALL)
                        .build()
                )
            )
            .build();

        String result = (String) agent.run("What is the capital of France?");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testIngestionWithFilteringExtractorAndOnAgentCompletionTiming() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var agent = AIAgent.builder()
            .promptExecutor(
                MockPromptExecutor.builder(serializer)
                    .mockLLMAnswer("answer").asDefaultResponse()
                    .build()
            )
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("You are a helpful assistant.")
            .install(LongTermMemory.Feature, config ->
                config.ingestion(
                    new LongTermMemory.IngestionSettingsBuilder()
                        .withStorage(storage)
                        .withExtractor(
                            MemoryRecordExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                                .build()
                        )
                        .withTiming(IngestionTiming.ON_AGENT_COMPLETION)
                        .build()
                )
            )
            .build();

        String result = (String) agent.run("Hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testIngestionWithLastMessageOnlyExtractor() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var agent = AIAgent.builder()
            .promptExecutor(
                MockPromptExecutor.builder(serializer)
                    .mockLLMAnswer("answer").asDefaultResponse()
                    .build()
            )
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("You are a helpful assistant.")
            .install(LongTermMemory.Feature, config ->
                config.ingestion(
                    new LongTermMemory.IngestionSettingsBuilder()
                        .withStorage(storage)
                        .withExtractor(
                            MemoryRecordExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.Assistant)))
                                .withLastMessageOnly(true)
                                .build()
                        )
                        .withTiming(IngestionTiming.ON_LLM_CALL)
                        .build()
                )
            )
            .build();

        String result = (String) agent.run("Hello");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testFullConfigurationWithIngestionAndRetrieval() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var agent = AIAgent.builder()
            .promptExecutor(
                MockPromptExecutor.builder(serializer)
                    .mockLLMAnswer("full config answer").asDefaultResponse()
                    .build()
            )
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("You are a helpful assistant.")
            .install(LongTermMemory.Feature, config -> {
                config.ingestion(
                    new LongTermMemory.IngestionSettingsBuilder()
                        .withStorage(storage)
                        .withExtractor(
                            MemoryRecordExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                                .build()
                        )
                        .withTiming(IngestionTiming.ON_AGENT_COMPLETION)
                        .build()
                );
                config.retrieval(
                    new LongTermMemory.RetrievalSettingsBuilder()
                        .withStorage(storage)
                        .withSearchStrategy(query ->
                            new KeywordSearchRequest(query, 15, 0.5, null)
                        )
                        .build()
                );
            })
            .build();

        String result = (String) agent.run("Hello");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
