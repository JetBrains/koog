package ai.koog.rag.vector.database

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline

/**
 * Kotlin DSL for building filter expressions for vector databases.
 *
 * Example usage:
 * ```kotlin
 * val filter = filterExpression {
 *     "category" eq "books"
 *     and {
 *         "price" lt 100
 *         "inStock" eq true
 *     }
 * }
 *
 * // Or using infix functions:
 * val filter2 = filterExpression {
 *     ("category" eq "books") and ("price" lt 100)
 * }
 *
 * // Complex expressions:
 * val filter3 = filterExpression {
 *     (("category" eq "books") or ("category" eq "electronics")) and ("price" lte 50)
 * }
 * ```
 */

// ==================== Core Types ====================

/**
 * Custom serializer for Any? types used in filter expressions.
 * Supports: String, Number (Int, Long, Double), Boolean, null, List, Key, Value, Expression
 */
public object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("AnySerializer only works with JSON format")
        val jsonElement = toJsonElement(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("AnySerializer only works with JSON format")
        val element = jsonDecoder.decodeJsonElement()
        return fromJsonElement(element)
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Key -> buildJsonObject {
            put("__type", "Key")
            put("name", value.name)
        }

        is Value -> buildJsonObject {
            put("__type", "Value")
            put("value", toJsonElement(value.value))
        }

        is Expression -> buildJsonObject {
            put("__type", "Expression")
            put("type", value.type.name)
            put("left", toJsonElement(value.left))
            put("right", toJsonElement(value.right))
        }

        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }

        is JsonArray -> element.map { fromJsonElement(it) }
        is JsonObject -> {
            when (element["__type"]?.jsonPrimitive?.content) {
                "Key" -> Key(element["name"]!!.jsonPrimitive.content)
                "Value" -> Value(fromJsonElement(element["value"]!!))
                "Expression" -> Expression(
                    type = ExpressionType.valueOf(element["type"]!!.jsonPrimitive.content),
                    left = fromJsonElement(element["left"]!!),
                    right = element["right"]?.let { fromJsonElement(it) }
                )

                else -> element.mapValues { fromJsonElement(it.value) }
            }
        }
    }
}

/**
 * Enumeration of supported expression types for filter operations.
 */
@Serializable
public enum class ExpressionType {
    EQ,      // Equal
    NE,      // Not equal
    GT,      // Greater than
    GTE,     // Greater than or equal
    LT,      // Less than
    LTE,     // Less than or equal
    IN,      // In collection
    NIN,     // Not in collection
    AND,     // Logical AND
    OR,      // Logical OR
    NOT,     // Logical NOT
    ISNULL,  // Is null check
    ISNOTNULL // Is not null check
}

/**
 * Represents a key (field name) in a filter expression.
 */
@Serializable
@JvmInline
public value class Key(public val name: String)

/**
 * Represents a value in a filter expression.
 */
@Serializable
@JvmInline
public value class Value(
    @Serializable(with = AnySerializer::class)
    public val value: Any?
)

/**
 * Base sealed interface for filter operands.
 */
@Serializable
public sealed interface Operand

/**
 * Represents a filter expression with a type, left operand, and optional right operand.
 */
@Serializable
public data class Expression(
    val type: ExpressionType,
    @Serializable(with = AnySerializer::class)
    val left: Any?,  // Can be Key, Expression, or null
    @Serializable(with = AnySerializer::class)
    val right: Any? = null  // Can be Value, Expression, or null
) : Operand

/**
 * Represents a grouped expression (parentheses).
 */
@Serializable
public data class Group(val content: Expression) : Operand

// ==================== FilterOp Wrapper ====================

/**
 * Wrapper class for filter operations.
 * Provides a fluent API for combining filter expressions.
 */
@JvmInline
public value class FilterOp(public val operand: Operand) {

    /**
     * Combines this filter with another using AND logic.
     */
    public infix fun and(other: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.AND, toExpression(), other.toExpression()))

    /**
     * Combines this filter with another using OR logic.
     */
    public infix fun or(other: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.OR, toExpression(), other.toExpression()))

    /**
     * Builds the final Expression from this operation.
     */
    public fun build(): Expression = toExpression()

    /**
     * Converts the operand to an Expression.
     */
    private fun toExpression(): Expression = when (val op = operand) {
        is Group -> op.content
        is Expression -> op
    }
}

// ==================== DSL Scope ====================

/**
 * DSL marker for filter expression building.
 */
@DslMarker
public annotation class FilterDsl

/**
 * Builder class for constructing filter expressions using Kotlin DSL.
 */
@FilterDsl
public class FilterExpressionScope {

    // ==================== Comparison Operations ====================

