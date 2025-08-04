package ai.koog.rag.base.files

import kotlinx.serialization.Serializable
import kotlin.js.JsName
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a range of text, defined by a start position (inclusive)
 * and an end position (exclusive).
 *
 * @property start The inclusive start index of the range.
 * @property endExclusive The exclusive end index of the range.
 */
@Serializable
internal data class TextRange(val start: Int, val endExclusive: Int) {
    /**
     * The inclusive end boundary of the range.
     *
     * This property represents the last valid value within the range, which is
     * calculated as `endExclusive - 1`.
     *
     * It is used to determine the extent of the range, inclusively.
     */
    internal val endInclusive: Int
        get() = endExclusive - 1

    /**
     * Represents the length of the text range.
     *
     * The length is calculated as the difference between `endExclusive` and `start`.
     * It defines the number of elements or characters within the range.
     */
    internal val length: Int
        get() = endExclusive - start

    /**
     * Checks if the text range is empty.
     *
     * A text range is considered empty when the starting position (start) is
     * greater than or equal to the exclusive ending position (endExclusive).
     *
     * @return `true` if the range is empty, otherwise `false`.
     */
    internal fun isEmpty(): Boolean = start >= endExclusive

    /**
     * Checks whether the text range is not empty.
     *
     * A text range is considered not empty if its start position is less than its exclusive end position.
     * This method returns the logical negation of the `isEmpty` method.
     *
     * @return `true` if the text range is not empty, `false` otherwise.
     */
    internal fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Shifts the current range by a specified value.
     *
     * The method creates a new `TextRange` instance by shifting the `start` and `endExclusive` properties of the current range
     * by the given `value`. It does not mutate the original range but instead returns a new instance with the updated bounds.
     *
     * @param value The amount to shift the range. This value is added to both the `start` and `endExclusive` positions.
     *              Positive values move the range to the right, and negative values move the range to the left.
     * @return A new `TextRange` instance representing the shifted range.
     */
    internal fun shift(value: Int): TextRange = TextRange(start + value, endExclusive + value)

    /**
     * Extracts a substring from the given text using this range.
     * The range is represented by the `start` and `endExclusive` properties of the containing class.
     * The substring starts at the `start` index and ends at the minimum of `endExclusive` or the length of the string.
     *
     * @param text The string from which to extract the substring.
     * @return A substring defined by the range within the provided string.
     */
    internal fun substring(text: String): String = text.substring(start, min(endExclusive, text.length))

    /**
     * Checks if the specified position is within the range defined by this `TextRange`.
     *
     * @param position The position to check.
     * @return `true` if the given position is within the range; `false` otherwise.
     */
    @JsName("containsPosition")
    internal operator fun contains(position: Int): Boolean {
        return position in start until endExclusive
    }

    /**
     * Checks if the specified range is fully contained within this range.
     *
     * @param range The `TextRange` to check for containment.
     * @return `true` if the specified range is fully within this range, otherwise `false`.
     */
    @JsName("containsRange")
    internal operator fun contains(range: TextRange): Boolean {
        return range.start >= this.start && range.endExclusive <= this.endExclusive
    }

    /**
     * Determines if this TextRange intersects with another specified TextRange.
     *
     * Two TextRanges intersect if they share any common segment within their respective ranges.
     *
     * @param other The TextRange to check for intersection with this range.
     * @return True if the two TextRanges intersect, false otherwise.
     */
    internal fun intersects(other: TextRange): Boolean {
        return this.endExclusive > other.start && other.endExclusive > start
    }

    /**
     * Computes the intersection of the current text range with another text range.
     *
     * The intersection is a new `TextRange` that represents the overlapping region between
     * the two text ranges. If there is no overlapping region, the resulting `TextRange` will
     * have equal start and end values, representing an empty range.
     *
     * @param other The other `TextRange` to intersect with.
     * @return A new `TextRange` representing the intersection of the two ranges.
     */
    internal fun intersect(other: TextRange): TextRange {
        return TextRange(max(this.start, other.start), min(this.endExclusive, other.endExclusive))
    }

    /**
     * Determines whether the current text range fully covers another text range.
     *
     * A text range is considered to "cover" another text range if:
     * - The current range starts at or before the start of the other range.
     * - The current range ends at or after the end of the other range.
     *
     * @param other The text range to check against this range.
     * @return `true` if the current range covers the other range, otherwise `false`.
     */
    internal fun covers(other: TextRange): Boolean {
        return this.endExclusive >= other.endExclusive && this.start <= other.start
    }

    /**
     * Subtracts the given `TextRange` from the current range and returns an array of resulting ranges.
     *
     * If the two ranges do not intersect, the original range is returned as a single-element array.
     * If the given range fully contains the current range, an empty array is returned.
     * Otherwise, appropriate segments of the current range are returned after subtraction.
     *
     * @param other The `TextRange` to subtract from the current range.
     * @return An array of `TextRange` objects representing the result of the subtraction.
     */
    internal fun subtract(other: TextRange): Array<TextRange> {
        if (!this.intersects(other)) return arrayOf(this)

        if (other.isEmpty()) return arrayOf(this)
        if (this.isEmpty()) return arrayOf(this)

        if (other.contains(this)) return emptyArray()
        if (this.contains(other)) {
            return arrayOf(
                TextRange(this.start, other.start),
                TextRange(other.endExclusive, this.endExclusive)
            ).filter { it.isNotEmpty() }.toTypedArray()
        }

        val intersection = this.intersect(other)
        if (intersection.isEmpty()) return emptyArray()

        val result = ArrayList<TextRange>()
        for (value in this.subtract(intersection)) {
            val previous = result.lastOrNull()
            if (previous == null || previous.endExclusive != value.start) {
                result.add(value)
            } else {
                result.removeLast()
                result.add(TextRange(previous.start, value.endExclusive))
            }
        }
        return result.toTypedArray()
    }

    /**
     * Companion object for the TextRange class, providing utility functions
     * for creating and manipulating TextRange objects.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    internal companion object {
        /**
         * Creates a `TextRange` from the provided integer range.
         *
         * @param range The range of integers to be converted into a `TextRange`.
         *              The `start` of the `TextRange` corresponds to the `start` of the input range,
         *              and the `endExclusive` of the `TextRange` is determined by the `endInclusive` of the input range plus one.
         * @return A new `TextRange` instance representing the provided range.
         */
        internal operator fun invoke(range: IntRange): TextRange = TextRange(range.start, range.endInclusive + 1)

        /**
         * Calculates the smallest `TextRange` that fully covers the provided array of ranges.
         *
         * @param ranges An array of `TextRange` objects to calculate the covering range for.
         *               If the array is empty, null is returned.
         * @return A `TextRange` that fully encompasses all provided ranges, or null if the input array is empty.
         */
        internal fun covering(ranges: Array<TextRange>): TextRange? {
            if (ranges.isEmpty()) return null
            return TextRange(ranges.minOf { it.start }, ranges.maxOf { it.endExclusive })
        }
    }
}
