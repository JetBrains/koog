package ai.koog.rag.base.files.filter

/**
 * A pattern for matching file paths using glob syntax.
 *
 * Supports standard glob pattern syntax:
 * - * matches any sequence of characters within a path segment
 * - ** matches any sequence of characters across path segments
 * - ? matches any single character
 * - \[abc\] matches any character in the set
 * - [!abc] matches any character not in the set
 * - {a,b,c} matches any of the alternatives a, b, or c
 *
 * @param pattern the glob pattern string
 * @param caseSensitive whether the pattern matching should be case-sensitive (default: `true`)
 * @property regex the compiled regular expression used for matching
 */
public class GlobPattern(pattern: String, caseSensitive: Boolean = true) {
    private val regex: Regex = convertGlobToRegex(pattern, caseSensitive)

    /**
     * Tests if the given path matches this glob pattern.
     *
     * @param path the file system path to test, using `/` as separator
     * @return `true` if the path matches the pattern, `false` otherwise
     */
    public fun matches(path: String): Boolean = regex.matches(path)

    public companion object {
        /**
         * A pattern that matches any path.
         */
        public val ANY: GlobPattern = GlobPattern("**", caseSensitive = false)

        private fun convertGlobToRegex(glob: String, caseSensitive: Boolean): Regex {
            val escaped = glob.escape()
            // First, preserve alternatives by replacing them with a placeholder
            val alternatives = mutableListOf<String>()
            // Modified regex pattern with double escaping for curly braces
            val withPlaceholders = escaped.replace(Regex("\\{([^\\}]+)\\}")) { matchResult ->
                val altGroup = matchResult.groupValues[1]
                alternatives.add(altGroup)
                "\$ALT${alternatives.size - 1}$"
            }

            // Then convert glob to regex
            val regexPattern = withPlaceholders
                .replace("**", "\$DOUBLE_STAR$")
                .replace("*", "[^/]*")
                .replace("\$DOUBLE_STAR$", ".*")
                .replace("?", ".")

            // Handle leading **/ pattern
            val finalRegexPattern = if (glob.startsWith("**/")) {
                // Make the leading part optional by using /?(.*/)?
                regexPattern.replaceFirst(".*/", "/?(.*/)?")
            } else {
                regexPattern
            }

            // Finally, restore alternatives
            val finalPattern = alternatives.foldIndexed(finalRegexPattern) { index, pattern, alts ->
                pattern.replace(
                    "\$ALT$index$",
                    "(" + alts.split(",").joinToString("|") { Regex.escape(it) } + ")"
                )
            }

            return ("^$finalPattern$")
                .toRegex(if (!caseSensitive) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }

        private fun String.escape() = replace("\\", "\\\\") // Escape backslashes first
            .replace(".", "\\.")
            .replace("+", "\\+")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("|", "\\|")
    }
}