    /**
     * Creates an equality filter: key == value
     */
    public infix fun String.eq(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.EQ, Key(this), Value(value)))

    /**
     * Creates a not-equal filter: key != value
     */
    public infix fun String.ne(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.NE, Key(this), Value(value)))

    /**
     * Creates a greater-than filter: key > value
     */
    public infix fun String.gt(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.GT, Key(this), Value(value)))

    /**
     * Creates a greater-than-or-equal filter: key >= value
     */
    public infix fun String.gte(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.GTE, Key(this), Value(value)))

    /**
     * Creates a less-than filter: key < value
     */
    public infix fun String.lt(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.LT, Key(this), Value(value)))

    /**
     * Creates a less-than-or-equal filter: key <= value
     */
    public infix fun String.lte(value: Any): FilterOp =
        FilterOp(Expression(ExpressionType.LTE, Key(this), Value(value)))

    // ==================== Collection Operations ====================

    /**
     * Creates an IN filter: key in [values]
     */
    public infix fun String.isIn(values: List<Any>): FilterOp =
        FilterOp(Expression(ExpressionType.IN, Key(this), Value(values)))

    /**
     * Creates an IN filter: key in [values]
     */
    public fun String.isIn(vararg values: Any): FilterOp =
        FilterOp(Expression(ExpressionType.IN, Key(this), Value(values.toList())))

    /**
     * Creates a NOT IN filter: key not in [values]
     */
    public infix fun String.notIn(values: List<Any>): FilterOp =
        FilterOp(Expression(ExpressionType.NIN, Key(this), Value(values)))

    /**
     * Creates a NOT IN filter: key not in [values]
     */
    public fun String.notIn(vararg values: Any): FilterOp =
        FilterOp(Expression(ExpressionType.NIN, Key(this), Value(values.toList())))

    // ==================== Null Check Operations ====================

    /**
     * Creates an IS NULL filter: key is null
     */
    public fun String.isNull(): FilterOp =
        FilterOp(Expression(ExpressionType.ISNULL, Key(this)))

    /**
     * Creates an IS NOT NULL filter: key is not null
     */
    public fun String.isNotNull(): FilterOp =
        FilterOp(Expression(ExpressionType.ISNOTNULL, Key(this)))

    // ==================== Logical Operations ====================

    /**
     * Creates a grouped expression (parentheses).
     */
    public fun group(content: FilterOp): FilterOp =
        FilterOp(Group(content.build()))

    /**
     * Negates the given filter expression.
     */
    public fun not(content: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.NOT, content.build(), null))

    /**
     * Combines two filters with AND logic.
     */
    public fun and(left: FilterOp, right: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.AND, left.build(), right.build()))

    /**
     * Combines two filters with OR logic.
     */
    public fun or(left: FilterOp, right: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.OR, left.build(), right.build()))
}

// ==================== Entry Points ====================

/**
 * Entry point for the filter expression DSL.
 * Creates an Expression using the provided DSL block.
 *
 * @param block The DSL block that builds the filter expression
 * @return The built Expression
 *
 * Example:
 * ```kotlin
 * val filter = filterExpression {
 *     ("category" eq "books") and ("price" lt 100)
 * }
 * ```
 */
public inline fun filterExpression(block: FilterExpressionScope.() -> FilterOp): Expression {
    val scope = FilterExpressionScope()
    return scope.block().build()
}

/**
 * Entry point for the filter expression DSL that returns a FilterOp.
 * Useful when you need to compose filters outside the DSL scope.
 *
 * @param block The DSL block that builds the filter operation
 * @return The FilterOp wrapper
 */
public inline fun filterOp(block: FilterExpressionScope.() -> FilterOp): FilterOp {
    val scope = FilterExpressionScope()
    return scope.block()
}

// ==================== Extension Functions ====================

/**
 * Converts a FilterOp to an Expression.
 */
public fun FilterOp.toExpression(): Expression = build()

// ==================== Convenience Functions ====================

/**
 * Creates a simple equality filter without using the full DSL.
 */
public fun eq(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.EQ, Key(key), Value(value)))

/**
 * Creates a simple not-equal filter without using the full DSL.
 */
public fun ne(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.NE, Key(key), Value(value)))

/**
 * Creates a simple greater-than filter without using the full DSL.
 */
public fun gt(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.GT, Key(key), Value(value)))

/**
 * Creates a simple greater-than-or-equal filter without using the full DSL.
 */
public fun gte(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.GTE, Key(key), Value(value)))

/**
 * Creates a simple less-than filter without using the full DSL.
 */
public fun lt(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.LT, Key(key), Value(value)))

/**
 * Creates a simple less-than-or-equal filter without using the full DSL.
 */
