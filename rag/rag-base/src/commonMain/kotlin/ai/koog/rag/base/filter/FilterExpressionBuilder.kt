package ai.koog.rag.base.filter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

// ==================== Core Types ====================

/**
 * Enumeration of supported expression types for filter operations.
 */
@Serializable
public enum class ExpressionType {
    /** Equal */
    EQ,

    /** Not equal */
    NE,

    /** Greater than */
    GT,

    /** Greater than or equal */
    GTE,

    /** Less than */
    LT,

    /** Less than or equal */
    LTE,

    /** In collection */
    IN,

    /** Not in collection */
    NIN,

    /** Logical AND */
    AND,

    /** Logical OR */
    OR,

    /** Logical NOT */
    NOT,

    /** Is null check */
    ISNULL,

    /** Is not null check */
    ISNOTNULL
}

/**
 * Represents a key (field name) in a filter expression.
 */
@Serializable
@JvmInline
public value class Key(public val name: String)

/**
 * Represents a value in a filter expression.
 *
 * The wrapped [value] is a [JsonElement]: either a [JsonPrimitive] (for strings, numbers, booleans)
 * or a [JsonArray] (for collection operations like IN / NOT IN).
 */
@Serializable
@JvmInline
public value class Value(public val value: JsonElement)

/**
 * Base sealed interface for filter operands.
 */
@Serializable
public sealed interface Operand

/**
 * Represents a filter expression with a type, left operand, and optional right operand.
 *
 * For comparison operations (EQ, NE, GT, GTE, LT, LTE): [left] is a [Key], [right] is a [Value].
 * For collection operations (IN, NIN): [left] is a [Key], [right] is a [Value] wrapping a [JsonArray].
 * For logical operations (AND, OR): [left] and [right] are both [Expression].
 * For NOT: [left] is an [Expression], [right] is null.
 * For null checks (ISNULL, ISNOTNULL): [left] is a [Key], [right] is null.
 */
@Serializable
public data class Expression(
    val type: ExpressionType,
    val left: Operand,
    val right: Operand? = null
) : Operand

/**
 * Represents a grouped expression (parentheses).
 */
@Serializable
public data class Group(val content: Expression) : Operand

/**
 * Wraps a [Key] as an [Operand] for use in [Expression].
 */
@Serializable
@JvmInline
public value class KeyOperand(public val key: Key) : Operand

/**
 * Wraps a [Value] as an [Operand] for use in [Expression].
 */
@Serializable
@JvmInline
public value class ValueOperand(public val value: Value) : Operand

// ==================== FilterOp Wrapper ====================

/**
 * Wrapper class for filter operations.
 * Provides a fluent API for combining filter expressions.
 *
 * Instances are created via [FilterExpressionBuilder] methods.
 */
@JvmInline
public value class FilterOp(public val operand: Operand) {

    /**
     * Combines this filter with another using AND logic.
     */
    public fun and(other: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.AND, toExpression(), other.toExpression()))

    /**
     * Combines this filter with another using OR logic.
     */
    public fun or(other: FilterOp): FilterOp =
        FilterOp(Expression(ExpressionType.OR, toExpression(), other.toExpression()))

    /**
     * Builds the final [Expression] from this operation.
     */
    public fun build(): Expression = toExpression()

    /**
     * Converts the operand to an [Expression].
     */
    private fun toExpression(): Expression = when (val op = operand) {
        is Group -> op.content
        is Expression -> op
        else -> throw IllegalStateException("Cannot convert $op to Expression")
    }
}

// ==================== Builder ====================

/**
 * Builder for constructing filter expressions.
 *
 * This is the single entry point for creating filter expressions.
 *
 * Values are stored as [JsonPrimitive] (for strings, numbers, booleans) or [JsonArray] (for collections).
 *
 * Example (Kotlin):
 * ```kotlin
 * val b = FilterExpressionBuilder()
 * val filter = b.and(b.eq("category", "books"), b.lt("price", 100)).build()
 * ```
 *
 * Parsing from string:
 * ```kotlin
 * val filter = FilterExpressionBuilder.fromString("category == 'books' and price < 100")
 * ```
 */
