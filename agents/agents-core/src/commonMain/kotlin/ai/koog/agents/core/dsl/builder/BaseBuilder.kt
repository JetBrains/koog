package ai.koog.agents.core.dsl.builder

@DslMarker
public annotation class AIAgentBuilderMarker

@AIAgentBuilderMarker
public interface BaseBuilder<T> {
    public fun build(): T
}
