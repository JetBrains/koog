package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.feature.model.AgentCreateEvent
import ai.grazie.code.agents.core.feature.model.DefinedFeatureEvent
import ai.grazie.code.agents.core.feature.model.LLMCallEndEvent
import ai.grazie.code.agents.core.feature.model.LLMCallStartEvent
import ai.grazie.code.agents.core.feature.model.LLMCallWithToolsEndEvent
import ai.grazie.code.agents.core.feature.model.LLMCallWithToolsStartEvent
import ai.grazie.code.agents.core.feature.model.NodeExecutionEndEvent
import ai.grazie.code.agents.core.feature.model.NodeExecutionStartEvent
import ai.grazie.code.agents.core.feature.model.StrategyStartEvent
import ai.grazie.code.agents.core.feature.model.ToolCallsEndEvent
import ai.grazie.code.agents.core.feature.model.ToolCallsStartEvent
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val agentFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }

            polymorphic(FeatureEvent::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }

            polymorphic(DefinedFeatureEvent::class) {
                subclass(AgentCreateEvent::class, AgentCreateEvent.serializer())
                subclass(StrategyStartEvent::class, StrategyStartEvent.serializer())
                subclass(NodeExecutionStartEvent::class, NodeExecutionStartEvent.serializer())
                subclass(NodeExecutionEndEvent::class, NodeExecutionEndEvent.serializer())
                subclass(LLMCallStartEvent::class, LLMCallStartEvent.serializer())
                subclass(LLMCallWithToolsStartEvent::class, LLMCallWithToolsStartEvent.serializer())
                subclass(LLMCallEndEvent::class, LLMCallEndEvent.serializer())
                subclass(LLMCallWithToolsEndEvent::class, LLMCallWithToolsEndEvent.serializer())
                subclass(ToolCallsStartEvent::class, ToolCallsStartEvent.serializer())
                subclass(ToolCallsEndEvent::class, ToolCallsEndEvent.serializer())
            }
        }