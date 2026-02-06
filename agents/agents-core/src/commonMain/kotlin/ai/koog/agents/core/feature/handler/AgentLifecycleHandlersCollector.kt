package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.entity.AIAgentStorageKey

internal class AgentLifecycleHandlersCollector {

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

    private val featureToHandlersMap =
        mutableMapOf<AIAgentStorageKey<*>, FeatureEventHandlers>()

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
