package ai.koog.agents.features.longtermmemory.aws.dsl

import ai.koog.agents.features.longtermmemory.aws.AgentcoreCompositeSearchStrategy
import ai.koog.agents.features.longtermmemory.aws.AgentcoreSearchStorage
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcorePromptAugmenter
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreDiscoveredStrategy
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreDiscoveredStrategyType
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the AgentCore long-term memory auto-discovery DSL.
 *
 * These tests focus on the materialization step: given a list of already-discovered
 * strategies (the suspending control-plane call is exercised by the integration test),
 * verify that `agentcoreDiscovered { }` produces the correct composite subrequests,
 * applies overrides, honors exclusions, and validates inputs without talking to AWS.
 */
class AgentcoreAutoDiscoveryDslTest {

    private val client = mockk<BedrockAgentCoreClient>(relaxed = true)
    private val memoryId = "mem-123"
    private val actorId = "alice"
    private val sessionId = "sess-42"

    private fun materialize(
        discovered: List<AgentcoreDiscoveredStrategy>,
        actorId: String = this.actorId,
        sessionId: String? = this.sessionId,
        block: AgentcoreAutoDiscoveryBuilder.() -> Unit = {},
    ): LongTermMemory.RetrievalSettingsBuilder {
        val settings = LongTermMemory.RetrievalSettingsBuilder()
        settings.agentcoreDiscovered(
            client = client,
            memoryId = memoryId,
            discoveredStrategies = discovered,
            actorId = actorId,
            sessionId = sessionId,
            block = block,
        )
        return settings
    }

    private fun compositeOf(settings: LongTermMemory.RetrievalSettingsBuilder): AgentcoreCompositeSearchStrategy {
        val strategy = settings.searchStrategy
        assertIs<AgentcoreCompositeSearchStrategy>(strategy)
        return strategy
    }

    private fun semantic(id: String = "sem-1", ns: String = "/strategies/{memoryStrategyId}/actors/{actorId}/") =
        AgentcoreDiscoveredStrategy(id, AgentcoreDiscoveredStrategyType.SEMANTIC, listOf(ns))

    private fun userPreference(id: String = "up-1", ns: String = "/strategies/{memoryStrategyId}/actors/{actorId}/") =
        AgentcoreDiscoveredStrategy(id, AgentcoreDiscoveredStrategyType.USER_PREFERENCE, listOf(ns))

    private fun summary(
        id: String = "sum-1",
        ns: String = "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/",
    ) = AgentcoreDiscoveredStrategy(id, AgentcoreDiscoveredStrategyType.SUMMARY, listOf(ns))

    private fun episodic(
        id: String = "ep-1",
        episodesNs: String = "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/",
        reflectionsNs: String? = "/strategies/{memoryStrategyId}/actors/{actorId}/",
    ) = AgentcoreDiscoveredStrategy(
        id,
        AgentcoreDiscoveredStrategyType.EPISODIC,
        listOf(episodesNs),
        reflectionsNs?.let { listOf(it) } ?: emptyList(),
    )

    // ---- Basic materialization ----

    @Test
    fun testSemanticDiscoveryProducesOneSimilaritySubrequest() {
        val settings = materialize(listOf(semantic("sem-1")))

        val strategy = compositeOf(settings)
        assertEquals(1, strategy.subrequests.size)
        val sub = strategy.subrequests[0]
        assertEquals("/strategies/sem-1/actors/alice/", sub.namespace)

        val req = sub.buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(req)
        assertEquals("sem-1", req.memoryStrategyId)
        assertEquals(AgentcoreMemoryStrategy.SEMANTIC, req.strategyType)
        assertEquals(5, req.limit) // default semantic topK
    }

    @Test
    fun testUserPreferenceDiscoveryProducesListingSubrequest() {
        val settings = materialize(listOf(userPreference("up-1")), sessionId = null)

        val strategy = compositeOf(settings)
        assertEquals(1, strategy.subrequests.size)
        val req = strategy.subrequests[0].buildRequest("ignored")
        assertIs<AgentcoreListingSearchRequest>(req)
        assertEquals("up-1", req.memoryStrategyId)
        assertEquals(AgentcoreMemoryStrategy.PREFERENCE, req.strategyType)
        assertEquals(50, req.limit) // default user-preference limit
    }

