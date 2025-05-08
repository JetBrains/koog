package ai.grazie.code.agents.core.dsl.builder

@DslMarker
annotation class LocalAgentBuilderMarker

@LocalAgentBuilderMarker
interface BaseBuilder<T> {
    fun build(): T
}
