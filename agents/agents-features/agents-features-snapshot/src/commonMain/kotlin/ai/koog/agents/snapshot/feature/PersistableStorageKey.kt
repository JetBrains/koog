package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import kotlinx.serialization.KSerializer

/**
 * Pairs an [AIAgentStorageKey] with a [KSerializer] so the persistence feature
 * can round-trip its value through a checkpoint.
 *
 * Storage holds `Any` values; without an explicit serializer per key there is no portable way
 * to encode and decode them. Equality of [AIAgentStorageKey] is identity-based, so the same
 * instance must also be used at restore time, which is why registration carries the key itself
 * and not just its name.
 *
 * Construct via [persisted] and pass the result to [PersistenceFeatureConfig.persistedKeys] (for the
 * automatic snapshot path) or to [Persistence.runFromCheckpoint] (when resuming an agent that did
 * not have the feature installed).
 *
 * @param T The type of the value stored under [key].
 * @property key The storage key whose value should be included in checkpoints.
 * @property serializer The kotlinx serializer used to encode and decode the value.
 */
public class PersistableStorageKey<T : Any> internal constructor(
    public val key: AIAgentStorageKey<T>,
    public val serializer: KSerializer<T>,
)

/**
 * Registers [key] for persistence with the given [serializer]. The returned wrapper is intended
 * for [PersistenceFeatureConfig.persistedKeys] or [Persistence.runFromCheckpoint].
 */
public fun <T : Any> persisted(
    key: AIAgentStorageKey<T>,
    serializer: KSerializer<T>,
): PersistableStorageKey<T> = PersistableStorageKey(key, serializer)