public fun lte(key: String, value: Any): FilterOp =
    FilterOp(Expression(ExpressionType.LTE, Key(key), Value(value)))

/**
 * Creates a simple IN filter without using the full DSL.
 */
public fun isIn(key: String, vararg values: Any): FilterOp =
    FilterOp(Expression(ExpressionType.IN, Key(key), Value(values.toList())))

/**
 * Creates a simple NOT IN filter without using the full DSL.
 */
public fun notIn(key: String, vararg values: Any): FilterOp =
    FilterOp(Expression(ExpressionType.NIN, Key(key), Value(values.toList())))

/**
 * Creates a simple IS NULL filter without using the full DSL.
 */
public fun isNull(key: String): FilterOp =
    FilterOp(Expression(ExpressionType.ISNULL, Key(key)))

/**
 * Creates a simple IS NOT NULL filter without using the full DSL.
 */
public fun isNotNull(key: String): FilterOp =
    FilterOp(Expression(ExpressionType.ISNOTNULL, Key(key)))

// ==================== String Parsing ====================

/**
 * Exception thrown when filter expression parsing fails.
 */
public class FilterParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Parses a string into a filter Expression.
 *
 * Supported syntax:
 * - Comparison operators: `==`, `!=`, `>`, `>=`, `<`, `<=`
 * - Collection operators: `in`, `not in` (with values in brackets: `[1, 2, 3]`)
 * - Null checks: `is null`, `is not null`
 * - Logical operators: `and`, `or`, `not`
 * - Grouping: parentheses `(` and `)`
 * - String values: quoted with single or double quotes
 * - Numeric values: integers and decimals
 * - Boolean values: `true`, `false`
 *
 * Example:
 * ```kotlin
 * val filter = "category == 'books' and price < 100".toFilterExpression()
 * val filter2 = "(status == 'active' or status == 'pending') and count >= 5".toFilterExpression()
 * val filter3 = "tags in ['kotlin', 'java'] and deprecated is null".toFilterExpression()
 * ```
 *
 * @return The parsed Expression
 * @throws FilterParseException if the string cannot be parsed
 */
public fun String.toFilterExpression(): Expression {
    return FilterExpressionParser(this).parse()
}

/**
 * Parses a string into a FilterOp.
 *
 * @return The parsed FilterOp
 * @throws FilterParseException if the string cannot be parsed
 * @see toFilterExpression
 */
public fun String.toFilterOp(): FilterOp {
    return FilterOp(toFilterExpression())
}

/**
 * Tries to parse a string into a filter Expression.
 *
 * @return The parsed Expression, or null if parsing fails
 */
public fun String.toFilterExpressionOrNull(): Expression? {
    return try {
        toFilterExpression()
    } catch (e: FilterParseException) {
        null
    }
}

/**
 * Parser for filter expression strings.
 */
private class FilterExpressionParser(private val input: String) {
    private var pos = 0

    fun parse(): Expression {
        skipWhitespace()
        val result = parseOrExpression()
        skipWhitespace()
        if (pos < input.length) {
            throw FilterParseException("Unexpected character at position $pos: '${input[pos]}'")
        }
        return result
    }

    private fun parseOrExpression(): Expression {
        var left = parseAndExpression()
        while (true) {
            skipWhitespace()
            if (matchKeyword("or")) {
                skipWhitespace()
                val right = parseAndExpression()
                left = Expression(ExpressionType.OR, left, right)
            } else {
                break
            }
        }
        return left
    }

    private fun parseAndExpression(): Expression {
        var left = parseNotExpression()
        while (true) {
            skipWhitespace()
            if (matchKeyword("and")) {
                skipWhitespace()
                val right = parseNotExpression()
                left = Expression(ExpressionType.AND, left, right)
            } else {
                break
            }
        }
        return left
    }

    private fun parseNotExpression(): Expression {
        skipWhitespace()
        return if (matchKeyword("not")) {
            skipWhitespace()
            val expr = parseNotExpression()
            Expression(ExpressionType.NOT, expr, null)
        } else {
            parsePrimaryExpression()
        }
    }

    private fun parsePrimaryExpression(): Expression {
        skipWhitespace()

        // Handle grouped expressions
        if (peek() == '(') {
            advance() // consume '('
            skipWhitespace()
            val expr = parseOrExpression()
            skipWhitespace()
            if (peek() != ')') {
                throw FilterParseException("Expected ')' at position $pos")
            }
            advance() // consume ')'
            return expr
        }

        // Parse field comparison
        return parseComparison()
    }

