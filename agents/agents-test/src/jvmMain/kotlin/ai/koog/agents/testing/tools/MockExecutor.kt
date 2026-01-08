package ai.koog.agents.testing.tools

import ai.koog.agents.annotations.JavaAPI

/**
 * A class designed for mocking the execution of specific scenarios, typically used in testing environments.
 *
 * This class serves as a mock executor that provides tools and utilities to replicate or mimic the behavior
 * of specific systems or components, allowing developers to simulate and validate various execution patterns.
 *
 * Annotated with [JavaAPI], this class is designed for seamless integration and usability in Java environments.
 */
@JavaAPI
public class MockExecutor {
    /**
     * Companion object for the `MockExecutor` class, providing utility methods to create instances
     * and interact with `MockExecutor` in a streamlined manner.
     */
    public companion object {
        /**
         * Creates a new instance of `MockExecutorBuilder` to configure and build `MockExecutor` instances.
         *
         * @return An instance of `MockExecutorBuilder` for setting up and constructing `MockExecutor`.
         */
        @JvmStatic
        public fun builder(): MockExecutorBuilder = MockExecutorBuilder()
    }
}
