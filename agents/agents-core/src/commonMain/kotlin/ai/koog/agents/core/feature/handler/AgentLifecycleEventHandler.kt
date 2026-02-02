package ai.koog.agents.core.feature.handler

public interface AgentLifecycleEventHandler<TContext : AgentLifecycleEventContext, TResult : Any>

public fun interface AgentLifecycleContextEventHandler<TContext : AgentLifecycleEventContext> : AgentLifecycleEventHandler<TContext, Unit> {

    public suspend fun handle(eventContext: TContext)
}

public fun interface AgentLifecycleTransformEventHandler<TContext : AgentLifecycleEventContext, TResult : Any> : AgentLifecycleEventHandler<TContext, TResult> {

    public suspend fun handle(eventContext: TContext, entity: TResult): TResult
}
