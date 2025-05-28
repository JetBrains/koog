package ai.koog.prompt.tokenizer.tiktoken

/**
 * Represents a key based on a byte array. This class is primarily designed to be used as a key in
 * collections such as Maps due to the proper implementation of `equals` and `hashCode` methods based
 * on the byte array's content.
 *
 * @constructor Creates a `ByteArrayKey` instance from the given byte array.
 * @param bytes The byte array that forms the basis of the key.
 */
public class ByteArrayKey(private val bytes: ByteArray) {
    /**
     * Represents the size of the byte array encapsulated by the `ByteArrayKey` instance.
     *
     * This property provides the number of bytes contained in the underlying byte array.
     *
     * It is calculated by retrieving the `size` of the `ByteArray` instance.
     */
    public val size: Int
        get() = bytes.size

    /**
     * Creates a new `ByteArrayKey` object containing a subrange of bytes from the current `ByteArrayKey`.
     *
     * @param start The starting index (inclusive) of the range to extract.
     * @param end The ending index (exclusive) of the range to extract.
     * @return A new `ByteArrayKey` object with the specified range of bytes.
     */
    public fun range(start: Int, end: Int): ByteArrayKey = ByteArrayKey(
        bytes.copyOfRange(start, end)
    )

    /**
     * Determines whether this object is equal to the specified object.
     *
     * @param other The object to compare with this object for equality.
     * @return true if the specified object is equal to this object, false otherwise.
     */
    override fun equals(other: Any?): Boolean =
        this === other || other is ByteArrayKey && this.bytes contentEquals other.bytes

    /**
     * Computes the hash code for the `ByteArrayKey` instance using the content-based hash code
     * of the underlying byte array.
     *
     * @return An integer hash code representing the contents of the byte array.
     */
    override fun hashCode(): Int = bytes.contentHashCode()
    /**
     * Returns a string representation of the byte array encapsulated in the `ByteArrayKey` object.
     *
     * @return A string representation of the contents of this byte array.
     */
    override fun toString(): String = bytes.contentToString()
}

internal data class MutablePair<First, Second>(var first: First, var second: Second)

internal infix fun <First, Second> First.toM(second: Second) = MutablePair(this, second)

/**
 * Converts the current string into a `ByteArrayKey` by encoding the string into a UTF-8 byte array.
 *
 * @return A `ByteArrayKey` containing the UTF-8 bytes of the string.
 */
public fun String.toByteArrayKey(): ByteArrayKey = ByteArrayKey(this.encodeToByteArray())