    @Test
    fun testSummaryDiscoveryUsesSessionScopedNamespace() {
        val settings = materialize(listOf(summary("sum-1")))

        val strategy = compositeOf(settings)
        assertEquals(1, strategy.subrequests.size)
        val sub = strategy.subrequests[0]
        assertEquals("/strategies/sum-1/actors/alice/sessions/sess-42/", sub.namespace)

        val req = sub.buildRequest("q")
        assertIs<AgentcoreSimilaritySearchRequest>(req)
        assertEquals(AgentcoreMemoryStrategy.SUMMARY, req.strategyType)
    }

    @Test
    fun testEpisodicDiscoveryProducesEpisodesAndReflections() {
        val settings = materialize(listOf(episodic("ep-1")))

        val strategy = compositeOf(settings)
        assertEquals(2, strategy.subrequests.size)

        val episodes = strategy.subrequests[0]
        assertEquals("/strategies/ep-1/actors/alice/sessions/sess-42/", episodes.namespace)
        val episodesReq = episodes.buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(AgentcoreMemoryStrategy.EPISODES, episodesReq.strategyType)
        assertEquals(3, episodesReq.limit)

        val reflections = strategy.subrequests[1]
        assertEquals("/strategies/ep-1/actors/alice/", reflections.namespace)
        val reflectionsReq = reflections.buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(AgentcoreMemoryStrategy.REFLECTIONS, reflectionsReq.strategyType)
        assertEquals(2, reflectionsReq.limit)
    }

    @Test
    fun testEpisodicWithoutReflectionsTemplateOnlyEmitsEpisodes() {
        val settings = materialize(listOf(episodic("ep-1", reflectionsNs = null)))

        val strategy = compositeOf(settings)
        assertEquals(1, strategy.subrequests.size)
        val req = strategy.subrequests[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(AgentcoreMemoryStrategy.EPISODES, req.strategyType)
    }

    @Test
    fun testMixedDiscoveryPreservesOrderAndAllSubrequests() {
        val settings = materialize(
            listOf(
                semantic("sem-1"),
                userPreference("up-1"),
                summary("sum-1"),
                episodic("ep-1"),
            )
        )

        val strategy = compositeOf(settings)
        // semantic, user-preference, summary, episodes, reflections = 5
        assertEquals(5, strategy.subrequests.size)
    }

    // ---- Storage / namespace / augmenter wiring ----

    @Test
    fun testStorageIsAgentcoreSearchStorageWithExpectedMemoryId() {
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null)
        val storage = settings.storage
        assertIs<AgentcoreSearchStorage>(storage)
        assertEquals(memoryId, storage.agentcoreMemoryId)
        assertSame(client, storage.client)
    }

