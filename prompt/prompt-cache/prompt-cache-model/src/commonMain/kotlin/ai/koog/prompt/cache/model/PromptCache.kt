package ai.koog.prompt.cache.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

/**
 * Interface for caching prompt execution results.
 * Implementations should provide a way to store and retrieve prompt execution results.
 */
public interface PromptCache {
    /**
     * Represents a factory interface for creating instances of `PromptCache`.
     * Factories implementing this interface are designed to construct specific types of prompt caches
     * based on a provided configuration string.
     */
    public interface Factory {
        public class Aggregated(private val factories: List<Factory.Named>) : Factory {
            /**
             * Secondary constructor for the `Aggregated` class.
             *
             * This constructor allows the creation of an `Aggregated` instance using a variable number
             * of `Factory.Named` objects. Internally, the provided objects are converted into a list
             * and passed to the primary constructor.
             *
             * @param factories A variable number of `Factory.Named` instances to be included in the `Aggregated` instance.
             */
            public constructor(vararg factories: Factory.Named) : this(factories.toList())

            override fun create(config: String): PromptCache {
                for (factory in factories) {
                    if (factory.supports(config)) return factory.create(config)
                }
                error("Unable to find supported cache provider for '$config'")
            }
        }

        /**
         * Represents an abstract factory with a specific name for creating `PromptCache` instances.
         *
         * This class is designed to be extended by concrete implementations that register
         * themselves with a unique `name`, allowing them to support specific configurations.
         * It provides a utility method to determine if the factory can handle a given configuration
         * string based on its `name`.
         *
         * @property name The unique name associated with the factory.
         */
        public abstract class Named(public val name: String) : Factory {
            public fun supports(config: String): Boolean = name == elements(config).firstOrNull()
        }

        /**
         * Creates a new instance of `PromptCache` based on the provided configuration string.
         *
         * @param config The configuration string used to determine how the `PromptCache` instance is created.
         * @return An instance of `PromptCache` tailored to the provided configuration.
         */
        public fun create(config: String): PromptCache

        /**
         * Splits the given configuration string into a list of elements using ':' as the primary delimiter,
         * while correctly handling nested structures indicated by curly braces '{' and '}'.
         *
         * @param config The configuration string to be parsed. This string may include nested structures enclosed in braces.
         * @return A list of strings representing the parsed elements in the configuration, preserving nested structures as single elements.
         */
        public fun elements(config: String): List<String> {
            val result = mutableListOf<String>()
            var current = StringBuilder()
            var braceCount = 0

            for (char in config) {
                when (char) {
                    ':' -> if (braceCount == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }

                    '{' -> {
                        if (braceCount > 0) current.append(char)
                        braceCount++
                    }

                    '}' -> {
                        braceCount--
                        if (braceCount > 0) current.append(char)
                    }

                    else -> current.append(char)
                }
            }

            if (current.isNotEmpty()) result.add(current.toString())

            return result
        }
    }

    /**
     * Get a cached response for a prompt with tools, or null if not cached.
     *
     * @param prompt The prompt to get the cached response for
     * @param tools The tools used with the prompt
     * @return The cached response, or null if not cached
     */
    public suspend fun get(prompt: Prompt, tools: List<ToolDescriptor> = emptyList()): List<Message.Response>?

    /**
     * Put a response in the cache for a prompt with tools.
     *
     * @param prompt The prompt to cache the response for
     * @param tools The tools used with the prompt
     * @param response The response to cache
     */
    public suspend fun put(prompt: Prompt, tools: List<ToolDescriptor> = emptyList(), response: List<Message.Response>)
}
