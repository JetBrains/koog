package ai.koog.agents.ext.tool.edit.diff

internal fun DiffAlgorithm<String>.diff(old: String, new: String): Diff<String> {
    return diff(old.split("\n"), new.split("\n"))
}

internal fun Diff<String>.toUnifiedDiff(): String = UnifiedDiffSerializer.forStrings.serialize(this)
