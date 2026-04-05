package ai.koog.agents.features.longtermmemory.aws.dsl;

import ai.koog.agents.core.annotation.ExperimentalAgentsApi;
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy;
import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest;
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver;
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceScope;
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage;
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreCompositeSearchRequest;
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreListingSearchRequest;
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSimilaritySearchRequest;
import ai.koog.agents.longtermmemory.feature.LongTermMemory;
import ai.koog.agents.longtermmemory.retrieval.augmentation.SystemPromptAugmenter;
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter;
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient;
import org.junit.jupiter.api.Test;

import static ai.koog.agents.features.longtermmemory.aws.dsl.AgentcoreJavaTestSupport.mockClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java tests for the AgentCore long-term memory retrieval Java builder façade.
 * <p>
 * These tests exercise {@link AgentcoreRetrieval#builder(BedrockAgentCoreClient, String)}
 * from Java, assert that the built {@link AgentcoreRetrievalConfig} wires correctly into
 * a {@link LongTermMemory.RetrievalSettingsBuilder}, and verify that the resulting
 * {@link AgentcoreCompositeSearchStrategy} carries the expected subrequests (namespaces,
 * strategy ids, request shapes).
 */
@ExperimentalAgentsApi
public class AgentcoreRetrievalJavaTest {

    private static final String MEMORY_ID = "mem-123";

    private static AgentcoreCompositeSearchStrategy compositeOf(AgentcoreRetrievalConfig cfg) {
        return cfg.getSearchStrategy();
    }

    private static String actorNs(String strategyId, String actorId) {
        return AgentcoreNamespaceResolver.Companion.getDefault().resolve(new AgentcoreNamespaceScope.Actor(strategyId, actorId));
    }

    private static String sessionNs(String strategyId, String actorId, String sessionId) {
        return AgentcoreNamespaceResolver.Companion.getDefault().resolve(new AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId));
    }

    // ---- basic wiring ---------------------------------------------------------

    @Test
    public void testSingleSemanticLegBuildsSimilarityLeg() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .semantic("sem-1", "alice", 5)
            .build();

        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(1, subrequests.size());
        var subrequest = subrequests.get(0);
        assertEquals(actorNs("sem-1", "alice"), subrequest.getNamespace());

        var req = subrequest.buildRequest("hello");
        var sim = assertInstanceOf(AgentcoreSimilaritySearchRequest.class, req);
        assertEquals("sem-1", sim.getMemoryStrategyId());
        assertEquals("hello", sim.getQueryText());
        assertEquals(5, sim.getLimit());
        assertNull(sim.getMinScore());
        assertNull(sim.getFilterExpression());
    }

    @Test
    public void testStorageHoldsExpectedMemoryIdAndClient() {
        var client = mockClient();
        var cfg = AgentcoreRetrieval.builder(client, MEMORY_ID)
            .semantic("sem-1", "alice")
            .build();

        AgentcoreSearchStorage storage = cfg.getStorage();
        assertEquals(MEMORY_ID, storage.getAgentcoreMemoryId());
        assertSame(client, storage.getClient());
    }

    @Test
    public void testDefaultAugmenterIsSystemPromptAugmenter() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .semantic("sem-1", "alice")
            .build();
        assertInstanceOf(SystemPromptAugmenter.class, cfg.getPromptAugmenter());
    }

    @Test
    public void testAugmenterOverride() {
        var custom = new UserPromptAugmenter();
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .augmenter(custom)
            .semantic("sem-1", "alice")
            .build();
        assertSame(custom, cfg.getPromptAugmenter());
    }

    // ---- applyTo wiring into RetrievalSettingsBuilder -------------------------

    @Test
    public void testApplyToWiresBuilderAndClearsNamespace() {
        var client = mockClient();
        var cfg = AgentcoreRetrieval.builder(client, MEMORY_ID)
            .semantic("sem-1", "alice", 5)
            .userPreferences("up-1", "alice", 20)
            .build();

        var settings = new LongTermMemory.RetrievalSettingsBuilder();
        settings.setNamespace("/should/be/overwritten/"); // pre-set; applyTo must clear to null

        cfg.applyTo(settings);

        assertSame(cfg.getStorage(), settings.getStorage());
        assertSame(cfg.getSearchStrategy(), settings.getSearchStrategy());
        assertSame(cfg.getPromptAugmenter(), settings.getPromptAugmenter());
        assertNull(settings.getNamespace());
    }

    // ---- combining multiple strategies ----------------------------------------

    @Test
    public void testSemanticPlusUserPreferencesProducesTwosubrequests() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .semantic("sem-1", "alice", 5)
            .userPreferences("up-1", "alice", 20)
            .build();

        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(2, subrequests.size());

        var sim = assertInstanceOf(AgentcoreSimilaritySearchRequest.class, subrequests.get(0).buildRequest("q"));
        assertEquals("sem-1", sim.getMemoryStrategyId());
        assertEquals(5, sim.getLimit());
        assertEquals(actorNs("sem-1", "alice"), subrequests.get(0).getNamespace());

        var listing = assertInstanceOf(AgentcoreListingSearchRequest.class, subrequests.get(1).buildRequest("q"));
        assertEquals("up-1", listing.getMemoryStrategyId());
        assertEquals(20, listing.getLimit());
        assertEquals(actorNs("up-1", "alice"), subrequests.get(1).getNamespace());
    }

    @Test
    public void testQueryIsInjectedOnCreate() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .semantic("sem-1", "alice", 5)
            .userPreferences("up-1", "alice", 20)
            .build();

        var req = compositeOf(cfg).create("my search");
        var composite = assertInstanceOf(AgentcoreCompositeSearchRequest.class, req);
        assertEquals(2, composite.getEntries().size());

        var first = assertInstanceOf(
            AgentcoreSimilaritySearchRequest.class,
            composite.getEntries().get(0).getRequest()
        );
        assertEquals("my search", first.getQueryText());

        // Listing subrequest doesn't carry the query; just verify its type.
        assertInstanceOf(AgentcoreListingSearchRequest.class, composite.getEntries().get(1).getRequest());
    }

    @Test
    public void testSemanticPropagatesMinScoreAndFilterExpression() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .semantic("sem-1", "alice", 7, 0.4, "category=news")
            .build();
        var sim = (AgentcoreSimilaritySearchRequest) compositeOf(cfg).getSubrequests().get(0).buildRequest("q");
        assertEquals(7, sim.getLimit());
        assertEquals(0.4, sim.getMinScore());
        assertEquals("category=news", sim.getFilterExpression());
    }

    // ---- EPISODIC: same strategyId + actorId, different namespaces -----------

    @Test
    public void testEpisodicAppendsSessionScopedEpisodesAndActorScopedReflections() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .episodic("ep-1", "alice", "s-1", "ep-1", /*episodesTopK*/ 4, /*reflectionsTopK*/ 2)
            .build();

        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(2, subrequests.size());

        var episodesReq = (AgentcoreSimilaritySearchRequest) subrequests.get(0).buildRequest("q");
        var reflectionsReq = (AgentcoreSimilaritySearchRequest) subrequests.get(1).buildRequest("q");

        // Same strategyId + actorId, different namespaces.
        assertEquals("ep-1", episodesReq.getMemoryStrategyId());
        assertEquals("ep-1", reflectionsReq.getMemoryStrategyId());
        assertEquals(sessionNs("ep-1", "alice", "s-1"), subrequests.get(0).getNamespace());
        assertEquals(actorNs("ep-1", "alice"), subrequests.get(1).getNamespace());
        assertNotEquals(subrequests.get(0).getNamespace(), subrequests.get(1).getNamespace());

        assertEquals(4, episodesReq.getLimit());
        assertEquals(2, reflectionsReq.getLimit());
    }

    @Test
    public void testEpisodicShortFormUsesSameStrategyIdForBothsubrequests() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .episodic("ep-1", "alice", "s-1")
            .build();
        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(2, subrequests.size());
        var episodesReq = (AgentcoreSimilaritySearchRequest) subrequests.get(0).buildRequest("q");
        var reflectionsReq = (AgentcoreSimilaritySearchRequest) subrequests.get(1).buildRequest("q");
        assertEquals("ep-1", episodesReq.getMemoryStrategyId());
        assertEquals("ep-1", reflectionsReq.getMemoryStrategyId());
    }

    @Test
    public void testEpisodesAndReflectionsMayBeAddedExplicitly() {
        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .episodes("ep-1", "alice", "s-1", 3)
            .reflections("ep-1", "alice", 2)
            .build();
        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(2, subrequests.size());
        assertEquals(sessionNs("ep-1", "alice", "s-1"), subrequests.get(0).getNamespace());
        assertEquals(actorNs("ep-1", "alice"), subrequests.get(1).getNamespace());
    }

    // ---- escape hatch: raw subrequest template --------------------------------------

    @Test
    public void testRawLegTemplateIsAppended() {
        AgentcoreSearchSubrequest template = AgentcoreSearchSubrequest.Companion.listing("raw-1", "/custom/ns/", 11);

        var cfg = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID)
            .subrequest(template)
            .build();
        var subrequests = compositeOf(cfg).getSubrequests();
        assertEquals(1, subrequests.size());
        assertEquals("/custom/ns/", subrequests.get(0).getNamespace());
        var req = assertInstanceOf(AgentcoreListingSearchRequest.class, subrequests.get(0).buildRequest("q"));
        assertEquals("raw-1", req.getMemoryStrategyId());
        assertEquals(11, req.getLimit());
    }

    // ---- validation ----------------------------------------------------------

    @Test
    public void testBlankMemoryIdFails() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentcoreRetrieval.builder(mockClient(), "  "));
    }

    @Test
    public void testEmptyBuildFails() {
        var b = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID);
        var thrown = assertThrows(IllegalStateException.class, b::build);
        assertTrue(thrown.getMessage().contains("at least one subrequest"), thrown.getMessage());
    }

    @Test
    public void testBlankStrategyIdFails() {
        var b = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID);
        assertThrows(IllegalArgumentException.class, () -> b.semantic(" ", "alice", 5));
    }

    @Test
    public void testNonPositiveTopKFails() {
        var b = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID);
        assertThrows(IllegalArgumentException.class, () -> b.semantic("sem-1", "alice", 0));
    }

    @Test
    public void testNonPositiveUserPreferencesLimitFails() {
        var b = AgentcoreRetrieval.builder(mockClient(), MEMORY_ID);
        assertThrows(IllegalArgumentException.class, () -> b.userPreferences("up-1", "alice", 0));
    }
}
