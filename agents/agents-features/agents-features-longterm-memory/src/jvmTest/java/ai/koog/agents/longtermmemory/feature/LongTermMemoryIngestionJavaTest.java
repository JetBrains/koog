package ai.koog.agents.longtermmemory.feature;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.longtermmemory.ingestion.extraction.DocumentExtractor;
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage;
import ai.koog.agents.testing.tools.MockPromptExecutor;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.message.Message;
import ai.koog.rag.base.storage.search.SimilaritySearchRequest;
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
public class LongTermMemoryIngestionJavaTest {
    private static final JSONSerializer serializer = new JacksonSerializer();

    @Test
    public void testIngestionWithFilteringExtractor() {
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
                        .withDocumentExtractor(
                            DocumentExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                                .build()
                        )
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
                        .withDocumentExtractor(
                            DocumentExtractor.builder()
                                .filtering()
                                .withExtractRoles(new HashSet<>(Arrays.asList(Message.Role.User, Message.Role.Assistant)))
                                .build()
                        )
                        .build()
                );
                config.retrieval(
                    new LongTermMemory.RetrievalSettingsBuilder()
                        .withStorage(storage)
                        .withSearchStrategy(query ->
                            new SimilaritySearchRequest(query, 15, 0, 0.5, null)
                        )
                        .withFailurePolicy(FailurePolicy.LOG_AND_CONTINUE)
                        .build()
                );
            })
            .build();

        String result = (String) agent.run("Hello");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
