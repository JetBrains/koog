package ai.koog.utils.lang

/**
 * Returns string with masked symbols using the strictest security level.
 *
 * This method is designed for masking sensitive secrets and provides maximum security
 * by returning a consistent pattern that reveals no information about the original content.
 *
 * Examples:
 *  - `null` -> `null`
 *  - `""` -> `null`
 *  - `"   "` -> `null`
 *  - `"I"` -> `"***HIDDEN***"`
 *  - `"Hi"` -> `"***HIDDEN***"`
 *  - `"Hello"` -> `"***HIDDEN***"`
 *  - `"VeryLongSecretKey123"` -> `"***HIDDEN***"`
 */
public fun String?.masked(
    maskChar: Char = '*',
): String? {
    if (this.isNullOrBlank()) return null

    // Strictest security level: consistent pattern regardless of input
    return "${maskChar}${maskChar}${maskChar}HIDDEN${maskChar}${maskChar}$maskChar"
}
