package ai.grazie.code.agents.tools.registry.utils

/**
 * Formats a list of strings by appending line numbers to each line, starting from the specified line number.
 *
 * @param lines The list of strings to be formatted with line numbers.
 * @param startLineNumber The line number to start numbering from.
 * @return A single string where each line is prefixed with its corresponding line number.
 */
fun formatLinesWithNumbers(lines: List<String>, startLineNumber: Int): String {
    return lines
        .mapIndexed { index, lineText -> "${startLineNumber + index}: $lineText" }
        .joinToString("\n")
}
