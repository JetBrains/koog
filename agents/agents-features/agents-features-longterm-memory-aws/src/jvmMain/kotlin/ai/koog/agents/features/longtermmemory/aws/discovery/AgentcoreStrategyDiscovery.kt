package ai.koog.agents.features.longtermmemory.aws.discovery

import aws.sdk.kotlin.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.GetMemoryRequest
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.MemoryStrategy
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.MemoryStrategyType
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.ReflectionConfiguration
import org.slf4j.LoggerFactory

/**
 * Discovers memory strategies registered on an AWS Bedrock AgentCore memory store via
 * the control-plane `GetMemory` API.
 *
 * It returns a list of [AgentcoreDiscoveredStrategy] that can  be passed
 * to the `agentcoreDiscovered(...)` DSL extension to build a Koog
 * [ai.koog.agents.longtermmemory.feature.LongTermMemory] retrieval configuration without
 * hand-listing every `strategyId` and namespace template.
 *
 * Discovery is a suspending operation because the AWS Kotlin SDK is suspending; perform
 * it *before* entering the synchronous `install(LongTermMemory) { retrieval { ... } }`
 * block (which cannot suspend) and then pass the result into `agentcoreDiscovered(...)`.
 *
 * Strategies that are unusable for retrieval are filtered out with a warning log:
 *  - strategies of type `CUSTOM` or any `SdkUnknown` value the Kotlin SDK does not
 *    map to a known `MemoryStrategyType` constant,
 *  - strategies with null/blank `strategyId`,
 *  - strategies with no `namespaceTemplates`.
 *
 * For `EPISODIC` strategies the resolver additionally extracts reflection namespaces from
 * `strategy.configuration.reflection` when the variant is
 * [ReflectionConfiguration.EpisodicReflectionConfiguration]. The
 * [ReflectionConfiguration.CustomReflectionConfiguration] variant is logged as
 * informational and ignored. **Custom reflection namespaces cannot be materialized via
 * the `configure(strategyId) { reflectionsNamespacePattern = ... }` override path**:
 * the DSL validator checks the supplied pattern against the namespaces present in the
 * discovered descriptor, and a custom configuration produces an empty list, so the
 * override will always fail. To use reflection namespaces with a custom configuration,
 * supply them in the AgentCore memory descriptor so they are returned by discovery.
 */
