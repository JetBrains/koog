package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
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
 * - [AgentCreateEvent]
 * - [AgentStartedEvent]
 * - [AgentFinishedEvent]
 * - [StrategyStartEvent]
 * - [StrategyFinishedEvent]
 * - [NodeExecutionStartEvent]
 * - [NodeExecutionEndEvent]
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
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(AgentStartedEvent::class, AgentStartedEvent.serializer())
                subclass(AgentFinishedEvent::class, AgentFinishedEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(StrategyFinishedEvent::class, StrategyFinishedEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }

            polymorphic(FeatureEvent::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(AgentStartedEvent::class, AgentStartedEvent.serializer())
                subclass(AgentFinishedEvent::class, AgentFinishedEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(StrategyFinishedEvent::class, StrategyFinishedEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }

            polymorphic(DefinedFeatureEvent::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(AgentStartedEvent::class, AgentStartedEvent.serializer())
                subclass(AgentFinishedEvent::class, AgentFinishedEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(StrategyFinishedEvent::class, StrategyFinishedEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
            }
        }
