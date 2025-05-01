package ai.grazie.code.agents.local.dsl.builders

@DslMarker
annotation class LocalAgentBuilderMarker

@LocalAgentBuilderMarker
interface BaseBuilder<T> {
    fun build(): T
}
