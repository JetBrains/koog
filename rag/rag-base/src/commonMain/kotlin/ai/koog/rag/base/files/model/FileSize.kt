package ai.koog.rag.base.files.model

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Provides human-readable file size information.
 *
 * Represents sizes as [Bytes] (in bytes, kibibytes, or mebibyte) or as [Lines] (a count of text
 * lines).
 */
@Serializable
public sealed interface FileSize {
    /** Returns a formatted string representation of the file size */
    public fun display(): String

    /** Provides utility methods and constants for file size handling */
    public companion object {
        /** Defines 1 kibibyte as 1024 bytes */
        public const val KIB: Long = 1024L

        /** Defines 1 mebibyte as 1024 × 1024 bytes */
        public const val MIB: Long = KIB * 1024L

        private fun Double.formatDecimals(decimals: Int): String {
            val roundedToDecimals =
                kotlin.math.round(this * 10.0.pow(decimals)) / 10.0.pow(decimals)
            if (roundedToDecimals == roundedToDecimals.toInt().toDouble()) {
                return "${roundedToDecimals.toInt()}.${"0".repeat(decimals)}"
            }
            return "$roundedToDecimals"
        }
    }

    /**
     * Represents a file's size in bytes with automatic unit selection for display.
     *
     * @property bytes the exact size in bytes (non-negative)
     */
    @Serializable
    public data class Bytes(val bytes: Long) : FileSize {
        /**
         * Formats the byte count as a human-readable string.
         *
         * Rules:
         * - `0` → `"0 bytes"`
         * - ≥ 1 MiB → value in MiB with one decimal place
         * - < 0.1 KiB → `"<0.1 KiB"`
         * - Otherwise → value in KiB with one decimal place
         */
        override fun display(): String {
            return when {
                bytes == 0L -> "0 bytes"
                bytes >= MIB -> "${(bytes.toDouble() / MIB).formatDecimals(1)} MiB"
                bytes.toDouble() / KIB < 0.1 -> "<0.1 KiB"
                else -> "${(bytes.toDouble() / KIB).formatDecimals(1)} KiB"
            }
        }
    }

    /**
     * Represents a file's size as line count.
     *
     * @property lines the number of lines (non-negative)
     */
    @Serializable
    public data class Lines(val lines: Int) : FileSize {
        /** Returns the line count as "1 line" when the value is one or "N lines" otherwise */
        override fun display(): String = if (lines == 1) "1 line" else "$lines lines"
    }
}
