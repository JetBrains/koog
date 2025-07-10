package ai.koog.agents.core.dsl.builder

/**
 * Indicates that a class or method is part of a DSL for building AI agents.
 *
 * This annotation functions as a DSL marker to ensure type-safe scoping and to
 * prevent accidental misuse of functions outside their intended context within
 * the AI agent builder DSL.
 *
 * Applying this annotation helps reduce errors by enforcing constraints on
 * nested DSL blocks, ensuring that certain DSL elements are used only
 * in the correct context.
 */
@DslMarker
public annotation class AIAgentBuilderDslMarker

/**
 * A generic base interface for builders that enables the construction of an object of type `T`.
 * This interface is typically used in DSL patterns, providing a fluent and structured way to
 * build complex objects.
 *
 * @param T The type of object that the builder produces.
 */
@AIAgentBuilderDslMarker
public interface BaseBuilder<T> {
    /**
     * Builds and returns the instance of type `T` that has been configured using the builder.
     *
     * @return The constructed instance of type `T`.
     */
    public fun build(): T
}
