package ai.koog.agents.ext.tool.edit.patch

import ai.koog.agents.core.tools.annotations.LLMDescription

internal data class FilePatch(
    @property:LLMDescription("The original text to be modified or removed. If empty, the file patch represents a rewrite of whole file")
    val original: String,
    @property:LLMDescription(description = "The replacement text. If empty, the file patch represents a deletion.")
    val replacement: String
) {
    val isDelete: Boolean
        get() = original.isNotEmpty() && replacement.isEmpty()
    val isReplace: Boolean
        get() = original.isNotEmpty() && replacement.isNotEmpty() && original != replacement
    val isRewrite: Boolean
        get() = original.isEmpty() && replacement.isNotEmpty()
}
