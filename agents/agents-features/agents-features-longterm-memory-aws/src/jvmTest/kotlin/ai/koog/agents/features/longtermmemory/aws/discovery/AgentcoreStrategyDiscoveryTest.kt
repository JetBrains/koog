package ai.koog.agents.features.longtermmemory.aws.discovery

import aws.sdk.kotlin.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.CustomReflectionConfiguration
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.EpisodicReflectionConfiguration
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.EpisodicReflectionOverride
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.GetMemoryRequest
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.GetMemoryResponse
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.Memory
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.MemoryStrategy
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.MemoryStrategyType
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.ReflectionConfiguration
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.StrategyConfiguration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct tests for the SDK-mapping logic in [AgentcoreStrategyDiscovery]. The
 * suspending control-plane client is mocked with `mockk`, so these tests run
 * offline without touching AWS.
 *
 * Covers:
 *  - mapping of every supported [MemoryStrategyType] to its [AgentcoreDiscoveredStrategyType],
 *  - filtering of `Custom` / `SdkUnknown` strategy types,
 *  - filtering of null/blank `strategyId`,
 *  - filtering of empty `namespaceTemplates`,
 *  - extraction of episodic reflection namespaces from
 *    [ReflectionConfiguration.EpisodicReflectionConfiguration],
 *  - graceful handling of [ReflectionConfiguration.CustomReflectionConfiguration]
 *    and `SdkUnknown` reflection variants.
 */
class AgentcoreStrategyDiscoveryTest {

    private val controlClient = mockk<BedrockAgentCoreControlClient>()
    private val memoryId = "mem-123"

    private fun mockMemoryWith(strategies: List<MemoryStrategy>) {
        // The SDK's Memory builder enforces a number of required fields (createdAt,
        // updatedAt, eventExpiryDuration, status, ...) that are irrelevant to discovery.
        // Mock the Memory directly so we only have to express what the discovery code
        // actually reads.
        val memoryMock = mockk<Memory>(relaxed = true)
        every { memoryMock.strategies } returns strategies
        val req = slot<GetMemoryRequest>()
        coEvery { controlClient.getMemory(capture(req)) } answers {
            GetMemoryResponse {
                memory = memoryMock
            }
        }
    }

    private fun discover(): List<AgentcoreDiscoveredStrategy> = runBlocking {
        AgentcoreStrategyDiscovery(controlClient).discover(memoryId)
    }

    /**
     * Build a [MemoryStrategy] via `mockk` instead of the SDK builder. The SDK builder
     * requires several fields (`name`, `createdAt`, ...) that the discovery code never
     * touches; using a relaxed mock keeps the tests focused on the four properties
     * `AgentcoreStrategyDiscovery` actually reads.
     */
    private fun strategy(
        id: String = "sem-1",
        type: MemoryStrategyType = MemoryStrategyType.Semantic,
        namespaceTemplates: List<String> = listOf("/strategies/{memoryStrategyId}/actors/{actorId}/"),
        configuration: StrategyConfiguration? = null,
    ): MemoryStrategy {
        val s = mockk<MemoryStrategy>(relaxed = true)
        every { s.strategyId } returns id
        every { s.type } returns type
        every { s.namespaceTemplates } returns namespaceTemplates
        every { s.configuration } returns configuration
        return s
    }

    // ---- Type mapping ----

