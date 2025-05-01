package ai.grazie.code.prompt.structure

import ai.jetbrains.code.prompt.params.LLMParams


abstract class StructuredData<TStruct>(
    val id: String,
    val examples: List<TStruct>,
    val schema: LLMParams.Schema
) : StructuredDataDefinition {
    abstract fun parse(text: String): TStruct
    abstract fun pretty(value: TStruct): String
}