package ai.jetbrains.code.prompt.cache.model

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message

/**
 * Interface for caching prompt execution results.
 * Implementations should provide a way to store and retrieve prompt execution results.
 */
public interface PromptCache {
    public interface Factory {
        public class Aggregated(private val factories: List<Named>) : Factory {
            public constructor(vararg factories: Named) : this(factories.toList())

            override fun create(config: String): PromptCache {
                for (factory in factories) {
                    if (factory.supports(config)) return factory.create(config)
                }
                error("Unable to find supported cache provider for '$config'")
            }
        }

        public abstract class Named(public val name: String) : Factory {
            public fun supports(config: String): Boolean = name == elements(config).firstOrNull()
        }

        public fun create(config: String): PromptCache

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
