package ai.koog.agents.ext.tool.edit.diff

/**
 * Implementation of DiffSerialization that produces unified diff format according to POSIX standard.
 *
 * Unified diff format shows the changes in a combined view with context lines,
 * using '+' for additions, '-' for deletions, and ' ' for context lines.
 * File headers use '---' for the original file and '+++' for the new file.
 * Hunk headers use '@@ -start,count +start,count @@' format.
 *
 * @param T The type of elements being compared (e.g., Lines for line-based diffs)
 */
internal class UnifiedDiffSerializer<T> {
    companion object {
        val forStrings: UnifiedDiffSerializer<String> = UnifiedDiffSerializer<String>()
    }

    /**
     * Serializes a diff into the unified diff format.
     *
     * @param diff The diff to serialize
     * @param oldPath The path or identifier for the original file
     * @param newPath The path or identifier for the new file
     * @param contextSize The number of context lines to include before and after changes
     * @return A string containing the unified diff
     */
    fun serialize(diff: Diff<T>, oldPath: String = "original", newPath: String = "revised", contextSize: Int = 3): String {
        // Format paths according to POSIX standard if they don't already have a/ b/ prefixes
        val formattedOldPath = if (oldPath.startsWith("a/") || oldPath == "original") oldPath else "a/$oldPath"
        val formattedNewPath = if (newPath.startsWith("b/") || newPath == "revised") newPath else "b/$newPath"

        return diff.operations.toUnifiedDiff(contextSize, formattedOldPath, formattedNewPath)
    }

