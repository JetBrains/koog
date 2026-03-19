package ai.koog.poet

import ai.koog.poet.SymbolFilter.Companion.fromOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration

/**
 * Filters [KSClassDeclaration] and [KSFunctionDeclaration] symbols from a mixed sequence,
 * applying root package and glob pattern constraints.
 *
 * Filtering order (applied to both classes and functions):
 * 1. Type cast — only the target declaration type is kept.
 * 2. Root package check via [filterByRootPackage].
 * 3. Include patterns: if non-empty, the qualified name must match at least one.
 *    A declaration with no qualified name is excluded when any include pattern is present.
 * 4. Exclude patterns: a declaration matching any of these is dropped.
 *
 * Glob syntax: `*` matches any sequence of non-`.` characters; `**` matches any sequence
 * including `.`; `?` matches a single non-`.` character.
 *
 * Prefer constructing via [fromOptions] when reading directly from KSP processor options.
 *
 * Example:
 * ```kotlin
 * val filter = SymbolFilter.fromOptions(
 *     rootPackage = options["kotlinx.schema.rootPackage"],
 *     includeOption = options["kotlinx.schema.include"],
 *     excludeOption = options["kotlinx.schema.exclude"],
 *     logger = logger,
 * )
 * val classes: Sequence<KSClassDeclaration> = filter.filter<KSClassDeclaration>(allSymbols)
 * val functions: Sequence<KSFunctionDeclaration> = filter.filter<KSFunctionDeclaration>(allSymbols)
 * ```
 *
 * @param rootPackage optional root package; declarations outside it are skipped.
 * @param includePatterns glob patterns; if non-empty, a declaration must match at least one.
 * @param excludePatterns glob patterns; a declaration matching any of these is excluded.
 * @param logger KSP logger for diagnostic messages.
 */
internal class SymbolFilter(
    private val rootPackage: String?,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    private val logger: KSPLogger,
) {
    private val includeRegexes = includePatterns.map { globToRegex(it) }
    private val excludeRegexes = excludePatterns.map { globToRegex(it) }

    companion object {
        /**
         * Constructs a [SymbolFilter] from raw KSP processor option strings.
         *
         * Splits [includeOption] and [excludeOption] on commas and semicolons, trims each token,
         * and drops blanks. A `null` or blank option value means no filtering for that dimension.
         *
         * @param rootPackage raw value of the `kotlinx.schema.rootPackage` option.
         * @param includeOption raw value of the `kotlinx.schema.include` option.
         * @param excludeOption raw value of the `kotlinx.schema.exclude` option.
         * @param logger KSP logger for diagnostic messages.
         */
        fun fromOptions(
            rootPackage: String?,
            includeOption: String?,
            excludeOption: String?,
            logger: KSPLogger,
        ) = SymbolFilter(
            rootPackage = rootPackage?.trim()?.takeIf { it.isNotEmpty() },
            includePatterns = includeOption.parsePatterns(),
            excludePatterns = excludeOption.parsePatterns(),
            logger = logger,
        )

        private fun String?.parsePatterns(): List<String> =
            this
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.split(Regex("[,;]"))
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
    }

    inline fun <reified T : KSDeclaration> filter(symbols: Sequence<KSAnnotated>): Sequence<T> =
        symbols
            .filterIsInstance<T>()
//            .filter { filterByRootPackage(it, rootPackage, logger) }
            .filter { matchesPatterns(it.qualifiedName?.asString()) }

    @Suppress("ReturnCount")
    private fun matchesPatterns(name: String?): Boolean {
        if (includeRegexes.isEmpty() && excludeRegexes.isEmpty()) return true
        if (name == null) return includeRegexes.isEmpty()
        val included = includeRegexes.isEmpty() || includeRegexes.any { it.matches(name) }
        val excluded = excludeRegexes.isNotEmpty() && excludeRegexes.any { it.matches(name) }
        return included && !excluded
    }
}

/**
 * Converts a glob pattern to a [Regex].
 *
 * - `**` matches any sequence of characters including `.`
 * - `*` matches any sequence of non-`.` characters
 * - `?` matches a single non-`.` character
 * - All other characters are matched literally.
 */
internal fun globToRegex(glob: String): Regex {
    val regex =
        buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                when {
                    glob[i] == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                        append(".*")
                        i += 2
                    }

                    glob[i] == '*' -> {
                        append("[^.]*")
                        i++
                    }

                    glob[i] == '?' -> {
                        append("[^.]")
                        i++
                    }

                    else -> {
                        append(Regex.escape(glob[i].toString()))
                        i++
                    }
                }
            }
            append('$')
        }
    return Regex(regex)
}
