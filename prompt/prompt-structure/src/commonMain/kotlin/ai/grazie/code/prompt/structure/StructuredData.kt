package ai.grazie.code.prompt.structure

import ai.jetbrains.code.prompt.params.LLMParams

public abstract class StructuredData<TStruct>(
    public val id: String,
    public val examples: List<TStruct>,
    public val schema: LLMParams.Schema
) : StructuredDataDefinition {
    public abstract fun parse(text: String): TStruct
    public abstract fun pretty(value: TStruct): String
}
