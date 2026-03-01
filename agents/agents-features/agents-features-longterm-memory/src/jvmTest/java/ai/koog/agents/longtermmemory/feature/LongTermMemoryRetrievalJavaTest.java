package ai.koog.agents.longtermmemory.feature;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.annotation.ExperimentalAgentsApi;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.longtermmemory.retrieval.KeywordSearchRequest;
import ai.koog.agents.longtermmemory.retrieval.RetrievalSettings;
import ai.koog.agents.longtermmemory.retrieval.SearchStrategy;
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter;
import ai.koog.agents.longtermmemory.storage.InMemoryRecordStorage;
import ai.koog.agents.testing.tools.MockExecutor;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java tests for configuring {@link LongTermMemory} retrieval settings from Java code.
 * Each test case demonstrates a different retrieval configuration using builders.
 */
@ExperimentalAgentsApi
public class LongTermMemoryRetrievalJavaTest {

    private static final ToolRegistry EMPTY_REGISTRY = ToolRegistry.builder().build();

    private AIAgent buildAgentWithRetrieval(RetrievalSettings retrievalSettings) {
        return AIAgent.builder()
            .promptExecutor(
                MockExecutor.builder()
                    .toolRegistry(EMPTY_REGISTRY)
                    .mockLLMAnswer("answer").asDefaultResponse()
                    .build()
            )
            .llmModel(OpenAIModels.Chat.GPT4o)
            .systemPrompt("system")
            .install(LongTermMemory.Feature, config ->
                config.retrieval(retrievalSettings)
            )
            .build();
    }

    @Test
    public void testKeywordSearchViaSearchStrategy() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(SearchStrategy.builder().keyword().withTopK(10).build())
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testKeywordSearchViaLambda() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(query -> new KeywordSearchRequest(query, 20, 0.0, null))
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testSimilaritySearchWithSystemPromptAugmenter() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(
                SearchStrategy.builder().similarity().withTopK(10).withSimilarityThreshold(0.7).build()
            )
            .withPromptAugmenter(PromptAugmenter.builder().system().build())
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testKeywordSearchWithUserPromptAugmenter() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(
                SearchStrategy.builder().keyword().withTopK(5).withSimilarityThreshold(0.1).build()
            )
            .withPromptAugmenter(PromptAugmenter.builder().user().build())
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testSimilaritySearchViaSearchStrategy() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(
                SearchStrategy.builder().similarity().withTopK(10).withSimilarityThreshold(0.7).build()
            )
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testKeywordSearchViaSearchStrategyWithThreshold() {
        InMemoryRecordStorage storage = new InMemoryRecordStorage();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(storage)
            .withSearchStrategy(
                SearchStrategy.builder().keyword().withTopK(5).withSimilarityThreshold(0.1).build()
            )
            .build();

        var agent = buildAgentWithRetrieval(retrievalSettings);

        String result = (String) agent.run("test query");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
