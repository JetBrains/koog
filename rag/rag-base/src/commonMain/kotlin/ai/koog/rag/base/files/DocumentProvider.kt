package ai.koog.rag.base.files

import kotlinx.serialization.Serializable

/**
 * Interface representing a provider for managing documents and their contents.
 * It allows retrieval of documents, manipulation of text, and extraction of content fragments.
 *
 * @param Path The type representing the path or identifier for locating documents.
 * @param Document The type representing the document being operated on.
 */
public interface DocumentProvider<Path, Document> {
    /**
     * Retrieves a document corresponding to the specified path.
     *
     * The document represents the content or metadata associated with the given path.
     * If no document can be retrieved for the provided path, `null` is returned.
     *
     * @param path The path for which the document is to be retrieved.
     * @return A document corresponding to the path, or `null` if no document is available.
     */
    public suspend fun document(path: Path): Document?

    /**
     * Retrieves the full text content of the given document as a character sequence.
     *
     * @param document The document whose text content is to be retrieved.
     * @return A CharSequence representing the text content of the document.
     */
    public suspend fun text(document: Document): CharSequence

    /**
     * Extracts a specified fragment of text from a given document based on a defined range.
     *
     * @param document The document from which the text fragment will be extracted.
     * @param range The range in the document that defines the start and end positions of the text fragment.
     * @return A string containing the text fragment within the specified range of the document.
     */
    public suspend fun textFragment(document: Document, range: DocumentRange): String {
        val text = text(document)
        return text.substring(range.start.toOffset(text), range.end.toOffset(text))
    }

    /**
     * Represents an interface for performing edit operations on a document.
     *
     * @param Path The type representing the document's path or identifier.
     * @param Document The type representing the document to perform edits on.
     */
    public interface Edit<Path, Document> {
        /**
         * Sets the text content of the specified document, optionally within the provided range.
         *
         * @param document The document where the text will be set.
         * @param text The new text to replace within the document.
         * @param range An optional range specifying the portion of the document to be replaced.
         *              If null, the entire document will be replaced.
         */
        public suspend fun setText(document: Document, text: String, range: DocumentRange? = null)
    }

    /**
     * Represents a range within a document, defined by a start and end position.
     *
     * A `DocumentRange` encapsulates two `Position` objects, representing the
     * beginning and end of the range, respectively. These positions are zero-based
     * and valid for navigating and selecting content within a textual document.
     *
     * This class provides utilities for working with document ranges, including
     * calculating the number of lines covered by the range, determining if the range
     * spans multiple lines, and extracting a substring from a given text based on the range.
     *
     * @property start The starting position of the range (inclusive).
     * @property end The ending position of the range (exclusive).
     */
    @Serializable
    public data class DocumentRange(val start: Position, val end: Position) {
        /**
         * Extracts a substring from the given text based on the start and end positions of the `DocumentRange`.
         *
         * @param text The source text from which the substring will be extracted.
         *             This is a sequence of characters to operate on.
         * @return A substring of the text starting from the resolved offset of the start position
         *         and ending at the resolved offset of the end position.
         */
        public fun substring(text: CharSequence): String = text.substring(start.toOffset(text), end.toOffset(text))

        /**
         * The number of lines covered by the range defined by the `start` and `end` positions.
         * This computation includes both the start and end lines in the count.
         */
        public val lines: Int = end.line - start.line + 1

        /**
         * Indicates whether the range spans across multiple lines.
         *
         * This property is `true` if the number of lines in the range is greater than one.
         * It provides a convenient way to determine if the range is multiline or confined
         * to a single line.
         */
        public val isMultiline: Boolean = lines > 1
    }

    /**
     * Represents a position in a text document identified by a specific line and column.
     * Used for precise referencing of specific locations within multi-line text content.
     *
     * @property line The line number, starting from 0.
     * @property column The column number, starting from 0.
     */
    @Serializable
    public data class Position(val line: Int, val column: Int) : Comparable<Position> {
        /**
         * Compares this `Position` instance to another `Position` based on their `line` and `column` values.
         *
         * @param other The `Position` instance to compare to this instance.
         * @return A negative integer, zero, or a positive integer if this `Position` is less than,
         *         equal to, or greater than the specified `Position`, respectively.
         */
        override fun compareTo(other: Position): Int = compareValuesBy(this, other, { it.line }, { it.column })
    }
}
