package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Provides a [SerializersModule] that handles polymorphic serialization and deserialization for various events
 * and messages associated with features, agents, and strategies.
 *
 * This module supports polymorphic serialization for the following base classes:
 * - [FeatureMessage]
 * - [FeatureEvent]
 * - [DefinedFeatureEvent]
 *
 * It registers the concrete subclasses of these base classes for serialization and deserialization:
 * - [AIAgentCreateEvent]
 * - [AIAgentStartedEvent]
 * - [AIAgentFinishedEvent]
 * - [AIAgentStrategyStartEvent]
 * - [AIAgentStrategyFinishedEvent]
 * - [AIAgentNodeExecutionStartEvent]
 * - [AIAgentNodeExecutionEndEvent]
 * - [LLMCallStartEvent]
 * - [LLMCallWithToolsStartEvent]
 * - [LLMCallEndEvent]
 * - [LLMCallWithToolsEndEvent]
 *
 * This configuration enables proper handling of the diverse event types encountered in the system by ensuring
 * that the polymorphic serialization framework can correctly serialize and deserialize each subclass.
 */
public val agentFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }

            polymorphic(FeatureEvent::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }

            polymorphic(DefinedFeatureEvent::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }
        }
