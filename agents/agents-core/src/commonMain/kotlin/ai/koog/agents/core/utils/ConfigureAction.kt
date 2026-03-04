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

/**
 * A functional interface representing an action that can be performed on a builder in a chainable manner.
 * It defines a transformation from one builder type to another, allowing the construction process to be extended or modified incrementally.
 *
 * @param Builder The type of the input builder that will be configured.
 * @param ResultingBuilder The type of the resulting builder produced after configuration.
 */
public fun interface BuilderChainAction<Builder, ResultingBuilder> {
    /**
     * Configures the current builder with the specified configuration settings.
     *
     * @param builder The builder instance containing the configuration settings to apply.
     * @return The resulting builder after the configuration has been applied.
     */
    public fun configure(builder: Builder): ResultingBuilder
}
