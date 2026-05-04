package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider

@Deprecated(
    "`PersistencyFeatureConfig` has been renamed to `PersistenceFeatureConfig`",
    replaceWith = ReplaceWith(
        expression = "PersistenceFeatureConfig",
        "ai.koog.agents.snapshot.feature.PersistenceFeatureConfig"
    )
)
public typealias PersistencyFeatureConfig = PersistenceFeatureConfig

/**
 * Configuration class for the Snapshot feature.
 */
public class PersistenceFeatureConfig : FeatureConfig() {

    /**
     * Defines the storage mechanism for persisting snapshots in the feature.
     * This property accepts implementations of [PersistenceStorageProvider],
     * which manage how snapshots are stored and retrieved.
     *
     * By default, the storage is set to [NoPersistencyStorageProvider], a no-op
     * implementation that does not persist any data. To enable actual state
     * persistence, assign a custom implementation of [PersistenceStorageProvider]
     * to this property.
     */
    public var storage: PersistenceStorageProvider<*> = NoPersistencyStorageProvider()

    /**
     * Controls whether the feature's state should be automatically persisted.
     * When enabled, changes to the checkpoint are saved after each node execution through the assigned
     * [PersistenceStorageProvider], ensuring the state can be restored later.
     *
     * Set this property to `true` to turn on automatic state persistence,
     * or `false` to disable it.
     */
    public var enableAutomaticPersistence: Boolean = true

    @Deprecated(
        message = "rollbackStrategy is deprecated. Use ChatMemory feature is you want to preserve only message history."
    )
    public var rollbackStrategy: RollbackStrategy = RollbackStrategy.Default

    /**
     * Registry for rollback tools used when rolling back to checkpoints.
     * Configure it during Persistence installation. Do not mutate later in withPersistence.
     */
    public var rollbackToolRegistry: RollbackToolRegistry = RollbackToolRegistry.EMPTY

    /**
     * Storage keys whose values should be captured in checkpoints and restored on resume.
     *
     * Each entry pairs an [ai.koog.agents.core.agent.entity.AIAgentStorageKey] with a kotlinx
     * [kotlinx.serialization.KSerializer] for its value type; build them with the [persisted] helper.
     * Only registered keys participate in persistence; everything else (including framework-internal
     * storage entries such as the per-feature state) is left untouched, so existing storage usage
     * keeps working without change.
     *
     * Example:
     * ```kotlin
     * install(Persistence) {
     *     storage = InMemoryPersistenceStorageProvider()
     *     persistedKeys = listOf(
     *         persisted(RetryCounterKey, Int.serializer()),
     *         persisted(PendingHumanInputKey, PendingHumanInput.serializer()),
     *     )
     * }
     * ```
     */
    public var persistedKeys: List<PersistableStorageKey<*>> = emptyList()
}
