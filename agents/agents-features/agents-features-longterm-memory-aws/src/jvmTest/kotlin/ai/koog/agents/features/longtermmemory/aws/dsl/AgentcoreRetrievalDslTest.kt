package ai.koog.agents.features.longtermmemory.aws.dsl

import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceScope
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcorePromptAugmenter
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreCompositeSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreListingSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSimilaritySearchRequest
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the AgentCore long-term memory retrieval DSL.
 *
 * The DSL always builds an [AgentcoreCompositeSearchStrategy]; these tests drive the
 * DSL directly against a [LongTermMemory.RetrievalSettingsBuilder] and inspect the
 * resulting strategy / storage / augmenter / namespace without talking to AWS.
 */
class AgentcoreRetrievalDslTest {

    private val client = mockk<BedrockAgentCoreClient>(relaxed = true)
    private val memoryId = "mem-123"

    private fun configure(block: AgentcoreRetrievalBuilder.() -> Unit): LongTermMemory.RetrievalSettingsBuilder {
        val settings = LongTermMemory.RetrievalSettingsBuilder()
        settings.agentcore(client, memoryId = memoryId, block = block)
        return settings
    }

    private fun compositeOf(settings: LongTermMemory.RetrievalSettingsBuilder): AgentcoreCompositeSearchStrategy {
        val strategy = settings.searchStrategy
        assertIs<AgentcoreCompositeSearchStrategy>(strategy)
        return strategy
    }

