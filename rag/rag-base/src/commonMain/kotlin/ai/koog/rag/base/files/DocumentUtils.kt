package ai.koog.rag.base.files

import ai.koog.rag.base.files.DocumentProvider.Position

/**
 * Converts an integer representing a character offset to a `Position` object, which includes
 * line and column information within the given `content`.
 *
 * @param content The multi-line text content in which the position is calculated.
 * @return A `Position` object that represents the line and column corresponding to the given offset.
 */
public fun Int.toPosition(content: CharSequence): Position {
    val lineRanges = content.lineRanges(true)
    var offset = 0L

    for ((lineNumber, range) in lineRanges.withIndex()) {
        if (offset + range.length > this) {
            val character = (this - offset).toInt()
            return Position(lineNumber, character)
        }
        offset += range.length
    }

    // return a position at the end of the file if offset is out of bounds
    return Position(lineRanges.count(), 0)
}

/**
 * Converts a position in a text document, defined by a line and column,
 * into its corresponding offset in the provided content.
 *
 * @param content The text content from which the offset is calculated.
 *                The content should consist of multiple lines of text.
 * @return The calculated offset corresponding to the given position.
 * @throws IllegalArgumentException If the line number specified in the position
 *                                  exceeds the total number of lines in the content.
 */
internal fun Position.toOffset(content: CharSequence): Int {
    val lineRanges = content.lineRanges(true)
    var offset = 0

    for ((lineNumber, range) in lineRanges.withIndex()) {
        if (lineNumber == line) {
            offset += column
            break
        } else if (lineNumber >= line) {
            throw IllegalArgumentException("Invalid position: line $line exceeds file line count")
        }
        offset += range.length
    }
    return offset
}

/**
 * Retrieves the beginning index of the current line in the given `CharSequence`, relative to the specified offset.
 * This method works by scanning backwards from the provided offset until it encounters a line break character.
 *
 * @param offset The position in the `CharSequence` from which to start determining the beginning of the current line.
 *               The offset should be a valid index within the bounds of the input `CharSequence`.
 * @return The index of the first character of the current line. Returns 0 if no preceding line break is found.
 */
internal fun CharSequence.getCurrentLineBeginning(offset: Int): Int {
    var current = offset - 1
    while (current >= 0) {
        if (this[current].isLineBreak()) {
            return current + 1
        }
        current--
    }
    return 0
}

/**
 * Determines the end index (exclusive) of the current line in the character sequence
 * starting from the specified offset.
 *
 * The behavior of this method depends on whether line separators should be included:
 * - If `includeLineSeparators` is `true`, the end index will include the line separator.
 * - If `includeLineSeparators` is `false`, the end index will exclude the line separator.
 *
 * @param offset The starting index to analyze the current line from. Must be non-negative
 *               and within the bounds of the character sequence.
 * @param includeLineSeparators A flag indicating whether line separators should be included
 *                              in determining the end of the current line.
 * @return The index (exclusive) marking the end of the current line in the character sequence.
 */
internal fun CharSequence.getCurrentLineEndExclusive(offset: Int, includeLineSeparators: Boolean): Int {
    return if (includeLineSeparators) {
        getCurrentLineEndExclusiveWithLineBreaks(offset)
    } else {
        getCurrentLineEndExclusiveWithoutLineBreaks(offset)
    }
}

/**
 * Generates a sequence of ranges representing the start and end indexes of each line
 * in the given character sequence.
 *
 * Each range corresponds to a single line, and whether line separators (e.g., newline characters)
 * are included in the ranges can be controlled by the `includeLineSeparators` parameter.
 *
 * @param includeLineSeparators Determines whether the line ranges should include line separator characters.
 * If true, the ranges will include newline characters; otherwise, they will exclude them.
 * @param after The starting index from which to begin processing lines. Defaults to 0.
 * If specified, only lines starting after this index will be considered.
 */
internal fun CharSequence.lineRanges(includeLineSeparators: Boolean, after: Int = 0): Sequence<TextRange> = sequence {
    val str = this@lineRanges
    var start = after

    while (start != length) {
        val endExclusive = str.getCurrentLineEndExclusive(start, includeLineSeparators)

        yield(TextRange(start, endExclusive))

        start = if (includeLineSeparators) {
            endExclusive
        } else {
            str.getCurrentLineEndExclusive(endExclusive, true)
        }
    }
}

