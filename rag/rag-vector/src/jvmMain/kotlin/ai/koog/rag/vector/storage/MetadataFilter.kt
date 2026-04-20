package ai.koog.rag.vector.storage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Safe, SQL-injection-free metadata filter parser and translator used by [PGVectorStorage].
 *
 * Grammar (informal):
 * - an expression is a sequence of `key = value` terms joined by `AND`
 * - keys are `[A-Za-z_][A-Za-z0-9_.]*`, with `.` denoting nested JSON paths
 * - values are double-quoted strings, integers, doubles, or boolean literals
 *
 * All parsed terms are combined into a single JSONB containment match (metadata contains value),
 * which is fully parameterized — no user input is ever interpolated into SQL. Nested keys using
 * dotted paths (e.g., `owner.team = "core"`) are expanded into nested JSON objects.
 *
 * `OR` and comparison operators other than `=` are intentionally unsupported for the first cut
 * to keep the API safe and the `GIN (metadata jsonb_path_ops)` index efficient. They can be
 * added later without changing existing semantics.
 *
 * This parser is strict: unknown tokens, unterminated strings, and unsupported operators produce
 * [IllegalArgumentException] so callers can route malformed filters to a 4xx response.
 */
internal object MetadataFilter {

    /**
     * A compiled filter ready to be bound to a prepared statement.
     *
     * @property jsonContainment JSONB document used on the right-hand side of `metadata @>`.
     *   `null` means "no filter" (either the input was blank or only trivial).
     */
    internal data class Compiled(val jsonContainment: JsonObject?)

    private val EMPTY: Compiled = Compiled(null)

    internal fun compile(expression: String?): Compiled {
        if (expression.isNullOrBlank()) return EMPTY
        val terms = Parser(expression).parse()
        if (terms.isEmpty()) return EMPTY
        // Merge terms into a single JSON object. Duplicate keys with different values are a
        // contradiction and would never match; reject them at compile time. A LinkedHashMap is
        // used throughout so the JSONB representation is deterministic in input order, which
        // matters for testability and for downstream cache keys.
        val merged = LinkedHashMap<String, JsonPrimitive>()
        for ((key, value) in terms) {
            val existing = merged[key]
            if (existing != null && existing != value) {
                throw IllegalArgumentException(
                    "Contradictory filter terms for key '$key': '$existing' AND '$value'"
                )
            }
            merged[key] = value
        }
        // Expand dotted keys into nested JSON objects so `a.b = x` → {"a":{"b":"x"}}.
        val jsonRoot = buildJsonObject {
            val tree = LinkedHashMap<String, Any>()
            for ((k, v) in merged) insertPath(tree, k.split('.'), v)
            emit(tree, this)
        }
        return Compiled(jsonRoot)
    }

    @Suppress("UNCHECKED_CAST")
    private fun insertPath(node: LinkedHashMap<String, Any>, path: List<String>, leaf: JsonPrimitive) {
        var current: LinkedHashMap<String, Any> = node
        for (i in 0 until path.size - 1) {
            val segment = path[i]
            val child = current[segment]
            current = when (child) {
                null -> LinkedHashMap<String, Any>().also { current[segment] = it }
                is LinkedHashMap<*, *> -> child as LinkedHashMap<String, Any>
                else -> throw IllegalArgumentException(
                    "Filter key path '${path.joinToString(".")}' conflicts with a scalar leaf at '$segment'"
                )
            }
        }
        current[path.last()] = leaf
    }

    private fun emit(tree: Map<String, Any>, builder: kotlinx.serialization.json.JsonObjectBuilder) {
        for ((k, v) in tree) {
            when (v) {
                is JsonPrimitive -> builder.put(k, v)
                is Map<*, *> -> builder.put(k,
                    buildJsonObject {
                    @Suppress("UNCHECKED_CAST")
                    emit(v as Map<String, Any>, this)
                    }
                )

                else -> error("Unexpected tree node: $v")
            }
        }
    }

    private class Parser(private val source: String) {
        private var pos: Int = 0

        fun parse(): List<Pair<String, JsonPrimitive>> {
            val terms = mutableListOf<Pair<String, JsonPrimitive>>()
            skipWs()
            if (eof()) return terms
            terms.add(parseTerm())
            while (true) {
                skipWs()
                if (eof()) break
                expectKeyword("AND")
                skipWs()
                terms.add(parseTerm())
            }
            return terms
        }

        private fun parseTerm(): Pair<String, JsonPrimitive> {
            val key = parseIdentifier()
            skipWs()
            expectChar('=')
            skipWs()
            val value = parseValue()
            return key to value
        }

        private fun parseIdentifier(): String {
            val start = pos
            if (eof() || !(peek().isLetter() || peek() == '_')) {
                fail("Expected identifier")
            }
            while (!eof()) {
                val c = peek()
                if (c.isLetterOrDigit() || c == '_' || c == '.') pos++ else break
            }
            return source.substring(start, pos)
        }

        private fun parseValue(): JsonPrimitive {
            if (eof()) fail("Expected value")
            val c = peek()
            return when {
                c == '"' -> JsonPrimitive(parseStringLiteral())
                c == '-' || c.isDigit() -> parseNumberLiteral()
                c.isLetter() -> parseBooleanLiteral()
                else -> fail("Unexpected character '$c' while reading value")
            }
        }

        private fun parseStringLiteral(): String {
            expectChar('"')
            val sb = StringBuilder()
            while (!eof()) {
                val c = source[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (eof()) fail("Unterminated escape in string literal")
                        val esc = source[pos++]
                        when (esc) {
                            '"', '\\' -> sb.append(esc)
                            else -> fail("Unsupported escape '\\$esc'; only \\\" and \\\\ are allowed")
                        }
                    }

                    else -> sb.append(c)
                }
            }
            fail("Unterminated string literal")
        }

        private fun parseNumberLiteral(): JsonPrimitive {
            val start = pos
            if (peek() == '-') pos++
            var sawDigit = false
            while (!eof() && peek().isDigit()) {
                pos++; sawDigit = true
            }
            var isFloat = false
            if (!eof() && peek() == '.') {
                isFloat = true; pos++
                while (!eof() && peek().isDigit()) {
                    pos++; sawDigit = true
                }
            }
            if (!sawDigit) fail("Invalid number literal")
            val raw = source.substring(start, pos)
            return if (isFloat) JsonPrimitive(raw.toDouble()) else JsonPrimitive(raw.toLong())
        }

        private fun parseBooleanLiteral(): JsonPrimitive {
            val start = pos
            while (!eof() && (peek().isLetter())) pos++
            return when (val w = source.substring(start, pos)) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> fail("Expected boolean 'true'/'false', got '$w'")
            }
        }

        private fun expectKeyword(word: String) {
            val start = pos
            for (ch in word) {
                if (eof() || !source[pos].equals(ch, ignoreCase = true)) {
                    pos = start
                    fail("Expected keyword '$word'")
                }
                pos++
            }
            // Require word boundary.
            if (!eof() && (peek().isLetterOrDigit() || peek() == '_')) {
                pos = start
                fail("Expected keyword '$word'")
            }
        }

        private fun expectChar(c: Char) {
            if (eof() || source[pos] != c) fail("Expected '$c'")
            pos++
        }

        private fun skipWs() {
            while (!eof() && peek().isWhitespace()) pos++
        }

        private fun eof(): Boolean = pos >= source.length
        private fun peek(): Char = source[pos]

        private fun fail(message: String): Nothing {
            throw IllegalArgumentException(
                "Invalid metadata filter expression at position $pos: $message. " +
                    "Expression: '$source'"
            )
        }
    }
}
