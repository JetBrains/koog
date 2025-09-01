package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionErrorEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.AIAgentRunErrorEvent
import ai.koog.agents.core.feature.model.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.DefinedFeatureEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.core.feature.model.ToolCallFailureEvent
import ai.koog.agents.core.feature.model.ToolCallResultEvent
import ai.koog.agents.core.feature.model.ToolValidationErrorEvent
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
 * - [AIAgentStartedEvent] - Fired when an AI agent starts execution
 * - [AIAgentFinishedEvent] - Fired when an AI agent completes execution
 * - [AIAgentBeforeCloseEvent] - Fired before an AI agent is closed
 * - [AIAgentRunErrorEvent] - Fired when an AI agent encounters a runtime error
 * - [AIAgentStrategyStartEvent] - Fired when an AI agent strategy begins
 * - [AIAgentStrategyFinishedEvent] - Fired when an AI agent strategy completes
 * - [AIAgentNodeExecutionStartEvent] - Fired when a node execution starts
 * - [AIAgentNodeExecutionEndEvent] - Fired when a node execution ends
 * - [ToolCallEvent] - Fired when a tool is called
 * - [ToolValidationErrorEvent] - Fired when tool validation fails
 * - [ToolCallFailureEvent] - Fired when a tool call fails
 * - [ToolCallResultEvent] - Fired when a tool call returns a result
 * - [BeforeLLMCallEvent] - Fired before making an LLM call
 * - [AfterLLMCallEvent] - Fired after completing an LLM call
 *
 * This configuration enables proper handling of the diverse event types encountered in the system by ensuring
 * that the polymorphic serialization framework can correctly serialize and deserialize each subclass.
 */
public val agentFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {
        polymorphic(FeatureMessage::class) {
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }

        polymorphic(FeatureEvent::class) {
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }

        polymorphic(DefinedFeatureEvent::class) {
            subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
            subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
            subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
            subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
            subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
            subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
            subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
            subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
            subclass(AIAgentNodeExecutionErrorEvent::class, AIAgentNodeExecutionErrorEvent.serializer())
            subclass(ToolCallEvent::class, ToolCallEvent.serializer())
            subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
            subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
            subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
            subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
            subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
        }
    }
