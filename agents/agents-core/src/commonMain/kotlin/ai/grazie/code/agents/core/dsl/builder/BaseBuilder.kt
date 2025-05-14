package ai.grazie.code.agents.core.dsl.builder

@DslMarker
public annotation class LocalAgentBuilderMarker

@LocalAgentBuilderMarker
public interface BaseBuilder<T> {
    public fun build(): T
}
