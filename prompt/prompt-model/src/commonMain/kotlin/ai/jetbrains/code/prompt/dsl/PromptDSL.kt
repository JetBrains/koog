package ai.jetbrains.code.prompt.dsl

import ai.jetbrains.code.prompt.params.LLMParams

@DslMarker
public annotation class PromptDSL

@PromptDSL
public fun prompt(
    id: String,
    params: LLMParams = LLMParams(),
    build: PromptBuilder.() -> Unit
): Prompt {
    return Prompt.build(id, params, build)
}

public fun prompt(existing: Prompt, build: PromptBuilder.() -> Unit): Prompt {
    return Prompt.build(existing, build)
}