    @Test
    fun testSemanticTypeMapsToSemantic() {
        mockMemoryWith(listOf(strategy(id = "sem-1", type = MemoryStrategyType.Semantic)))
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.SEMANTIC, result[0].type)
        assertEquals("sem-1", result[0].strategyId)
        assertEquals(listOf("/strategies/{memoryStrategyId}/actors/{actorId}/"), result[0].namespaces)
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
    }

    @Test
    fun testUserPreferenceTypeMapsToUserPreference() {
        mockMemoryWith(listOf(strategy(id = "up-1", type = MemoryStrategyType.UserPreference)))
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.USER_PREFERENCE, result[0].type)
    }

    @Test
    fun testSummarizationTypeMapsToSummary() {
        mockMemoryWith(listOf(strategy(id = "sum-1", type = MemoryStrategyType.Summarization)))
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.SUMMARY, result[0].type)
    }

    @Test
    fun testEpisodicTypeMapsToEpisodic() {
        mockMemoryWith(listOf(strategy(id = "ep-1", type = MemoryStrategyType.Episodic)))
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.EPISODIC, result[0].type)
        // No reflection configuration set → no reflection namespaces are extracted.
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
    }

    @Test
    fun testRequestIsRoutedToTheGivenMemoryId() {
        mockMemoryWith(listOf(strategy()))
        discover()
        coVerify(exactly = 1) { controlClient.getMemory(match { it.memoryId == memoryId }) }
    }

    // ---- Filtering ----

    @Test
    fun testCustomTypeIsFilteredOut() {
        mockMemoryWith(
            listOf(
                strategy(id = "custom-1", type = MemoryStrategyType.Custom),
                strategy(id = "sem-1", type = MemoryStrategyType.Semantic),
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals("sem-1", result[0].strategyId)
    }

    @Test
    fun testSdkUnknownTypeIsFilteredOut() {
        mockMemoryWith(
            listOf(
                strategy(id = "future-1", type = MemoryStrategyType.SdkUnknown("FUTURE_TYPE")),
                strategy(id = "sem-1", type = MemoryStrategyType.Semantic),
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals("sem-1", result[0].strategyId)
    }

    @Test
    fun testBlankStrategyIdIsFilteredOut() {
        mockMemoryWith(
            listOf(
                strategy(id = "   "),
                strategy(id = "sem-1"),
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals("sem-1", result[0].strategyId)
    }

    // Note: the AWS Kotlin SDK declares `MemoryStrategy.strategyId` as non-null on
    // the JVM, so a true null can't actually reach the mapper from production code;
    // the discovery code's `strategyId.isBlank()` check covers the observable case.

    @Test
    fun testEmptyNamespaceTemplatesIsFilteredOut() {
        mockMemoryWith(
            listOf(
                strategy(id = "no-ns", namespaceTemplates = emptyList()),
                strategy(id = "sem-1"),
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals("sem-1", result[0].strategyId)
    }

    @Test
    fun testEmptyMemoryReturnsEmptyDiscoveryList() {
        mockMemoryWith(emptyList())
        assertTrue(discover().isEmpty())
    }

    @Test
    fun testMemoryWithOnlyUnsupportedStrategiesReturnsEmptyList() {
        mockMemoryWith(
            listOf(
                strategy(id = "custom-1", type = MemoryStrategyType.Custom),
                strategy(id = "future-1", type = MemoryStrategyType.SdkUnknown("FOO")),
            )
        )
        assertTrue(discover().isEmpty())
    }

    @Test
    fun testBlankMemoryIdIsRejected() {
        // The require(...) check fires before any SDK call, so we don't need to set up a mock.
        assertFailsWith<IllegalArgumentException> {
            runBlocking { AgentcoreStrategyDiscovery(controlClient).discover("   ") }
        }
    }

    // ---- Episodic reflection extraction ----

    @Test
    fun testEpisodicReflectionNamespacesAreExtracted() {
        val episodicConfig = StrategyConfiguration {
            reflection = ReflectionConfiguration.EpisodicReflectionConfiguration(
                EpisodicReflectionConfiguration {
                    namespaceTemplates = listOf(
                        "/strategies/{memoryStrategyId}/actors/{actorId}/",
                        "/tenants/acme/{memoryStrategyId}/{actorId}/reflections/",
                    )
                }
            )
        }
        mockMemoryWith(
            listOf(
                strategy(
                    id = "ep-1",
                    type = MemoryStrategyType.Episodic,
                    namespaceTemplates = listOf("/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/"),
                    configuration = episodicConfig,
                )
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.EPISODIC, result[0].type)
        assertEquals(
            listOf(
                "/strategies/{memoryStrategyId}/actors/{actorId}/",
                "/tenants/acme/{memoryStrategyId}/{actorId}/reflections/",
            ),
            result[0].reflectionsNamespaces,
        )
    }

    @Test
    fun testNonEpisodicStrategyDoesNotExtractReflections() {
        // Even if (hypothetically) a non-EPISODIC strategy carried a reflection block,
        // the mapper must not surface it as reflection namespaces.
        val configWithReflection = StrategyConfiguration {
            reflection = ReflectionConfiguration.EpisodicReflectionConfiguration(
                EpisodicReflectionConfiguration {
                    namespaceTemplates = listOf("/ignored/")
                }
            )
        }
        mockMemoryWith(
            listOf(
                strategy(
                    id = "sem-1",
                    type = MemoryStrategyType.Semantic,
                    configuration = configWithReflection,
                )
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
    }

    @Test
    fun testEpisodicWithCustomReflectionConfigurationYieldsEmptyReflections() {
        // Build the CustomReflectionConfiguration variant via a mockk-relaxed inner
        // EpisodicReflectionOverride — the SDK builder enforces required fields
        // (appendToPrompt, ...) that the discovery code never inspects.
        val customReflection = ReflectionConfiguration.CustomReflectionConfiguration(
            CustomReflectionConfiguration.EpisodicReflectionOverride(
                mockk<EpisodicReflectionOverride>(relaxed = true)
            )
        )
        val customReflectionConfig = mockk<StrategyConfiguration>(relaxed = true)
        every { customReflectionConfig.reflection } returns customReflection
        mockMemoryWith(
            listOf(
                strategy(
                    id = "ep-1",
                    type = MemoryStrategyType.Episodic,
                    configuration = customReflectionConfig,
                )
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertEquals(AgentcoreDiscoveredStrategyType.EPISODIC, result[0].type)
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
        assertNull(result[0].defaultReflectionsNamespace)
    }

    @Test
    fun testEpisodicWithSdkUnknownReflectionYieldsEmptyReflections() {
        val unknownReflectionConfig = StrategyConfiguration {
            reflection = ReflectionConfiguration.SdkUnknown
        }
        mockMemoryWith(
            listOf(
                strategy(
                    id = "ep-1",
                    type = MemoryStrategyType.Episodic,
                    configuration = unknownReflectionConfig,
                )
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
    }

    @Test
    fun testEpisodicWithoutStrategyConfigurationYieldsEmptyReflections() {
        mockMemoryWith(
            listOf(
                strategy(
                    id = "ep-1",
                    type = MemoryStrategyType.Episodic,
                    configuration = null,
                )
            )
        )
        val result = discover()
        assertEquals(1, result.size)
        assertTrue(result[0].reflectionsNamespaces.isEmpty())
    }

    // ---- Mixed inputs ----

    @Test
    fun testDiscoveryPreservesEncounterOrderForUsableStrategies() {
        mockMemoryWith(
            listOf(
                strategy(id = "ep-1", type = MemoryStrategyType.Episodic),
                strategy(id = "custom-1", type = MemoryStrategyType.Custom), // filtered
                strategy(id = "sem-1", type = MemoryStrategyType.Semantic),
                strategy(id = "   ", type = MemoryStrategyType.Summarization), // filtered (blank id)
                strategy(id = "up-1", type = MemoryStrategyType.UserPreference),
            )
        )
        val result = discover()
        assertEquals(listOf("ep-1", "sem-1", "up-1"), result.map { it.strategyId })
        assertEquals(
            listOf(
                AgentcoreDiscoveredStrategyType.EPISODIC,
                AgentcoreDiscoveredStrategyType.SEMANTIC,
                AgentcoreDiscoveredStrategyType.USER_PREFERENCE,
            ),
            result.map { it.type },
        )
    }
}
