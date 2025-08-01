package ai.koog.prompt.text

// TODO: should the library provide such a function?
// fun spaces(count: Int): String = " ".repeat(count)

/**
 * Extension function for [TextContentBuilder] that adds line numbering to the content.
 *
 * @param startLineNumber The starting line number (default is 1)
 * @param body The content builder function
 * @return The [TextContentBuilder] instance with numbered lines
 */
public fun TextContentBuilderBase<*>.numbered(
    startLineNumber: Int = 1,
    body: TextContentBuilderBase<*>.() -> Unit
): TextContentBuilderBase<*> {
    // Create a temporary builder to capture the content
    val tempBuilder = TextContentBuilder()
    tempBuilder.body()

    // Get the content and split it into lines
    val content = tempBuilder.build()
    val lines = content.lines()

    // Calculate the width needed for line numbers based on the last line number
    val maxLineNumber = startLineNumber + lines.size - 1
    val lineNumberWidth = maxLineNumber.toString().length

    // Add numbered lines to the original builder
    lines.forEachIndexed { index, line ->
        val lineNumber = startLineNumber + index
        // Format the line number with consistent width and ensure the separator is at the same position
        // Pad the line number to ensure consistent width using right alignment
        val formattedLineNumber = lineNumber.toString().padStart(lineNumberWidth, ' ')
        // Use a consistent format for the line number and separator
        text("$formattedLineNumber: $line")

        // Add newline only if it's not the last line or if the original content ends with a newline
        // and we're not at the last line yet
        val isLastLine = index == lines.size - 1
        if (!isLastLine) {
            newline()
        }
    }

    return this
}