public class AgentcoreStrategyDiscovery(
    private val controlClient: BedrockAgentCoreControlClient,
) {
    /**
     * Discovers all usable strategies on the given AgentCore memory.
     *
     * @param memoryId AgentCore memory store identifier (must not be blank).
     * @return list of discovered, usable strategies. May be empty when the memory has no
     *   supported strategies; callers (in particular the `agentcoreDiscovered(...)` DSL)
     *   are expected to fail with a descriptive error in that case.
     */
    public suspend fun discover(memoryId: String): List<AgentcoreDiscoveredStrategy> {
        require(memoryId.isNotBlank()) { "memoryId must not be blank" }

        logger.info("Discovering AgentCore memory strategies for memoryId='{}'", memoryId)
        val memory = controlClient.getMemory(GetMemoryRequest { this.memoryId = memoryId }).memory
        logger.debug("AgentCore memory with memoryId='{}': name='{}', status='{}'", memoryId, memory?.name, memory?.status)
        val strategies = memory?.strategies.orEmpty()

        if (strategies.isEmpty()) {
            logger.warn("No strategies found on AgentCore memory '{}'", memoryId)
            return emptyList()
        }

        val discovered = mutableListOf<AgentcoreDiscoveredStrategy>()
        var skipped = 0
        for (strategy in strategies) {
            val mapped = mapStrategy(strategy)
            if (mapped != null) {
                discovered += mapped
            } else {
                skipped++
            }
        }

        logDiscoveryOverview(memoryId, strategies.size, discovered, skipped)
        return discovered
    }

    private fun mapStrategy(strategy: MemoryStrategy): AgentcoreDiscoveredStrategy? {
        val strategyId = strategy.strategyId
        if (strategyId.isBlank()) {
            logger.warn("Skipping AgentCore memory strategy with null/blank strategyId (type={})", strategy.type)
            return null
        }

        val sdkType = strategy.type

        val type = sdkType.toDiscoveredType()
        if (type == null) {
            logger.warn(
                "Skipping AgentCore memory strategy '{}': unsupported type '{}' (auto-discovery handles " +
                    "SEMANTIC, USER_PREFERENCE, SUMMARIZATION and EPISODIC only)",
                strategyId,
                sdkType
            )
            return null
        }

        val namespaces = strategy.namespaceTemplates
        if (namespaces.isEmpty()) {
            logger.warn("Skipping AgentCore memory strategy '{}': has no namespaces", strategyId)
            return null
        }

        val reflectionsNamespaces = if (type == AgentcoreDiscoveredStrategyType.EPISODIC) {
            extractReflectionsNamespaces(strategyId, strategy)
        } else {
            emptyList()
        }

        return AgentcoreDiscoveredStrategy(
            strategyId = strategyId,
            type = type,
            namespaces = namespaces.toList(),
            reflectionsNamespaces = reflectionsNamespaces,
        )
    }

    /**
     * Walk `MemoryStrategy.configuration.reflection` and return the reflection namespace
     * templates for an episodic strategy.
     *
     * The Kotlin SDK exposes [ReflectionConfiguration] as a sealed class; only the
     * `EpisodicReflectionConfiguration` variant carries namespace templates that we can
     * auto-discover. `CustomReflectionConfiguration` is logged as informational and
     * skipped — the caller can still wire it up via a `configure(strategyId) { ... }`
     * override on the DSL builder.
     */
    private fun extractReflectionsNamespaces(strategyId: String, strategy: MemoryStrategy): List<String> {
        val reflection = strategy.configuration?.reflection ?: return emptyList()
        return when (reflection) {
            is ReflectionConfiguration.EpisodicReflectionConfiguration ->
                reflection.value.namespaceTemplates.orEmpty().toList()
            is ReflectionConfiguration.CustomReflectionConfiguration -> {
                logger.info(
                    "Strategy '{}' uses customReflectionConfiguration; reflection-namespace auto-discovery " +
                        "will skip this entry. Custom reflection namespaces cannot be materialized via the " +
                        "configure(\"{}\") {{ reflectionsNamespacePattern = ... }} override path — the validator " +
                        "checks the override value against the namespaces present in the discovered descriptor, " +
                        "and a custom configuration produces an empty list. Supply the reflection namespace " +
                        "directly in the AgentCore memory descriptor so it is returned by discovery.",
                    strategyId,
                    strategyId
                )
                emptyList()
            }
            is ReflectionConfiguration.SdkUnknown -> {
                logger.warn(
                    "Strategy '{}' has an unknown ReflectionConfiguration variant; skipping auto-discovery " +
                        "of reflection namespaces",
                    strategyId,
                )
                emptyList()
            }
        }
    }

    private fun logDiscoveryOverview(
        memoryId: String,
        total: Int,
        discovered: List<AgentcoreDiscoveredStrategy>,
        skipped: Int,
    ) {
        if (discovered.isEmpty()) {
            logger.warn(
                "Strategy discovery for AgentCore memory '{}': {} total, 0 usable, {} skipped",
                memoryId,
                total,
                skipped
            )
            return
        }
        val table = buildString {
            append('\n')
            append("  Strategy discovery for AgentCore memory '")
            append(memoryId)
            append("':")
            append('\n')
            append("  ")
            append("TYPE".padEnd(20))
            append(' ')
            append("STRATEGY ID".padEnd(45))
            append(' ')
            append("REFLECTIONS")
            append('\n')
            append("  ")
            append("-".repeat(80))
            append('\n')
            for (ds in discovered) {
                val refl = if (ds.reflectionsNamespaces.isEmpty()) "no" else "yes"
                append("  ")
                append(ds.type.name.padEnd(20))
                append(' ')
                append(ds.strategyId.padEnd(45))
                append(' ')
                append(refl)
                append('\n')
            }
            append("  ")
            append(discovered.size)
            append(" usable / ")
            append(total)
            append(" total")
            if (skipped > 0) {
                append(" (")
                append(skipped)
                append(" skipped)")
            }
        }
        logger.info(table)
    }

    private fun MemoryStrategyType.toDiscoveredType(): AgentcoreDiscoveredStrategyType? = when (this) {
        MemoryStrategyType.Semantic -> AgentcoreDiscoveredStrategyType.SEMANTIC
        MemoryStrategyType.UserPreference -> AgentcoreDiscoveredStrategyType.USER_PREFERENCE
        MemoryStrategyType.Summarization -> AgentcoreDiscoveredStrategyType.SUMMARY
        MemoryStrategyType.Episodic -> AgentcoreDiscoveredStrategyType.EPISODIC
        MemoryStrategyType.Custom -> null
        is MemoryStrategyType.SdkUnknown -> null
    }

    private companion object {
        private val logger = LoggerFactory.getLogger("AgentcoreStrategyDiscovery")
    }
}
