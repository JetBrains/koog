package ai.koog.agents.features.pubsub.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.providers.NoPubSubProvider
import ai.koog.agents.features.pubsub.providers.PubSubProvider

/**
 * Configuration for the PubSub feature.
 *
 * This class allows you to configure how the PubSub feature behaves, including:
 * - Which PubSub provider to use (Redis, GCP PubSub, etc.)
 * - Which topics to automatically subscribe to
 * - Message filtering and processing options
 * - Publishing behavior for agent events
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(PubSub) {
 *         // Configure the PubSub provider
 *         provider = GCPPubSubProvider(
 *             projectId = "my-project",
 *             credentialsPath = "/path/to/service-account.json"
 *         )
 *         
 *         // Auto-subscribe to topics
 *         autoSubscribeTopics = listOf("agent-commands", "system-events")
 *         
 *         // Configure publishing of agent events
 *         publishAgentEvents = true
 *         agentEventTopic = "agent-lifecycle"
 *         
 *         // Add message processors to handle received messages
 *         addMessageProcessor(PubSubMessageLogWriter(logger))
 *         
 *         // Filter which events to publish
 *         publishFilter = { message ->
 *             message is AIAgentStartedEvent || message is AIAgentFinishedEvent
 *         }
 *     }
 * }
 * ```
 */
public class PubSubFeatureConfig : FeatureConfig() {
    
    /**
     * The PubSub provider to use for publishing and subscribing.
     * 
     * By default, uses [NoPubSubProvider] which is a no-op implementation.
     * Set this to a concrete provider like [GCPPubSubProvider] or [RedisPubSubProvider].
     */
    public var provider: PubSubProvider = NoPubSubProvider()
    
    /**
     * Topics to automatically subscribe to when the feature is installed.
     * 
     * The agent will start listening to these topics immediately and process
     * incoming messages through the configured message processors.
     */
    public var autoSubscribeTopics: List<String> = emptyList()
    
    /**
     * Whether to automatically publish agent lifecycle events to PubSub.
     * 
     * When enabled, events like agent started, finished, errors, etc. will be
     * published to the configured [agentEventTopic].
     */
    public var publishAgentEvents: Boolean = false
    
    /**
     * The topic to publish agent lifecycle events to.
     * 
     * Only used when [publishAgentEvents] is true.
     */
    public var agentEventTopic: String = "agent-events"
    
    /**
     * Whether to automatically publish tool call events to PubSub.
     * 
     * When enabled, tool calls and their results will be published to
     * the configured [toolEventTopic].
     */
    public var publishToolEvents: Boolean = false
    
    /**
     * The topic to publish tool events to.
     * 
     * Only used when [publishToolEvents] is true.
     */
    public var toolEventTopic: String = "tool-events"
    
    /**
     * Whether to automatically publish LLM call events to PubSub.
     * 
     * When enabled, LLM calls and responses will be published to
     * the configured [llmEventTopic].
     */
    public var publishLLMEvents: Boolean = false
    
    /**
     * The topic to publish LLM events to.
     * 
     * Only used when [publishLLMEvents] is true.
     */
    public var llmEventTopic: String = "llm-events"
    
    /**
     * Filter function to determine which messages should be published to PubSub.
     * 
     * This function is called for each feature message before publishing.
     * Return true to publish the message, false to skip it.
     * 
     * By default, all messages are published.
     */
    public var publishFilter: (FeatureMessage) -> Boolean = { true }
    
    /**
     * Filter function to determine which received PubSub messages should be processed.
     * 
     * This function is called for each message received from subscriptions.
     * Return true to process the message, false to skip it.
     * 
     * By default, all received messages are processed.
     */
    public var receiveFilter: (PubSubMessage) -> Boolean = { true }
    
    /**
     * Whether to acknowledge messages automatically after processing.
     * 
     * When true (default), messages are automatically acknowledged after being
     * processed by message processors. When false, you must manually acknowledge
     * messages in your message processors.
     */
    public var autoAcknowledge: Boolean = true
    
    /**
     * Maximum number of concurrent message processing operations.
     * 
     * This controls how many messages can be processed simultaneously.
     * Higher values increase throughput but use more resources.
     */
    public var maxConcurrentMessages: Int = 10
    
    /**
     * Additional provider-specific configuration options.
     * 
     * This map can be used to pass configuration that is specific to
     * the chosen PubSub provider implementation.
     */
    public var providerConfig: Map<String, Any> = emptyMap()
}