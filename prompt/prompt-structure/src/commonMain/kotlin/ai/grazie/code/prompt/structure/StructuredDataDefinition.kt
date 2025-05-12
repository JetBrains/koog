package ai.grazie.code.prompt.structure

import ai.jetbrains.code.prompt.text.TextContentBuilder

public interface StructuredDataDefinition {
    public fun definition(builder: TextContentBuilder): TextContentBuilder
}