    @Test
    fun testTopLevelNamespaceIsNull() {
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null)
        assertNull(settings.namespace)
    }

    @Test
    fun testDefaultAugmenterIsAgentcorePromptAugmenter() {
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null)
        assertIs<AgentcorePromptAugmenter>(settings.promptAugmenter)
    }

    @Test
    fun testAugmenterOverride() {
        val custom = UserPromptAugmenter()
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            augmenter = custom
        }
        assertSame(custom, settings.promptAugmenter)
    }

    // ---- Overrides ----

    @Test
    fun testConfigureOverridesTopKForSemantic() {
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            configure(AgentcoreStrategyOverride(strategyId = "sem-1", topK = 10, minScore = 0.7))
        }

        val req = compositeOf(settings).subrequests[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(10, req.limit)
        assertEquals(0.7, req.minScore)
    }

    @Test
    fun testConfigureBlockOverloadProducesSameOverride() {
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            configure("sem-1") {
                topK = 8
                filterExpression = "key='value'"
            }
        }

        val req = compositeOf(settings).subrequests[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(8, req.limit)
        assertEquals("key='value'", req.filterExpression)
    }

    @Test
    fun testEpisodicOverrideAppliesEpisodesAndReflectionsTopK() {
        val settings = materialize(listOf(episodic("ep-1"))) {
            configure(AgentcoreStrategyOverride(strategyId = "ep-1", episodesTopK = 7, reflectionsTopK = 4))
        }

        val subs = compositeOf(settings).subrequests
        val episodesReq = subs[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        val reflectionsReq = subs[1].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals(7, episodesReq.limit)
        assertEquals(4, reflectionsReq.limit)
    }

    @Test
    fun testConfigureWithoutMatchingDiscoveredStrategyIsIgnored() {
        // Override for a strategyId that doesn't exist — must not fabricate a subrequest.
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            configure(AgentcoreStrategyOverride(strategyId = "ghost", topK = 99))
        }
        assertEquals(1, compositeOf(settings).subrequests.size)
    }

    @Test
    fun testDoubleConfigureForSameStrategyFails() {
        assertFailsWith<IllegalArgumentException> {
            materialize(listOf(semantic("sem-1")), sessionId = null) {
                configure(AgentcoreStrategyOverride(strategyId = "sem-1", topK = 5))
                configure(AgentcoreStrategyOverride(strategyId = "sem-1", topK = 10))
            }
        }
    }

    // ---- Exclusion ----

    @Test
    fun testExcludeRemovesSpecificDiscoveredStrategy() {
        val settings = materialize(
            listOf(semantic("sem-1"), userPreference("up-1")),
            sessionId = null,
        ) {
            exclude("up-1")
        }
        val subs = compositeOf(settings).subrequests
        assertEquals(1, subs.size)
        val req = subs[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        assertEquals("sem-1", req.memoryStrategyId)
    }

    @Test
    fun testExcludingAllStrategiesFails() {
        val ex = assertFailsWith<IllegalStateException> {
            materialize(listOf(semantic("sem-1")), sessionId = null) {
                exclude("sem-1")
            }
        }
        assertTrue(ex.message!!.contains("excluded"))
    }

    // ---- Input validation ----

    @Test
    fun testEmptyDiscoveredListFails() {
        val ex = assertFailsWith<IllegalStateException> {
            materialize(emptyList(), sessionId = null)
        }
        assertTrue(ex.message!!.contains("No supported AgentCore memory strategies"))
    }

    @Test
    fun testMissingSessionIdForSessionScopedStrategyFails() {
        val ex = assertFailsWith<IllegalArgumentException> {
            materialize(listOf(summary("sum-1")), sessionId = null)
        }
        assertTrue(ex.message!!.contains("sessionId is required"))
    }

    @Test
    fun testBlankActorIdRejected() {
        assertFailsWith<IllegalArgumentException> {
            materialize(listOf(semantic("sem-1")), actorId = "  ", sessionId = null)
        }
    }

    @Test
    fun testBlankMemoryIdRejected() {
        val settings = LongTermMemory.RetrievalSettingsBuilder()
        assertFailsWith<IllegalArgumentException> {
            settings.agentcoreDiscovered(
                client = client,
                memoryId = "  ",
                discoveredStrategies = listOf(semantic("sem-1")),
                actorId = actorId,
                sessionId = null,
            )
        }
    }

    @Test
    fun testNonPositiveTopKInOverrideIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreStrategyOverride(strategyId = "sem-1", topK = 0)
        }
    }

    // ---- Namespace override via configure(...) ----

    @Test
    fun testNamespaceOverrideReplacesDiscoveredTemplate() {
        // The strategy must publish the override template among its discovered
        // namespaces — otherwise the DSL fails fast (see testNamespaceOverrideRejectsUnknownTemplate).
        val customTemplate = "/tenants/acme/users/{actorId}/{memoryStrategyId}/"
        val defaultTemplate = "/strategies/{memoryStrategyId}/actors/{actorId}/"
        val discovered = AgentcoreDiscoveredStrategy(
            strategyId = "sem-1",
            type = AgentcoreDiscoveredStrategyType.SEMANTIC,
            namespaces = listOf(defaultTemplate, customTemplate),
        )
        val settings = materialize(listOf(discovered), sessionId = null) {
            configure(
                AgentcoreStrategyOverride(
                    strategyId = "sem-1",
                    namespacePattern = customTemplate,
                )
            )
        }
        val sub = compositeOf(settings).subrequests[0]
        assertEquals("/tenants/acme/users/alice/sem-1/", sub.namespace)
    }

    @Test
    fun testNamespaceOverrideRejectsUnknownTemplate() {
        val ex = assertFailsWith<IllegalStateException> {
            materialize(listOf(semantic("sem-1")), sessionId = null) {
                configure(
                    AgentcoreStrategyOverride(
                        strategyId = "sem-1",
                        namespacePattern = "/typo/{memoryStrategyId}/users/{actorId}/",
                    )
                )
            }
        }
        val msg = ex.message!!
        assertTrue(msg.contains("namespacePattern"))
        assertTrue(msg.contains("/typo/"))
        // Discovered alternatives are listed so the typo is obvious.
        assertTrue(msg.contains("Discovered:"))
        assertTrue(msg.contains("/strategies/{memoryStrategyId}/actors/{actorId}/"))
    }

    @Test
    fun testReflectionsNamespaceOverrideAddsReflectionsForEpisodicWithoutDiscoveredReflections() {
        // An EPISODIC strategy with NO discovered reflection namespaces: an explicit
        // reflectionsNamespacePattern is not allowed because we cannot validate it against
        // discovery and would risk routing retrieval to an unregistered namespace.
        val ex = assertFailsWith<IllegalStateException> {
            materialize(listOf(episodic("ep-1", reflectionsNs = null))) {
                configure(
                    AgentcoreStrategyOverride(
                        strategyId = "ep-1",
                        reflectionsNamespacePattern = "/strategies/{memoryStrategyId}/actors/{actorId}/",
                    )
                )
            }
        }
        val msg = ex.message!!
        assertTrue(msg.contains("reflectionsNamespacePattern"))
        assertTrue(msg.contains("no discovered namespaces"))
    }

    @Test
    fun testReflectionsNamespaceOverrideAcceptsDiscoveredTemplate() {
        val primary = "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/"
        val reflPrimary = "/strategies/{memoryStrategyId}/actors/{actorId}/"
        val reflAlt = "/tenants/acme/{memoryStrategyId}/{actorId}/reflections/"
        val discovered = AgentcoreDiscoveredStrategy(
            strategyId = "ep-1",
            type = AgentcoreDiscoveredStrategyType.EPISODIC,
            namespaces = listOf(primary),
            reflectionsNamespaces = listOf(reflPrimary, reflAlt),
        )
        val settings = materialize(listOf(discovered)) {
            configure(
                AgentcoreStrategyOverride(
                    strategyId = "ep-1",
                    reflectionsNamespacePattern = reflAlt,
                )
            )
        }
        val subs = compositeOf(settings).subrequests
        assertEquals(2, subs.size)
        assertEquals("/tenants/acme/ep-1/alice/reflections/", subs[1].namespace)
    }

    @Test
    fun testReflectionsNamespaceOverrideRejectsUnknownTemplate() {
        val ex = assertFailsWith<IllegalStateException> {
            materialize(listOf(episodic("ep-1"))) {
                configure(
                    AgentcoreStrategyOverride(
                        strategyId = "ep-1",
                        reflectionsNamespacePattern = "/typo/{memoryStrategyId}/{actorId}/",
                    )
                )
            }
        }
        val msg = ex.message!!
        assertTrue(msg.contains("reflectionsNamespacePattern"))
        assertTrue(msg.contains("does not match any discovered"))
    }

    // ---- Unknown configure/exclude logging ----

    @Test
    fun testUnknownExcludeIdIsAcceptedButLogged() {
        // No throw — unknown IDs are a typo trap, not a correctness bug.
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            exclude("ghost-strategy")
        }
        assertEquals(1, compositeOf(settings).subrequests.size)
    }

    @Test
    fun testUnknownConfigureIdIsAcceptedButLogged() {
        // No throw — discovery + materialization continue as if the override didn't exist.
        val settings = materialize(listOf(semantic("sem-1")), sessionId = null) {
            configure(AgentcoreStrategyOverride(strategyId = "ghost", topK = 99))
        }
        val req = compositeOf(settings).subrequests[0].buildRequest("q") as AgentcoreSimilaritySearchRequest
        // Default topK is preserved because the ghost override is ignored.
        assertEquals(5, req.limit)
    }
}
