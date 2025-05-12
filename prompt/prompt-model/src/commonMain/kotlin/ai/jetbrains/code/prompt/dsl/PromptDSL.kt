package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.params.LLMParams

@DslMarker
annotation class PromptDSL

@PromptDSL
fun prompt(
    id: String,
    params: LLMParams = LLMParams(),
    build: PromptBuilder.() -> Unit
): Prompt {
    return Prompt.build(id, params, build)
}

fun prompt(existing: Prompt, build: PromptBuilder.() -> Unit): Prompt {
    return Prompt.build(existing, build)
}
