package ai.koog.agents.core.utils

/**
 * Represents a functional interface designed to apply configurations of type [T].
 * This interface allows for standardized handling of configuration operations
 * across various implementations or systems.
 *
 * @param T The type of object that this configuration action will operate on.
 */
public fun interface ConfigureAction<T> {
    /**
     * Configures the provided configuration object.
     *
     * @param config The configuration object to be modified or customized.
     */
    public fun configure(config: T)
}
