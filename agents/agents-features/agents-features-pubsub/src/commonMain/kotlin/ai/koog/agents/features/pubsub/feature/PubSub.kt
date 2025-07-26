package ai.koog.agents.features.pubsub.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import ai.koog.agents.features.pubsub.message.*
import ai.koog.agents.features.pubsub.providers.PubSubException
import ai.koog.agents.features.pubsub.providers.ReceivedMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Feature that provides comprehensive PubSub capabilities for agent communication and event distribution.
 * 
 * The PubSub feature enables agents to:
 * - Publish agent lifecycle events to external systems
 * - Subscribe to command topics for remote agent control
 * - Distribute tool calls and LLM interactions across systems
 * - Integrate with message queues, event streams, and notification systems
 * 
 * This feature supports multiple PubSub providers (Redis, GCP PubSub, etc.) through a 
 * provider-agnostic interface, allowing seamless switching between implementations.
 * 
 * Example of installing PubSub to an agent:
 * ```kotlin
 * val agent = AIAgent(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(PubSub) {
 *         // Configure the PubSub provider
 *         provider = RedisPubSubProvider(
 *             redisUri = "redis://localhost:6379"
 *         )
 *         
 *         // Auto-subscribe to command topics
 *         autoSubscribeTopics = listOf("agent-commands", "system-events")
 *         
 *         // Publish agent lifecycle events
 *         publishAgentEvents = true
 *         agentEventTopic = "agent-lifecycle"
 *         
 *         // Publish tool and LLM events for monitoring
 *         publishToolEvents = true
 *         publishLLMEvents = true
 *         
 *         // Add message processors to handle received messages
 *         addMessageProcessor(PubSubMessageLogWriter(logger))
 *         addMessageProcessor(PubSubCommandProcessor())
 *     }
 * }
 * ```
 * 
 * Real-world use cases:
 * - **Distributed Agent Systems**: Coordinate multiple agents across services
 * - **Event-Driven Architecture**: Publish agent events to trigger downstream processes
 * - **Remote Agent Control**: Send commands to agents via PubSub topics
 * - **Monitoring & Analytics**: Stream agent activity to monitoring systems
 * - **Multi-Tenant SaaS**: Isolate agent events per tenant using topic routing
 */
public class PubSub {

    /**
     * Feature implementation for the PubSub functionality.
     * 
     * This companion object implements [AIAgentFeature] and provides methods for creating
     * an initial configuration and installing the PubSub feature in an agent pipeline.
     */
    @OptIn(ExperimentalUuidApi::class)
    public companion object Feature : AIAgentFeature<PubSubFeatureConfig, PubSub> {

        private val logger = KotlinLogging.logger { }
        private val json = Json { ignoreUnknownKeys = true }

        override val key: AIAgentStorageKey<PubSub> =
            AIAgentStorageKey("agents-features-pubsub")

        override fun createInitialConfig(): PubSubFeatureConfig = PubSubFeatureConfig()

        override fun install(
            config: PubSubFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${PubSub::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warn { "PubSub Feature. No message processors defined. Received messages will not be handled." }
            }

            val interceptContext = InterceptContext(this, PubSub())
            
            // Start subscription management coroutine
            val subscriptionJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                startSubscriptions(config)
            }

            // Store the job for cleanup
            pipeline.interceptAgentBeforeClosed(interceptContext) intercept@{ _ ->
                subscriptionJob.cancel()
                try {
                    config.provider.close()
                } catch (e: Exception) {
                    logger.warn(e) { "Error closing PubSub provider" }
                }
            }

            //region Intercept Agent Events

            if (config.publishAgentEvents) {
                pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{ eventContext ->
                    val event = AgentStartedPubSubEvent(
                        agentId = eventContext.agent.id,
                        runId = eventContext.runId,
                        strategyName = eventContext.strategy.name,
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.agentEventTopic, event)
                }

                pipeline.interceptAgentFinished(interceptContext) intercept@{ eventContext ->
                    val event = AgentFinishedPubSubEvent(
                        agentId = eventContext.agentId,
                        runId = eventContext.runId,
                        result = eventContext.result?.toString(),
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.agentEventTopic, event)
                }

                pipeline.interceptAgentRunError(interceptContext) intercept@{ eventContext ->
                    val event = AgentErrorPubSubEvent(
                        agentId = eventContext.agentId,
                        runId = eventContext.runId,
                        error = eventContext.throwable.message ?: "Unknown error",
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.agentEventTopic, event)
                }
            }

            //endregion Intercept Agent Events

            //region Intercept Tool Events

            if (config.publishToolEvents) {
                pipeline.interceptToolCall(interceptContext) intercept@{ eventContext ->
                    val event = ToolCallPubSubEvent(
                        runId = eventContext.runId ?: "unknown",
                        toolCallId = eventContext.toolCallId ?: "unknown",
                        toolName = eventContext.tool.name,
                        toolArgs = eventContext.toolArgs.toString(),
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.toolEventTopic, event)
                }

                pipeline.interceptToolCallResult(interceptContext) intercept@{ eventContext ->
                    val event = ToolResultPubSubEvent(
                        runId = eventContext.runId ?: "unknown",
                        toolCallId = eventContext.toolCallId ?: "unknown",
                        toolName = eventContext.tool.name,
                        result = eventContext.result?.toString() ?: "null",
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.toolEventTopic, event)
                }
            }

            //endregion Intercept Tool Events

            //region Intercept LLM Events

            if (config.publishLLMEvents) {
                pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ eventContext ->
                    val event = LLMCallStartPubSubEvent(
                        runId = eventContext.runId,
                        model = eventContext.model.toString(),
                        promptLength = eventContext.prompt.toString().length,
                        toolCount = eventContext.tools.size,
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.llmEventTopic, event)
                }

                pipeline.interceptAfterLLMCall(interceptContext) intercept@{ eventContext ->
                    val event = LLMCallEndPubSubEvent(
                        runId = eventContext.runId,
                        model = eventContext.model.toString(),
                        responseCount = eventContext.responses.size,
                        timestamp = System.currentTimeMillis()
                    )
                    publishEvent(config, config.llmEventTopic, event)
                }
            }

