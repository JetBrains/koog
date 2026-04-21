package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.entity.AIAgentStorageKey

/**
 * Collects and manages lifecycle event handlers associated with AI agents and features.
 *
 * This class serves as a centralized registry for associating handlers with specific
 * lifecycle event types and their corresponding features. Handlers are grouped by the
 * associated feature key and further categorized by the event type they handle.
 */
internal class AgentLifecycleHandlersCollector {

    /**
     * The internal class maintains a mapping between event types and their corresponding handlers, enabling
     * the addition and retrieval of event handlers for different agent lifecycle events.
     *
     * @property featureKey The key representing the feature associated with these event handlers.
     */
    private class FeatureEventHandlers(
        val featureKey: AIAgentStorageKey<*>
    ) {
        private val handlersByEventType = mutableMapOf<AgentLifecycleEventType, MutableList<AgentLifecycleEventHandler<*, *>>>()

        fun <TContext : AgentLifecycleEventContext, TReturn : Any> addHandler(
            eventType: AgentLifecycleEventType,
            handler: AgentLifecycleEventHandler<TContext, TReturn>
        ) {
            handlersByEventType.getOrPut(eventType) { mutableListOf() }
                .add(handler)
        }

        fun <TContext : AgentLifecycleEventContext, TReturn : Any> getHandlers(
            eventType: AgentLifecycleEventType
        ): List<AgentLifecycleEventHandler<TContext, TReturn>> {
            return handlersByEventType[eventType]?.mapNotNull { handler ->
                @Suppress("UNCHECKED_CAST")
                handler as? AgentLifecycleEventHandler<TContext, TReturn>
            } ?: emptyList()
        }
    }

    private val featureToHandlersMap = mutableMapOf<AIAgentStorageKey<*>, FeatureEventHandlers>()

    internal fun <TContext : AgentLifecycleEventContext, TReturn : Any> addHandlerForFeature(
        featureKey: AIAgentStorageKey<*>,
        eventType: AgentLifecycleEventType,
        handler: AgentLifecycleEventHandler<TContext, TReturn>
    ) {
        featureToHandlersMap.getOrPut(featureKey) { FeatureEventHandlers(featureKey) }
            .addHandler(eventType, handler)
    }

    internal fun <TContext : AgentLifecycleEventContext, TReturn : Any> getHandlersForEvent(
        eventType: AgentLifecycleEventType
    ): Map<AIAgentStorageKey<*>, List<AgentLifecycleEventHandler<TContext, TReturn>>> {
        val handlers = featureToHandlersMap
            .mapValues { (_, featureHandlers) -> featureHandlers.getHandlers<TContext, TReturn>(eventType) }
            .filterValues { it.isNotEmpty() }

        return handlers
    }
}
