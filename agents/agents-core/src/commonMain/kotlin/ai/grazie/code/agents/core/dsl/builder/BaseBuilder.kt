package ai.grazie.code.agents.core.dsl.builder

@DslMarker
annotation class AgentBuilderMarker

@AgentBuilderMarker
interface BaseBuilder<T> {
    fun build(): T
}