    private fun parseComparison(): Expression {
        val key = parseIdentifier()
        skipWhitespace()

        // Check for null checks first
        if (matchKeyword("is")) {
            skipWhitespace()
            if (matchKeyword("not")) {
                skipWhitespace()
                if (!matchKeyword("null")) {
                    throw FilterParseException("Expected 'null' after 'is not' at position $pos")
                }
                return Expression(ExpressionType.ISNOTNULL, Key(key))
            } else if (matchKeyword("null")) {
                return Expression(ExpressionType.ISNULL, Key(key))
            } else {
                throw FilterParseException("Expected 'null' or 'not null' after 'is' at position $pos")
            }
        }

        // Check for 'not in'
        if (matchKeyword("not")) {
            skipWhitespace()
            if (!matchKeyword("in")) {
                throw FilterParseException("Expected 'in' after 'not' at position $pos")
            }
            skipWhitespace()
            val values = parseList()
            return Expression(ExpressionType.NIN, Key(key), Value(values))
        }

        // Check for 'in'
        if (matchKeyword("in")) {
            skipWhitespace()
            val values = parseList()
            return Expression(ExpressionType.IN, Key(key), Value(values))
        }

        // Parse comparison operator
        val operator = parseOperator()
        skipWhitespace()
        val value = parseValue()

        val type = when (operator) {
            "==" -> ExpressionType.EQ
            "!=" -> ExpressionType.NE
            ">" -> ExpressionType.GT
            ">=" -> ExpressionType.GTE
            "<" -> ExpressionType.LT
            "<=" -> ExpressionType.LTE
            else -> throw FilterParseException("Unknown operator: $operator")
        }

        return Expression(type, Key(key), Value(value))
    }

    private fun parseIdentifier(): String {
        skipWhitespace()
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '.')) {
            pos++
        }
        if (pos == start) {
            throw FilterParseException("Expected identifier at position $pos")
        }
        return input.substring(start, pos)
    }

    private fun parseOperator(): String {
        skipWhitespace()
        val operators = listOf("==", "!=", ">=", "<=", ">", "<")
        for (op in operators) {
            if (input.substring(pos).startsWith(op)) {
                pos += op.length
                return op
            }
        }
        throw FilterParseException("Expected operator at position $pos")
    }

    private fun parseValue(): Any {
        skipWhitespace()
        return when {
            peek() == '"' || peek() == '\'' -> parseString()
            peek() == '[' -> parseList()
            peek()?.isDigit() == true || peek() == '-' -> parseNumber()
            matchKeyword("true") -> true
            matchKeyword("false") -> false
            matchKeyword("null") -> "null" // Return string "null" for null literal
            else -> throw FilterParseException("Expected value at position $pos")
        }
    }

    private fun parseString(): String {
        val quote = advance() // consume opening quote
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != quote) {
            if (input[pos] == '\\' && pos + 1 < input.length) {
                pos++ // skip backslash
                sb.append(input[pos])
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        if (pos >= input.length) {
            throw FilterParseException("Unterminated string at position $pos")
        }
        advance() // consume closing quote
        return sb.toString()
    }

    private fun parseNumber(): Number {
        val start = pos
        if (peek() == '-') {
            pos++
        }
        while (pos < input.length && input[pos].isDigit()) {
            pos++
        }
        if (pos < input.length && input[pos] == '.') {
            pos++
            while (pos < input.length && input[pos].isDigit()) {
                pos++
            }
            return input.substring(start, pos).toDouble()
        }
        return input.substring(start, pos).toLong()
    }

    private fun parseList(): List<Any> {
        if (peek() != '[') {
            throw FilterParseException("Expected '[' at position $pos")
        }
        advance() // consume '['
        skipWhitespace()

        val values = mutableListOf<Any>()
        if (peek() != ']') {
            values.add(parseValue())
            skipWhitespace()
            while (peek() == ',') {
                advance() // consume ','
                skipWhitespace()
                values.add(parseValue())
                skipWhitespace()
            }
        }

        if (peek() != ']') {
            throw FilterParseException("Expected ']' at position $pos")
        }
        advance() // consume ']'
        return values
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }

    private fun peek(): Char? = if (pos < input.length) input[pos] else null

    private fun advance(): Char {
        if (pos >= input.length) {
            throw FilterParseException("Unexpected end of input")
        }
        return input[pos++]
    }

    private fun matchKeyword(keyword: String): Boolean {
        val remaining = input.substring(pos)
        if (remaining.lowercase().startsWith(keyword.lowercase())) {
            // Make sure it's a complete word (not part of an identifier)
            val nextPos = pos + keyword.length
            if (nextPos >= input.length || !input[nextPos].isLetterOrDigit()) {
                pos = nextPos
                return true
            }
        }
        return false
    }
}