    /**
     * Represents a hunk in a unified diff.
     *
     * @property oldStart The starting line number in the original file (0-based)
     * @property oldCount The number of lines from the original file in this hunk
     * @property newStart The starting line number in the new file (0-based)
     * @property newCount The number of lines from the new file in this hunk
     * @property lines The formatted lines of the hunk, including context and changed lines
     */
    private data class DiffHunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val lines: List<String>
    )

    /**
     * Generates a unified diff from a list of diff operations.
     *
     * @param oldPath The path or identifier for the original file
     * @param newPath The path or identifier for the new file
     * @param contextSize The number of context lines to include before and after changes
     * @return A string containing the unified diff
     */
    fun <T> List<DiffOperation<T>>.toUnifiedDiff(
        contextSize: Int = 3,
        oldPath: String = "original",
        newPath: String = "revised",
    ): String {
        // If there are no operations or all operations are KEEP, return empty string
        if (isEmpty() || all { it.type == DiffOperation.Type.KEEP }) {
            return ""
        }

        val hunks = createHunks(contextSize)
        if (hunks.isEmpty()) {
            return ""
        }

        val result = StringBuilder()

        // Add the file headers
        result.append("--- $oldPath\n")
        result.append("+++ $newPath\n")

        // Add each hunk
        for (hunk in hunks) {
            // Add the hunk header
            result.append("@@ -${hunk.oldStart + 1},${hunk.oldCount} +${hunk.newStart + 1},${hunk.newCount} @@\n")

            // Add the hunk lines
            for (line in hunk.lines) {
                result.append(line).append("\n")
            }
        }

        return result.toString()
    }

    /**
     * Creates hunks from a list of diff operations.
     *
     * @param contextSize The number of context lines to include before and after changes
     * @return A list of [DiffHunk] objects
     */
    private fun <T> List<DiffOperation<T>>.createHunks(contextSize: Int): List<DiffHunk> {
        if (isEmpty()) {
            return emptyList()
        }

        // inside your UnifiedDiffSerializer class, wherever you build hunks:

        // 1) compute individual ranges around each non-keep (change) index:
        val changeIndexes = this.withIndex()
            .filter { it.value.type != DiffOperation.Type.KEEP }
            .map { it.index }

        if (changeIndexes.isEmpty()) return emptyList()

        val rawRanges = changeIndexes.map { idx ->
            val start = maxOf(0, idx - contextSize)
            val end = minOf(this.lastIndex, idx + contextSize)
            start..end
        }

        // 2) sort + merge so that overlapping or adjacent ranges collapse to one
        val mergedRanges = rawRanges
            .sortedBy { it.first }
            .fold(mutableListOf<IntRange>()) { acc, range ->
                if (acc.isEmpty()) {
                    acc.add(range)
                } else {
                    val last = acc.last()
                    if (range.first <= last.last + 1) {
                        // overlap or contiguous → extend the last range
                        acc[acc.lastIndex] = last.first..maxOf(last.last, range.last)
                    } else {
                        acc.add(range)
                    }
                }
                acc
            }

        val hunks = mutableListOf<DiffHunk>()
        // 3) now iterate over mergedRanges to emit each unified hunk exactly once
        for (hunk in mergedRanges) {
            val hunkOps = this.subList(hunk.first, hunk.last + 1)
            // … produce “@@ -l,k +l',k' @@” header based on hunk.first/hunkOps counts …
            hunks.add(finalizeHunk(hunkOps, hunk.first))
            // … then print each hunkOp without repeating any context line …
        }

        // Merge hunks that are close to each other
        return mergeCloseHunks(hunks, contextSize)
    }

    /**
     * Finalizes a hunk by calculating its metrics and formatting its lines.
     *
     * @param hunkOps The operations in the hunk
     * @param startIndex The starting index of the hunk in the original operations list
     * @return A [DiffHunk] object
     */
    private fun <T> List<DiffOperation<T>>.finalizeHunk(
        hunkOps: List<DiffOperation<T>>,
        startIndex: Int
    ): DiffHunk {
        // Calculate old and new line counts and starting positions
        var oldCount = 0
        var newCount = 0
        var oldStart = 0
        var newStart = 0

        // Count KEEP and DELETE operations before the hunk to determine start positions
        for (i in 0 until startIndex) {
            when (this[i].type) {
                DiffOperation.Type.KEEP -> {
                    oldStart++
                    newStart++
                }
                DiffOperation.Type.DELETE -> oldStart++
                DiffOperation.Type.INSERT -> newStart++
            }
        }

        // Format the lines and count old and new lines
        val formattedLines = mutableListOf<String>()
        for (op in hunkOps) {
            // Format the line based on operation type
            val formattedLine = when (op.type) {
                DiffOperation.Type.KEEP -> " ${op.value}"
                DiffOperation.Type.DELETE -> "-${op.value}"
                DiffOperation.Type.INSERT -> "+${op.value}"
            }
            formattedLines.add(formattedLine)

            when (op.type) {
                DiffOperation.Type.KEEP -> {
                    oldCount++
                    newCount++
                }
                DiffOperation.Type.DELETE -> oldCount++
                DiffOperation.Type.INSERT -> newCount++
            }
        }

        return DiffHunk(oldStart, oldCount, newStart, newCount, formattedLines)
    }

    /**
     * Merges hunks that are close to each other.
     *
     * @param hunks The list of hunks to merge
     * @param contextSize The number of context lines
     * @return A list of merged hunks
     */
    private fun mergeCloseHunks(hunks: List<DiffHunk>, contextSize: Int): List<DiffHunk> {
        if (hunks.size <= 1) {
            return hunks
        }

        val result = mutableListOf<DiffHunk>()
        var current = hunks[0]

        for (i in 1 until hunks.size) {
            val next = hunks[i]

            // If the hunks are close enough to merge (less than 2*contextSize lines between them)
            if (next.oldStart - (current.oldStart + current.oldCount) < contextSize * 2) {
                // Calculate the combined metrics
                val combinedOldCount = next.oldStart + next.oldCount - current.oldStart
                val combinedNewCount = next.newStart + next.newCount - current.newStart

                // Merge the lines, avoiding duplicates in the overlapping context
                val overlapStart = maxOf(0, next.oldStart - current.oldStart - current.oldCount)
                val combinedLines = current.lines.toMutableList()
                combinedLines.addAll(next.lines.drop(overlapStart))

                // Create the merged hunk
                current = DiffHunk(
                    current.oldStart,
                    combinedOldCount,
                    current.newStart,
                    combinedNewCount,
                    combinedLines
                )
            } else {
                // Hunks are not close enough to merge
                result.add(current)
                current = next
            }
        }

        // Add the last hunk
        result.add(current)

        return result
    }
}
