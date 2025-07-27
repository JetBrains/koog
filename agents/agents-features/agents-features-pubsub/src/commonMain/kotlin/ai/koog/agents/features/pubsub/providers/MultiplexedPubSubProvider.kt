package ai.koog.agents.features.pubsub.providers

import ai.koog.agents.features.pubsub.message.PubSubMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A PubSub provider that delegates to multiple underlying providers based on topic routing rules.
 *
 * This provider enables sophisticated multi-provider coordination for different types of messages:
 * - **Local coordination**: Use InMemoryPubSubProvider for fast same-process agent communication
 * - **Cross-process**: Use RedisPubSubProvider for low-latency LAN messaging  
 * - **Cross-environment**: Use GCPPubSubProvider for cloud-scale coordination
 * - **Development**: Use LocalFilePubSubProvider for cross-process dev environments
 *
 * Example usage:
 * ```kotlin
 * val provider = MultiplexedPubSubProvider {
 *     // Local agent coordination (fast, same-process)
 *     route("agent.local.*") to InMemoryPubSubProvider()
 *     route("alerts.emergency") to InMemoryPubSubProvider()
 *     
 *     // Cross-environment coordination (distributed)
 *     route("agent.planner.*") to GCPPubSubProvider(...)
 *     route("tasks.*") to RedisPubSubProvider(...)
 *     
 *     // Default fallback
 *     defaultProvider = RedisPubSubProvider(...)
 * }
 * ```
 *
 * **Topic Routing Rules:**
 * - Exact match: `"agent.builder.tasks"` matches exactly that topic
 * - Wildcard match: `"agent.*"` matches any topic starting with "agent."
 * - Prefix match: `"alerts"` matches any topic starting with "alerts"
 * - Rules are evaluated in order of specificity (exact → wildcard → prefix)
 *
 * **Multi-Provider Scenarios:**
 * 1. **Mixed local/distributed topologies**: Fast local coordination + distributed planning
 * 2. **Environment-based routing**: Different providers for dev/staging/prod environments
 * 3. **Performance optimization**: Route high-frequency messages to fast local providers
 * 4. **Cost optimization**: Use cheaper providers for non-critical message types
 * 5. **Reliability tiers**: Route critical messages to highly available providers
 *
 * **Implementation Notes:**
 * - Publishing routes to exactly one provider per topic (no fan-out)
 * - Subscriptions aggregate flows from all relevant providers
 * - Health info combines status from all underlying providers
 * - Connection status is healthy if any provider is connected
 * - Proper cleanup delegates to all underlying providers
 */
