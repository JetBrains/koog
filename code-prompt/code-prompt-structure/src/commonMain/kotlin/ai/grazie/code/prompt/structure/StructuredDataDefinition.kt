package ai.grazie.code.prompt.structure

import ai.jetbrains.code.prompt.text.TextContentBuilder

interface StructuredDataDefinition {
    fun definition(builder: TextContentBuilder): TextContentBuilder
}