public class FilterExpressionBuilder {

    // ==================== Comparison Operations (String values) ====================

    /**
     * Creates an equality filter: key == value.
     */
    public fun eq(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.EQ, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates an equality filter: key == value.
     */
    public fun eq(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.EQ, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates an equality filter: key == value.
     */
    public fun eq(key: String, value: Boolean): FilterOp =
        FilterOp(Expression(ExpressionType.EQ, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a not-equal filter: key != value.
     */
    public fun ne(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.NE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a not-equal filter: key != value.
     */
    public fun ne(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.NE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a not-equal filter: key != value.
     */
    public fun ne(key: String, value: Boolean): FilterOp =
        FilterOp(Expression(ExpressionType.NE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a greater-than filter: key > value.
     */
    public fun gt(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.GT, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a greater-than filter: key > value.
     */
    public fun gt(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.GT, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a greater-than-or-equal filter: key >= value.
     */
    public fun gte(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.GTE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a greater-than-or-equal filter: key >= value.
     */
    public fun gte(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.GTE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a less-than filter: key < value.
     */
    public fun lt(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.LT, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a less-than filter: key < value.
     */
    public fun lt(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.LT, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a less-than-or-equal filter: key <= value.
     */
    public fun lte(key: String, value: Number): FilterOp =
        FilterOp(Expression(ExpressionType.LTE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    /**
     * Creates a less-than-or-equal filter: key <= value.
     */
    public fun lte(key: String, value: String): FilterOp =
        FilterOp(Expression(ExpressionType.LTE, KeyOperand(Key(key)), ValueOperand(Value(JsonPrimitive(value)))))

    // ==================== Logical Operations ====================

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

    // ==================== Collection Operations ====================

    /**
     * Creates an IN filter: key in [values].
     */
    public fun isIn(key: String, values: List<JsonPrimitive>): FilterOp =
        FilterOp(Expression(ExpressionType.IN, KeyOperand(Key(key)), ValueOperand(Value(JsonArray(values)))))

    /**
     * Creates an IN filter: key in [values].
     */
    public fun isIn(key: String, vararg values: JsonPrimitive): FilterOp =
        isIn(key, values.toList())

    /**
     * Creates a NOT IN filter: key not in [values].
     */
    public fun notIn(key: String, values: List<JsonPrimitive>): FilterOp =
        FilterOp(Expression(ExpressionType.NIN, KeyOperand(Key(key)), ValueOperand(Value(JsonArray(values)))))

    /**
     * Creates a NOT IN filter: key not in [values].
     */
    public fun notIn(key: String, vararg values: JsonPrimitive): FilterOp =
        notIn(key, values.toList())

    // ==================== Null Check Operations ====================

    /**
     * Creates an IS NULL filter.
     */
    public fun isNull(key: String): FilterOp =
        FilterOp(Expression(ExpressionType.ISNULL, KeyOperand(Key(key))))

    /**
     * Creates an IS NOT NULL filter.
     */
    public fun isNotNull(key: String): FilterOp =
        FilterOp(Expression(ExpressionType.ISNOTNULL, KeyOperand(Key(key))))

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
     * Companion object with static methods for parsing.
     */
    public companion object {
        /**
         * Parses a string into a filter [Expression].
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
         * val filter = FilterExpressionBuilder.fromString("category == 'books' and price < 100")
         * ```
         *
         * @param input the filter expression string to parse
         * @return the parsed [Expression]
         * @throws FilterParseException if the string cannot be parsed
         */
        @JvmStatic
        public fun fromString(input: String): Expression {
            return FilterExpressionParser(input).parse()
        }

        /**
         * Tries to parse a string into a filter [Expression].
         *
         * @param input the filter expression string to parse
         * @return the parsed [Expression], or null if parsing fails
         */
        @JvmStatic
        public fun fromStringOrNull(input: String): Expression? {
            return try {
                fromString(input)
            } catch (_: FilterParseException) {
                null
            }
        }
    }
}