/**
 * Determines whether the character is a line break.
 *
 * A line break is identified if the character is either `\n` (LF) or `\r` (CR).
 *
 * @return `true` if the character is a line break, `false` otherwise.
 */
internal fun Char.isLineBreak(): Boolean {
    return this == '\n' || this == '\r'
}

/**
 * Finds the end position of the current line in the character sequence, including any line break character(s),
 * starting from the given offset. If no line break is found, returns the length of the character sequence.
 *
 * @param offset The starting index to search for the end of the current line.
 * @return The index of the character after the line break, or the length of the character sequence if no line break is found.
 */
private fun CharSequence.getCurrentLineEndExclusiveWithLineBreaks(offset: Int): Int {
    var current = offset
    while (current < length) {
        if (this[current] == '\n') {
            return current + 1
        }
        current++
    }
    return length
}

/**
 * Finds the end index of the current line in the CharSequence, excluding line break characters.
 *
 * This function starts at the given offset and iterates through the characters in the CharSequence
 * until a line break character is encountered. It returns the index of the first line break character
 * or the length of the CharSequence if no line break is found.
 *
 * @param offset The starting position within the CharSequence to begin the search.
 * @return The index of the end of the current line, excluding the line break characters.
 */
internal fun CharSequence.getCurrentLineEndExclusiveWithoutLineBreaks(offset: Int): Int {
    var current = offset
    while (current < length) {
        if (this[current].isLineBreak()) {
            return current
        }
        current++
    }
    return length
}

/**
 * Checks if the text represented by this `TextRange` is not blank.
 *
 * A `TextRange` is considered not blank if it is not empty and the corresponding
 * substring of the given text contains at least one non-whitespace character.
 *
 * @param text The source string to extract and evaluate the substring from.
 * @return `true` if the `TextRange` is non-empty and the extracted substring is not blank,
 *         `false` otherwise.
 */
internal fun TextRange.isNotBlank(text: String): Boolean {
    return isNotEmpty() && substring(text).isNotBlank()
}

/**
 * Retrieves the text content of a document identified by the given path as a character sequence.
 *
 * This function uses the specified path to locate a document and, if found, retrieves its full
 * text content. If no document exists for the given path, `null` is returned.
 *
 * @param path The path identifying the document to retrieve.
 * @return A `CharSequence` containing the text content of the document, or `null` if no document is found.
 */
internal suspend fun <Path, Document> DocumentProvider<Path, Document>.charsByPath(path: Path): CharSequence? {
    return document(path)?.let { text(it) }
}

/**
 * Retrieves the text content of a document located at the specified path.
 *
 * This function uses the `charsByPath` method to fetch the character sequence
 * associated with the document at the given path and converts it to a string.
 * If no document is found or the content is unavailable, the function returns `null`.
 *
 * @param path The path or identifier used to locate the document.
 * @return The text content of the document as a string, or `null` if no document
 *         exists at the specified path or the content could not be retrieved.
 */
internal suspend fun <Path, Document> DocumentProvider<Path, Document>.textByPath(path: Path): String? {
    return charsByPath(path)?.toString()
}

/**
 * Expands a DocumentRange by a number of full lines before and after.
 * Returns a new range clamped to the content bounds.
 */
public fun extendRangeByLines(
    content: CharSequence,
    range: DocumentProvider.DocumentRange,
    linesBefore: Int = 2,
    linesAfter: Int = 2,
): DocumentProvider.DocumentRange {
    val all = content.lineRanges(includeLineSeparators = false).toList()
    if (all.isEmpty()) return range
    val startLine = maxOf(range.start.line - linesBefore, 0)
    val endLine = minOf(range.end.line + linesAfter, all.lastIndex)
    // Compute end column as the line length (without line separators)
    val endRange = all[endLine]
    val endCol = (endRange.length).coerceAtLeast(0)
    return DocumentProvider.DocumentRange(
        Position(startLine, 0),
        Position(endLine, endCol)
    )
}