    private fun actorNs(strategyId: String, actorId: String): String =
        AgentcoreNamespaceResolver.Default.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))

    private fun sessionNs(strategyId: String, actorId: String, sessionId: String): String =
        AgentcoreNamespaceResolver.Default.resolve(AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId))

    // ---- Basic wiring ----

    @Test
    fun testSingleSemanticLegProducesOneSimilarityLeg() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice", topK = 5)
        }

        val strategy = compositeOf(settings)
        assertEquals(1, strategy.subrequests.size)

        val subrequest = strategy.subrequests[0]
        assertEquals(actorNs("sem-1", "alice"), subrequest.namespace)

        val req = subrequest.buildRequest("hello world")
        assertIs<AgentcoreSimilaritySearchRequest>(req)
        assertEquals("sem-1", req.memoryStrategyId)
        assertEquals("hello world", req.queryText)
        assertEquals(5, req.limit)
        assertNull(req.minScore)
        assertNull(req.filterExpression)
    }

    @Test
    fun testStorageIsAgentcoreSearchStorageWithExpectedMemoryId() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice")
        }
        val storage = settings.storage
        assertIs<AgentcoreSearchStorage>(storage)
        assertEquals(memoryId, storage.agentcoreMemoryId)
        assertSame(client, storage.client)
    }

    @Test
    fun testTopLevelNamespaceIsNullAndsubrequestsCarryTheirOwn() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice")
            userPreferences(strategyId = "up-1", actorId = "alice")
        }
        assertNull(settings.namespace)
    }

    @Test
    fun testDefaultAugmenterIsAgentcorePromptAugmenter() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice")
        }
        assertIs<AgentcorePromptAugmenter>(settings.promptAugmenter)
    }

    @Test
    fun testAugmenterOverride() {
        val custom = UserPromptAugmenter()
        val settings = configure {
            augmenter = custom
            semantic(strategyId = "sem-1", actorId = "alice")
        }
        assertSame(custom, settings.promptAugmenter)
    }

    // ---- Combining multiple strategies ----

    @Test
    fun testSemanticPlusUserPreferencesProducesTwosubrequests() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice", topK = 5)
            userPreferences(strategyId = "up-1", actorId = "alice", limit = 20)
        }

        val subrequests = compositeOf(settings).subrequests
        assertEquals(2, subrequests.size)

        val similarityReq = subrequests[0].buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(similarityReq)
        assertEquals("sem-1", similarityReq.memoryStrategyId)
        assertEquals(5, similarityReq.limit)
        assertEquals(actorNs("sem-1", "alice"), subrequests[0].namespace)

        val listingReq = subrequests[1].buildRequest("q")
        assertIs<AgentcoreListingSearchRequest>(listingReq)
        assertEquals("up-1", listingReq.memoryStrategyId)
        assertEquals(20, listingReq.limit)
        assertEquals(actorNs("up-1", "alice"), subrequests[1].namespace)
    }

    @Test
    fun testLegOrderIsPreserved() {
        val settings = configure {
            userPreferences(strategyId = "up-1", actorId = "alice")
            semantic(strategyId = "sem-1", actorId = "alice")
            summary(strategyId = "sum-1", actorId = "alice", sessionId = "s-1")
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals(3, subrequests.size)
        assertIs<AgentcoreListingSearchRequest>(subrequests[0].buildRequest("q"))
        assertEquals("up-1", (subrequests[0].buildRequest("q") as AgentcoreListingSearchRequest).memoryStrategyId)
        assertEquals("sem-1", (subrequests[1].buildRequest("q") as AgentcoreSimilaritySearchRequest).memoryStrategyId)
        assertEquals("sum-1", (subrequests[2].buildRequest("q") as AgentcoreSimilaritySearchRequest).memoryStrategyId)
    }

    // ---- Per-leg parameter propagation ----

    @Test
    fun testSemanticPropagatesMinScoreAndFilterExpression() {
        val settings = configure {
            semantic(
                strategyId = "sem-1",
                actorId = "alice",
                topK = 7,
                minScore = 0.4,
                filterExpression = "category=news",
            )
        }
        val req = compositeOf(settings).subrequests.single().buildRequest("query")
        assertIs<AgentcoreSimilaritySearchRequest>(req)
        assertEquals(7, req.limit)
        assertEquals(0.4, req.minScore)
        assertEquals("category=news", req.filterExpression)
    }

    @Test
    fun testSummaryUsesSessionScopedNamespace() {
        val settings = configure {
            summary(strategyId = "sum-1", actorId = "alice", sessionId = "s-42", topK = 3)
        }
        val subrequest = compositeOf(settings).subrequests.single()
        assertEquals(
            sessionNs("sum-1", "alice", "s-42"),
            subrequest.namespace,
        )
        val req = subrequest.buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(req)
        assertEquals(3, req.limit)
    }

    @Test
    fun testQueryIsInjectedIntoEverySimilarityLegOnCreate() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice")
            summary(strategyId = "sum-1", actorId = "alice", sessionId = "s-1")
            userPreferences(strategyId = "up-1", actorId = "alice") // listing, ignores query
        }
        val composite = compositeOf(settings).create("my search")
        assertIs<AgentcoreCompositeSearchRequest>(composite)
        assertEquals(3, composite.entries.size)

        val sim0 = composite.entries[0].request
        val sim1 = composite.entries[1].request
        val list2 = composite.entries[2].request

        assertIs<AgentcoreSimilaritySearchRequest>(sim0)
        assertEquals("my search", sim0.queryText)
        assertIs<AgentcoreSimilaritySearchRequest>(sim1)
        assertEquals("my search", sim1.queryText)
        assertIs<AgentcoreListingSearchRequest>(list2)
    }

    // ---- EPISODIC: same strategyId + actorId, different namespaces ----

    @Test
    fun testEpisodicAppendsSessionScopedEpisodesAndActorScopedReflections() {
        val settings = configure {
            episodic(
                strategyId = "ep-1",
                actorId = "alice",
                sessionId = "s-1",
                episodesTopK = 4,
                reflectionsTopK = 2,
            )
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals(2, subrequests.size)

        // episodes subrequest: session-scoped, similarity, strategyId=ep-1
        assertEquals(sessionNs("ep-1", "alice", "s-1"), subrequests[0].namespace)
        val episodesReq = subrequests[0].buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(episodesReq)
        assertEquals("ep-1", episodesReq.memoryStrategyId)
        assertEquals(4, episodesReq.limit)

        // reflections subrequest: actor-scoped, similarity, SAME strategyId=ep-1 by default
        assertEquals(actorNs("ep-1", "alice"), subrequests[1].namespace)
        val reflectionsReq = subrequests[1].buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(reflectionsReq)
        assertEquals("ep-1", reflectionsReq.memoryStrategyId)
        assertEquals(2, reflectionsReq.limit)

        // Both subrequests share memoryStrategyId + actorId but differ in namespace scope.
        assertEquals(episodesReq.memoryStrategyId, reflectionsReq.memoryStrategyId)
        assertTrue(subrequests[0].namespace != subrequests[1].namespace)
    }

    @Test
    fun testEpisodicAllowsOverridingReflectionsStrategyId() {
        val settings = configure {
            namespaceResolver
            episodic(
                strategyId = "ep-1",
                actorId = "alice",
                sessionId = "s-1",
                reflectionsStrategyId = "refl-1",
            )
        }
        val subrequests = compositeOf(settings).subrequests
        val reflectionsReq = subrequests[1].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals("refl-1", reflectionsReq.memoryStrategyId)
        assertEquals(actorNs("refl-1", "alice"), subrequests[1].namespace)
    }

    @Test
    fun testEpisodesAndReflectionssubrequestsMayAlsoBeAddedExplicitly() {
        val settings = configure {
            episodes(strategyId = "ep-1", actorId = "alice", sessionId = "s-1")
            reflections(strategyId = "ep-1", actorId = "alice")
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals(2, subrequests.size)
        assertEquals(sessionNs("ep-1", "alice", "s-1"), subrequests[0].namespace)
        assertEquals(actorNs("ep-1", "alice"), subrequests[1].namespace)
    }

    // ---- Escape hatch: raw subrequest(template) ----

    @Test
    fun testRawLegTemplateIsAppendedAsIs() {
        val template = AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest.listing(
            strategyType = AgentcoreMemoryStrategy.PREFERENCE,
            memoryStrategyId = "raw-1",
            namespace = "/custom/ns/",
            limit = 11,
        )
        val settings = configure { subrequest(template) }
        val subrequests = compositeOf(settings).subrequests
        assertEquals(1, subrequests.size)
        assertEquals("/custom/ns/", subrequests[0].namespace)
        val req = subrequests[0].buildRequest("q")
        assertIs<AgentcoreListingSearchRequest>(req)
        assertEquals("raw-1", req.memoryStrategyId)
        assertEquals(11, req.limit)
    }

    // ---- Validation ----

    @Test
    fun testEmptyBlockFails() {
        val thrown = assertFailsWith<IllegalStateException> {
            configure { /* no subrequests */ }
        }
        assertNotNull(thrown.message)
        assertTrue(thrown.message!!.contains("at least one subrequest"))
    }

    @Test
    fun testBlankMemoryIdFails() {
        val settings = LongTermMemory.RetrievalSettingsBuilder()
        assertFailsWith<IllegalArgumentException> {
            settings.agentcore(client, memoryId = "  ") {
                semantic(strategyId = "sem-1", actorId = "alice")
            }
        }
    }

    @Test
    fun testBlankStrategyIdFails() {
        assertFailsWith<IllegalArgumentException> {
            configure { semantic(strategyId = " ", actorId = "alice") }
        }
    }

    @Test
    fun testNonPositiveTopKFails() {
        assertFailsWith<IllegalArgumentException> {
            configure { semantic(strategyId = "sem-1", actorId = "alice", topK = 0) }
        }
    }

    @Test
    fun testNonPositiveUserPreferencesLimitFails() {
        assertFailsWith<IllegalArgumentException> {
            configure { userPreferences(strategyId = "up-1", actorId = "alice", limit = 0) }
        }
    }

    @Test
    fun testSummaryRejectsBlankSessionId() {
        assertFailsWith<IllegalArgumentException> {
            configure {
                summary(strategyId = "sum-1", actorId = "alice", sessionId = " ")
            }
        }
    }

    // ---- Blank actorId must be rejected by every helper that builds a namespace ----

    @Test
    fun testSemanticRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { semantic(strategyId = "sem-1", actorId = " ") }
        }
    }

    @Test
    fun testUserPreferencesRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { userPreferences(strategyId = "up-1", actorId = " ") }
        }
    }

    @Test
    fun testEpisodesRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { episodes(strategyId = "ep-1", actorId = " ", sessionId = "s-1") }
        }
    }

    @Test
    fun testReflectionsRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { reflections(strategyId = "ep-1", actorId = " ") }
        }
    }

    @Test
    fun testEpisodicRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { episodic(strategyId = "ep-1", actorId = " ", sessionId = "s-1") }
        }
    }

    @Test
    fun testSummaryRejectsBlankActorId() {
        assertFailsWith<IllegalArgumentException> {
            configure { summary(strategyId = "sum-1", actorId = " ", sessionId = "s-1") }
        }
    }

    // ---- Custom namespace resolver ----

    @Test
    fun testCustomTemplateResolverAppliesToActorAndSessionScopedHelpers() {
        val settings = configure {
            namespaceResolver = AgentcoreNamespaceResolver.template(
                actorScoped = "/tenants/acme/users/{actorId}/{strategyId}/",
                sessionScoped = "/tenants/acme/users/{actorId}/{strategyId}/sessions/{sessionId}/",
            )
            semantic(strategyId = "sem-1", actorId = "alice")
            userPreferences(strategyId = "up-1", actorId = "alice")
            summary(strategyId = "sum-1", actorId = "alice", sessionId = "s-1")
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals("/tenants/acme/users/alice/sem-1/", subrequests[0].namespace)
        assertEquals("/tenants/acme/users/alice/up-1/", subrequests[1].namespace)
        assertEquals("/tenants/acme/users/alice/sum-1/sessions/s-1/", subrequests[2].namespace)
    }

    @Test
    fun testCustomResolverAppliesToEpisodicHelpers() {
        val settings = configure {
            namespaceResolver = AgentcoreNamespaceResolver { scope ->
                when (scope) {
                    is AgentcoreNamespaceScope.Actor ->
                        "/custom/${scope.actorId}/${scope.strategyId}/"

                    is AgentcoreNamespaceScope.Session ->
                        "/custom/${scope.actorId}/${scope.strategyId}/${scope.sessionId}/"
                }
            }
            episodic(strategyId = "ep-1", actorId = "alice", sessionId = "s-1")
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals("/custom/alice/ep-1/s-1/", subrequests[0].namespace)
        assertEquals("/custom/alice/ep-1/", subrequests[1].namespace)
    }

    @Test
    fun testSubrequestEscapeHatchBypassesCustomResolver() {
        val template = AgentcoreCompositeSearchStrategy.AgentcoreSearchSubrequest.listing(
            strategyType = AgentcoreMemoryStrategy.PREFERENCE,
            memoryStrategyId = "raw-1",
            namespace = "/literal/ns/",
            limit = 3,
        )
        val settings = configure {
            namespaceResolver = AgentcoreNamespaceResolver { "/should/not/be/used/" }
            subrequest(template)
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals("/literal/ns/", subrequests[0].namespace)
    }

    @Test
    fun testDefaultResolverMatchesAgentcoreNamespaceHelper() {
        val settings = configure {
            semantic(strategyId = "sem-1", actorId = "alice")
            summary(strategyId = "sum-1", actorId = "alice", sessionId = "s-1")
        }
        val subrequests = compositeOf(settings).subrequests
        assertEquals(actorNs("sem-1", "alice"), subrequests[0].namespace)
        assertEquals(sessionNs("sum-1", "alice", "s-1"), subrequests[1].namespace)
    }
}
