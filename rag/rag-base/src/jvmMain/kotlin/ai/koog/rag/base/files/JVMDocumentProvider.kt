package ai.koog.rag.base.files

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText


/**
 * An implementation of the `DocumentProvider` interface for handling documents represented as `Path` objects.
 * This object provides functionality to read and write text from files, allowing for document editing and
 * text transformation while normalizing line separators.
 */
public object JVMDocumentProvider : DocumentProvider<Path, Path> {
    override suspend fun document(path: Path): Path = path
    override suspend fun text(document: Path): CharSequence = convertLineSeparators(document.readText())

    /**
     * An implementation of the `DocumentProvider.Edit` interface for editing document contents.
     *
     * This object provides functionality to modify the text content of a specified document,
     * either entirely or partially based on a given range. It handles reading and writing
     * text to the document with a focus on ensuring that the range and content modifications
     * are valid.
     */
    public object Edit : DocumentProvider.Edit<Path, Path> {
        override suspend fun setText(document: Path, text: String, range: DocumentProvider.DocumentRange?) {
            if (range != null) {
                val fileLines = document.readLines()
                document.writeLines(modifyLines(fileLines, text, range))
            } else {
                document.writeText(text)
            }
        }

        /**
         * Modifies the given list of lines by replacing the content within the specified range with new text.
         *
         * @param lines The list of strings representing lines of text to be modified.
         * @param newText The new text that will replace the content within the specified range.
         * @param range The range within which the content is to be replaced, defined by start and end positions
         *              in the document.
         * @return A new list of strings representing the modified lines.
         */
        private fun modifyLines(
            lines: List<String>,
            newText: String,
            range: DocumentProvider.DocumentRange
        ): List<String> {
            require(range.start <= range.end) { "Start position is greater than end position" }
            require(range.start.line in lines.indices) { "Start line ${range.start.line} is out of file lines range" }
            require(range.start.column <= lines[range.start.line].length) { "Start column ${range.start.column} is not in line ${range.start.line} range" }
            require(range.end.column <= lines[range.end.line].length) { "End column ${range.end.column} is not in line ${range.end.line} range" }
            require(range.end.line in lines.indices) { "End line ${range.end.line} is out of file lines range" }

            val startLine = lines[range.start.line].substring(0, range.start.column)
            val endLine = lines[range.end.line].substring(range.end.column)

            val newLines = mutableListOf<String>()

            val oldLinesIterator = lines.listIterator()
            for (i in 0 until range.start.line) {
                newLines.add(oldLinesIterator.next())
            }

            // Replace lines between start and end
            newLines.addAll((startLine + newText + endLine).lineSequence())

            for (i in range.start.line..range.end.line) {
                oldLinesIterator.next()
            }

            newLines.addAll(oldLinesIterator.asSequence())

            return newLines
        }
    }

    private fun convertLineSeparators(text: String): String {
        var buffer: StringBuilder? = null
        var intactLength = 0
        val newSeparatorIsSlashN = "\n" == "\n"
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                if (!newSeparatorIsSlashN) {
                    if (buffer == null) {
                        buffer = StringBuilder(text.length)
                        buffer.append(text, 0, intactLength)
                    }
                    buffer.append("\n")
                    shiftOffsets(null, buffer.length, 1, "\n".length)
                } else if (buffer == null) {
                    intactLength++
                } else {
                    buffer.append('\n')
                }
            } else if (c == '\r') {
                val followedByLineFeed = i < text.length - 1 && text[i + 1] == '\n'
                if (!followedByLineFeed && false) {
                    if (buffer == null) {
                        intactLength++
                    } else {
                        buffer.append('\r')
                    }
                    i++
                    continue
                }
                if (buffer == null) {
                    buffer = StringBuilder(text.length)
                    buffer.append(text, 0, intactLength)
                }
                buffer.append("\n")
                if (followedByLineFeed) {
                    i++
                    shiftOffsets(null, buffer.length, 2, "\n".length)
                } else {
                    shiftOffsets(null, buffer.length, 1, "\n".length)
                }
            } else if (buffer == null) {
                intactLength++
            } else {
                buffer.append(c)
            }
            i++
        }
        return (buffer ?: text).toString()
    }

    private fun shiftOffsets(offsets: IntArray?, changeOffset: Int, oldLength: Int, newLength: Int) {
        if (offsets == null) return
        val shift = newLength - oldLength
        if (shift == 0) return
        for (i in offsets.indices) {
            val offset = offsets[i]
            if (offset >= changeOffset + oldLength) {
                offsets[i] += shift
            }
        }
    }
}