package ai.koog.prompt.cache.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.absoluteValue

private val defaultJson = Json {
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
}

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
        /**
         * A [PromptCache.Factory] implementation that aggregates multiple [Factory.Named] instances.
         *
         * The `Aggregated` class provides a way to combine multiple named factories and attempts
         * to create a `PromptCache` using the first factory that supports a given configuration.
         *
         * @property factories The list of `Factory.Named` instances to be aggregated.
         */
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
            /**
             * Determines if the current factory instance can support the provided configuration string.
             *
             * The method checks if the factory's `name` matches the first element of the parsed configuration string.
             *
             * @param config The configuration string to be evaluated.
             * @return `true` if the factory supports the configuration, otherwise `false`.
             */
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
     * Represents a request to be cached, consisting of a prompt and optional tools.
     * This class is used by PromptCache implementations to store and retrieve cached responses.
     *
     * @property prompt The prompt to be cached
     * @property toolJsons Json representations of the tools
     */
    @Serializable
    public class Request private constructor(
        public val prompt: Prompt,
        public val toolJsons: List<JsonObject> = emptyList()
    ) {
        /**
         * A unique identifier for the cache entry derived from the request's prompt and tools.
         * This value is used by caching mechanisms to store and retrieve cached responses.
         */
        public val asCacheKey: String
            get() {
                // Create a new prompt with timestamps removed from all messages
                val messagesWithoutMetaInfo = prompt.messages.map { message ->
                    when (message) {
                        is Message.User -> message.copy(metaInfo = RequestMetaInfo.Empty)
                        is Message.System -> message.copy(metaInfo = RequestMetaInfo.Empty)
                        is Message.Assistant -> message.copy(metaInfo = ResponseMetaInfo.Empty)
                        is Message.Reasoning -> message.copy(metaInfo = ResponseMetaInfo.Empty)
                        is Message.Tool.Call -> message.copy(metaInfo = ResponseMetaInfo.Empty)
                        is Message.Tool.Result -> message.copy(metaInfo = RequestMetaInfo.Empty)
                    }
                }

                val requestWithoutMetaInfo =
                    Request(Prompt(messagesWithoutMetaInfo, prompt.id, prompt.params), toolJsons)

                return defaultJson.encodeToString(requestWithoutMetaInfo).hashCode().absoluteValue.toString(36)
            }

        /**
         * Companion object for the Request class, providing factory functions and utility methods.
         */
        public companion object {
            /**
             * Creates a new [Request] instance with the provided prompt and a list of tools.
             *
             * @param prompt The [Prompt] object containing the messages, ID, and parameters to construct the request.
             * @param tools A list of [ToolDescriptor] objects to be included in the request. Each tool is converted to a JSON representation.
             * @return A new [Request] instance initialized with the given prompt and tool data.
             */
            public fun create(prompt: Prompt, tools: List<ToolDescriptor>): Request =
                Request(prompt, tools.map { toolToJsonObject(it) })

            /**
             * Convert a ToolDescriptor to a JsonObject representation.
             * This is a simplified version that just captures the tool name and description for caching purposes.
             */
            private fun toolToJsonObject(tool: ToolDescriptor): JsonObject = buildJsonObject {
                put("name", JsonPrimitive(tool.name))
                put("description", JsonPrimitive(tool.description))
            }
        }
    }

    /**
     * Get a cached response for a request, or null if not cached.
     *
     * @param request The request to get the cached response for
     * @return The cached response, or null if not cached
     */
    public suspend fun get(request: Request): List<Message.Response>?

    /**
     * Put a response in the cache for a request.
     *
     * @param request The request to cache the response for
     * @param response The response to cache
     */
    public suspend fun put(request: Request, response: List<Message.Response>)
}

/**
 * Get a cached response for a prompt with tools, or null if not cached.
 *
 * @param prompt The prompt to get the cached response for
 * @param tools The tools used with the prompt
 * @return The cached response, or null if not cached
 */
public suspend fun PromptCache.get(
    prompt: Prompt,
    tools: List<ToolDescriptor>,
    clock: Clock = Clock.System
): List<Message.Response>? {
    return get(PromptCache.Request.create(prompt, tools))?.let { messages ->
        val metaInfo = prompt
            .messages
            .filterIsInstance<Message.Response>()
            .lastOrNull()
            ?.metaInfo
            ?.copy(timestamp = clock.now())
            ?: ResponseMetaInfo.create(clock)

        messages.map { message -> message.copy(metaInfo) }
    }
}

/**
 * Put a response in the cache for a prompt with tools.
 *
 * @param prompt The prompt to cache the response for
 * @param tools The tools used with the prompt
 * @param response The response to cache
 */
public suspend fun PromptCache.put(prompt: Prompt, tools: List<ToolDescriptor>, response: List<Message.Response>) {
    put(PromptCache.Request.create(prompt, tools), response)
}
