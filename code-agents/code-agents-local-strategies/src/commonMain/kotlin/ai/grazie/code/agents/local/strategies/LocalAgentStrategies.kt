package ai.grazie.code.agents.local.strategies

import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.strategies.impls.StageEssayWriting


@Suppress("FunctionName")
object LocalAgentStrategies {
    fun EssayWriter() = strategy("web-search-essay") {
        StageEssayWriting()
    }
}