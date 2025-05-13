package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val agentFeatureMessageSerializersModule: SerializersModule
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