public class MultiplexedPubSubProvider(
    configure: MultiplexedPubSubProviderConfig.() -> Unit
) : PubSubProvider {
    
    private val config = MultiplexedPubSubProviderConfig().apply(configure)
    private val routingMutex = Mutex()
    
    init {
        require(config.routes.isNotEmpty() || config.defaultProvider != null) {
            "MultiplexedPubSubProvider requires at least one route or a default provider"
        }
    }
    
    override suspend fun publish(message: PubSubMessage): String? {
        val provider = selectProviderForTopic(message.topic)
            ?: throw PubSubException("publish", message.topic, "No provider configured for topic: ${message.topic}")
        
        return provider.publish(message)
    }
    
    override suspend fun publish(
        topic: String,
        content: String,
        attributes: Map<String, String>
    ): String? {
        val provider = selectProviderForTopic(topic)
            ?: throw PubSubException("publish", topic, "No provider configured for topic: $topic")
        
        return provider.publish(topic, content, attributes)
    }
    
    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return subscribe(listOf(topic))
    }
    
    override suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage> {
        val providerTopics = mutableMapOf<PubSubProvider, MutableList<String>>()
        
        // Group topics by their assigned providers
        topics.forEach { topic ->
            val provider = selectProviderForTopic(topic)
                ?: throw PubSubException("subscribe", topic, "No provider configured for topic: $topic")
            
            providerTopics.getOrPut(provider) { mutableListOf() }.add(topic)
        }
        
        // Create subscription flows for each provider
        val flows = providerTopics.map { (provider, providerTopics) ->
            provider.subscribe(providerTopics)
        }
        
        // Merge all flows into a single flow
        return when (flows.size) {
            0 -> kotlinx.coroutines.flow.emptyFlow()
            1 -> flows.first()
            else -> merge(*flows.toTypedArray())
        }
    }
    
    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        val provider = selectProviderForTopic(topic)
            ?: throw PubSubException("unsubscribe", topic, "No provider configured for topic: $topic")
        
        provider.unsubscribe(topic, subscriptionId)
    }
    
    override suspend fun isConnected(): Boolean {
        val allProviders = getAllProviders()
        
        // Consider connected if any provider is connected
        return allProviders.any { provider ->
            try {
                provider.isConnected()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun getHealthInfo(): Map<String, Any> {
        val allProviders = getAllProviders()
        val providerHealth = mutableMapOf<String, Any>()
        
        allProviders.forEachIndexed { index, provider ->
            try {
                val healthInfo = provider.getHealthInfo()
                providerHealth["provider_$index"] = healthInfo
            } catch (e: Exception) {
                providerHealth["provider_$index"] = mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "connected" to false
                )
            }
        }
        
        val connectedProviders = allProviders.count { provider ->
            try { provider.isConnected() } catch (e: Exception) { false }
        }
        
        return mapOf(
            "provider" to "multiplexed",
            "totalProviders" to allProviders.size,
            "connectedProviders" to connectedProviders,
            "connected" to (connectedProviders > 0),
            "healthy" to (connectedProviders > 0),
            "routes" to config.routes.size,
            "hasDefaultProvider" to (config.defaultProvider != null),
            "providers" to providerHealth
        )
    }
    
    override fun close() {
        val allProviders = getAllProviders()
        
        allProviders.forEach { provider ->
            try {
                provider.close()
            } catch (e: Exception) {
                // Log error but continue closing other providers
                println("Warning: Error closing provider: ${e.message}")
            }
        }
    }
    
    /**
     * Selects the appropriate provider for a given topic based on routing rules.
     * 
     * Rules are evaluated in order of specificity:
     * 1. Exact topic match
     * 2. Wildcard pattern match (topic.*)
     * 3. Prefix match (topic starts with pattern)
     * 4. Default provider (if configured)
     * 
     * @param topic The topic to route
     * @return The selected provider, or null if no provider matches
     */
    private suspend fun selectProviderForTopic(topic: String): PubSubProvider? {
        return routingMutex.withLock {
            // First try exact match
            config.routes[topic]?.let { return@withLock it }
            
            // Then try wildcard patterns (e.g., "agent.*" matches "agent.builder.tasks")
            for ((pattern, provider) in config.routes) {
                if (pattern.endsWith("*")) {
                    val prefix = pattern.removeSuffix("*")
                    if (topic.startsWith(prefix)) {
                        return@withLock provider
                    }
                }
            }
            
            // Finally try prefix matches (e.g., "agent" matches "agent.builder.tasks")
            for ((pattern, provider) in config.routes) {
                if (!pattern.endsWith("*") && topic.startsWith(pattern)) {
                    return@withLock provider
                }
            }
            
            // Fall back to default provider
            config.defaultProvider
        }
    }
    
    /**
     * Gets all unique providers configured in this multiplexer.
     */
    private fun getAllProviders(): Set<PubSubProvider> {
        val providers = mutableSetOf<PubSubProvider>()
        providers.addAll(config.routes.values)
        config.defaultProvider?.let { providers.add(it) }
        return providers
    }
}

/**
 * Configuration builder for MultiplexedPubSubProvider.
 */
public class MultiplexedPubSubProviderConfig {
    /**
     * Map of topic patterns to their assigned providers.
     * 
     * Patterns can be:
     * - Exact: "agent.builder.tasks"
     * - Wildcard: "agent.*" (matches anything starting with "agent.")
     * - Prefix: "agent" (matches anything starting with "agent")
     */
    public val routes: MutableMap<String, PubSubProvider> = mutableMapOf()
    
    /**
     * Default provider to use when no routing rule matches a topic.
     */
    public var defaultProvider: PubSubProvider? = null
    
    /**
     * Routes messages for the specified topic pattern to the given provider.
     * 
     * @param pattern Topic pattern (exact, wildcard with *, or prefix)
     * @param provider Provider to handle messages for this pattern
     */
    public infix fun route(pattern: String): RouteBuilder = RouteBuilder(pattern)
    
    /**
     * Builder for individual route configuration.
     */
    public inner class RouteBuilder(private val pattern: String) {
        /**
         * Assigns the provider for this route.
         */
        public infix fun to(provider: PubSubProvider) {
            routes[pattern] = provider
        }
    }
}