            //endregion Intercept LLM Events
        }

        //region Private Methods

        private suspend fun startSubscriptions(config: PubSubFeatureConfig) {
            if (config.autoSubscribeTopics.isEmpty()) {
                logger.debug { "No auto-subscribe topics configured" }
                return
            }

            try {
                val messageFlow = config.provider.subscribe(config.autoSubscribeTopics)
                
                messageFlow
                    .onEach { receivedMessage ->
                        processReceivedMessage(config, receivedMessage)
                    }
                    .catch { error ->
                        logger.error(error) { "Error in PubSub subscription flow" }
                        
                        val errorEvent = PubSubErrorEvent(
                            eventId = Uuid.random().toString(),
                            topic = "",
                            operation = "subscribe",
                            error = error.message ?: "Unknown subscription error"
                        )
                        config.messageProcessor.onMessageForEachSafe(errorEvent)
                    }
                    .collect { /* Flow is handled in onEach */ }
                    
            } catch (e: Exception) {
                logger.error(e) { "Failed to start PubSub subscriptions" }
            }
        }

        private suspend fun processReceivedMessage(config: PubSubFeatureConfig, receivedMessage: ReceivedMessage) {
            try {
                // Create PubSub message from received message
                val pubSubMessage = PubSubStringMessage(
                    topic = receivedMessage.topic,
                    content = receivedMessage.content,
                    attributes = receivedMessage.attributes
                )

                // Apply receive filter
                if (!config.receiveFilter(pubSubMessage)) {
                    if (config.autoAcknowledge) {
                        receivedMessage.acknowledge()
                    }
                    return
                }

                // Process through message processors
                config.messageProcessor.onMessageForEachSafe(pubSubMessage)

                // Create received event
                val receivedEvent = MessageReceivedEvent(
                    eventId = Uuid.random().toString(),
                    topic = receivedMessage.topic,
                    messageId = receivedMessage.messageId,
                    content = receivedMessage.content,
                    attributes = receivedMessage.attributes
                )
                config.messageProcessor.onMessageForEachSafe(receivedEvent)

                // Auto-acknowledge if configured
                if (config.autoAcknowledge) {
                    receivedMessage.acknowledge()
                }

            } catch (e: Exception) {
                logger.error(e) { "Error processing received PubSub message: ${receivedMessage.messageId}" }
                
                try {
                    receivedMessage.nack()
                } catch (nackError: Exception) {
                    logger.error(nackError) { "Failed to nack message: ${receivedMessage.messageId}" }
                }
            }
        }

        private suspend fun publishEvent(config: PubSubFeatureConfig, topic: String, event: Any) {
            try {
                val eventJson = json.encodeToString(event)
                val messageId = config.provider.publish(topic, eventJson)
                
                // Create published event
                val publishedEvent = MessagePublishedEvent(
                    eventId = Uuid.random().toString(),
                    topic = topic,
                    messageId = messageId
                )
                config.messageProcessor.onMessageForEachSafe(publishedEvent)
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to publish event to topic: $topic" }
                
                val errorEvent = PubSubErrorEvent(
                    eventId = Uuid.random().toString(),
                    topic = topic,
                    operation = "publish",
                    error = e.message ?: "Unknown publish error"
                )
                config.messageProcessor.onMessageForEachSafe(errorEvent)
            }
        }

        //endregion Private Methods
    }
}

//region PubSub Event Data Classes

/**
 * Event published when an agent starts.
 */
@kotlinx.serialization.Serializable
private data class AgentStartedPubSubEvent(
    val agentId: String,
    val runId: String,
    val strategyName: String,
    val timestamp: Long
)

/**
 * Event published when an agent finishes.
 */
@kotlinx.serialization.Serializable
private data class AgentFinishedPubSubEvent(
    val agentId: String,
    val runId: String,
    val result: String?,
    val timestamp: Long
)

/**
 * Event published when an agent encounters an error.
 */
@kotlinx.serialization.Serializable  
private data class AgentErrorPubSubEvent(
    val agentId: String,
    val runId: String,
    val error: String,
    val timestamp: Long
)

/**
 * Event published when a tool is called.
 */
@kotlinx.serialization.Serializable
private data class ToolCallPubSubEvent(
    val runId: String,
    val toolCallId: String,
    val toolName: String,
    val toolArgs: String,
    val timestamp: Long
)

/**
 * Event published when a tool call returns a result.
 */
@kotlinx.serialization.Serializable
private data class ToolResultPubSubEvent(
    val runId: String,
    val toolCallId: String,
    val toolName: String,
    val result: String,
    val timestamp: Long
)

/**
 * Event published when an LLM call starts.
 */
@kotlinx.serialization.Serializable
private data class LLMCallStartPubSubEvent(
    val runId: String,
    val model: String,
    val promptLength: Int,
    val toolCount: Int,
    val timestamp: Long
)

/**
 * Event published when an LLM call ends.
 */
@kotlinx.serialization.Serializable
private data class LLMCallEndPubSubEvent(
    val runId: String,
    val model: String,
    val responseCount: Int,
    val timestamp: Long
)

//endregion PubSub Event Data Classes