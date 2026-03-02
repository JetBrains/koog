package ai.koog.rag.base.filter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FilterExpressionBuilderTest {

    private val builder = FilterExpressionBuilder()

    // ==================== Comparison Operations ====================

    @Test
    fun testEqExpression() {
        val expr = builder.eq("category", "books").build()
        assertEquals(ExpressionType.EQ, expr.type)
        assertEquals(KeyOperand(Key("category")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive("books"))), expr.right)
    }

    @Test
    fun testNeExpression() {
        val expr = builder.ne("status", "deleted").build()
        assertEquals(ExpressionType.NE, expr.type)
        assertEquals(KeyOperand(Key("status")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive("deleted"))), expr.right)
    }

    @Test
    fun testGtExpression() {
        val expr = builder.gt("price", 100).build()
        assertEquals(ExpressionType.GT, expr.type)
        assertEquals(KeyOperand(Key("price")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(100))), expr.right)
    }

    @Test
    fun testGteExpression() {
        val expr = builder.gte("price", 50).build()
        assertEquals(ExpressionType.GTE, expr.type)
        assertEquals(KeyOperand(Key("price")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(50))), expr.right)
    }

    @Test
    fun testLtExpression() {
        val expr = builder.lt("count", 10).build()
        assertEquals(ExpressionType.LT, expr.type)
        assertEquals(KeyOperand(Key("count")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(10))), expr.right)
    }

    @Test
    fun testLteExpression() {
        val expr = builder.lte("count", 5).build()
        assertEquals(ExpressionType.LTE, expr.type)
        assertEquals(KeyOperand(Key("count")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(5))), expr.right)
    }

    // ==================== Collection Operations ====================

    @Test
    fun testIsInWithList() {
        val expr =
            builder.isIn("color", listOf(JsonPrimitive("red"), JsonPrimitive("blue"), JsonPrimitive("green"))).build()
        assertEquals(ExpressionType.IN, expr.type)
        assertEquals(KeyOperand(Key("color")), expr.left)
        assertEquals(
            ValueOperand(Value(JsonArray(listOf(JsonPrimitive("red"), JsonPrimitive("blue"), JsonPrimitive("green"))))),
            expr.right
        )
    }

    @Test
    fun testNotInWithList() {
        val expr = builder.notIn("status", listOf(JsonPrimitive("archived"), JsonPrimitive("deleted"))).build()
        assertEquals(ExpressionType.NIN, expr.type)
        assertEquals(KeyOperand(Key("status")), expr.left)
        assertEquals(
            ValueOperand(Value(JsonArray(listOf(JsonPrimitive("archived"), JsonPrimitive("deleted"))))),
            expr.right
        )
    }

    // ==================== Null Check Operations ====================

    @Test
    fun testIsNull() {
        val expr = builder.isNull("description").build()
        assertEquals(ExpressionType.ISNULL, expr.type)
        assertEquals(KeyOperand(Key("description")), expr.left)
        assertNull(expr.right)
    }

    @Test
    fun testIsNotNull() {
        val expr = builder.isNotNull("description").build()
        assertEquals(ExpressionType.ISNOTNULL, expr.type)
        assertEquals(KeyOperand(Key("description")), expr.left)
        assertNull(expr.right)
    }

    // ==================== Logical Operations ====================

    @Test
    fun testAnd() {
        val expr = builder.and(builder.eq("category", "books"), builder.lt("price", 100)).build()
        assertEquals(ExpressionType.AND, expr.type)

        val left = expr.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals(KeyOperand(Key("category")), left.left)
        assertEquals(ValueOperand(Value(JsonPrimitive("books"))), left.right)

        val right = expr.right as Expression
        assertEquals(ExpressionType.LT, right.type)
        assertEquals(KeyOperand(Key("price")), right.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(100))), right.right)
    }

    @Test
    fun testOr() {
        val expr = builder.or(builder.eq("status", "active"), builder.eq("status", "pending")).build()
        assertEquals(ExpressionType.OR, expr.type)

        val left = expr.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals(ValueOperand(Value(JsonPrimitive("active"))), left.right)

        val right = expr.right as Expression
        assertEquals(ExpressionType.EQ, right.type)
        assertEquals(ValueOperand(Value(JsonPrimitive("pending"))), right.right)
    }

    @Test
    fun testFilterOpAnd() {
        val expr = builder.eq("a", 1).and(builder.ne("b", 2)).build()
        assertEquals(ExpressionType.AND, expr.type)
    }

    @Test
    fun testFilterOpOr() {
        val expr = builder.eq("a", 1).or(builder.eq("b", 2)).build()
        assertEquals(ExpressionType.OR, expr.type)
    }

    @Test
    fun testNot() {
        val expr = builder.not(builder.eq("deleted", true)).build()
        assertEquals(ExpressionType.NOT, expr.type)
        val inner = expr.left as Expression
        assertEquals(ExpressionType.EQ, inner.type)
        assertEquals(KeyOperand(Key("deleted")), inner.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(true))), inner.right)
    }

    @Test
    fun testGroup() {
        val expr = builder.group(builder.eq("x", 1)).build()
        assertEquals(ExpressionType.EQ, expr.type)
    }

    // ==================== Complex Expressions ====================

    @Test
    fun testComplexExpression() {
        val expr = builder.and(
            builder.or(builder.eq("category", "books"), builder.eq("category", "electronics")),
            builder.lte("price", 50)
        ).build()
        assertEquals(ExpressionType.AND, expr.type)

        val left = expr.left as Expression
        assertEquals(ExpressionType.OR, left.type)

        val right = expr.right as Expression
        assertEquals(ExpressionType.LTE, right.type)
        assertEquals(KeyOperand(Key("price")), right.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(50))), right.right)
    }

    @Test
    fun testBooleanValue() {
        val expr = builder.eq("inStock", true).build()
        assertEquals(ExpressionType.EQ, expr.type)
        assertEquals(KeyOperand(Key("inStock")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(true))), expr.right)
    }

    @Test
    fun testDoubleValue() {
        val expr = builder.gte("rating", 4.5).build()
        assertEquals(ExpressionType.GTE, expr.type)
        assertEquals(KeyOperand(Key("rating")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(4.5))), expr.right)
    }

    // ==================== String Parsing ====================

    @Test
    fun testFromStringSimpleEq() {
        val expr = FilterExpressionBuilder.fromString("category == 'books'")
        assertEquals(ExpressionType.EQ, expr.type)
        assertEquals(KeyOperand(Key("category")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive("books"))), expr.right)
    }

    @Test
    fun testFromStringNumericComparison() {
        val expr = FilterExpressionBuilder.fromString("price < 100")
        assertEquals(ExpressionType.LT, expr.type)
        assertEquals(KeyOperand(Key("price")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(100L))), expr.right)
    }

    @Test
    fun testFromStringAndExpression() {
        val expr = FilterExpressionBuilder.fromString("category == 'books' and price < 100")
        assertEquals(ExpressionType.AND, expr.type)
    }

    @Test
    fun testFromStringOrExpression() {
        val expr = FilterExpressionBuilder.fromString("status == 'active' or status == 'pending'")
        assertEquals(ExpressionType.OR, expr.type)
    }

    @Test
    fun testFromStringIsNull() {
        val expr = FilterExpressionBuilder.fromString("description is null")
        assertEquals(ExpressionType.ISNULL, expr.type)
        assertEquals(KeyOperand(Key("description")), expr.left)
    }

    @Test
    fun testFromStringIsNotNull() {
        val expr = FilterExpressionBuilder.fromString("description is not null")
        assertEquals(ExpressionType.ISNOTNULL, expr.type)
        assertEquals(KeyOperand(Key("description")), expr.left)
    }

    @Test
    fun testFromStringIn() {
        val expr = FilterExpressionBuilder.fromString("tags in ['kotlin', 'java']")
        assertEquals(ExpressionType.IN, expr.type)
        assertEquals(KeyOperand(Key("tags")), expr.left)
        assertEquals(
            ValueOperand(Value(JsonArray(listOf(JsonPrimitive("kotlin"), JsonPrimitive("java"))))),
            expr.right
        )
    }

    @Test
    fun testFromStringNotIn() {
        val expr = FilterExpressionBuilder.fromString("status not in ['deleted', 'archived']")
        assertEquals(ExpressionType.NIN, expr.type)
        assertEquals(KeyOperand(Key("status")), expr.left)
    }

    @Test
    fun testFromStringGroupedExpression() {
        val expr = FilterExpressionBuilder.fromString("(status == 'active' or status == 'pending') and count >= 5")
        assertEquals(ExpressionType.AND, expr.type)

        val left = expr.left as Expression
        assertEquals(ExpressionType.OR, left.type)

        val right = expr.right as Expression
        assertEquals(ExpressionType.GTE, right.type)
    }

    @Test
    fun testFromStringBooleanValue() {
        val expr = FilterExpressionBuilder.fromString("active == true")
        assertEquals(ExpressionType.EQ, expr.type)
        assertEquals(KeyOperand(Key("active")), expr.left)
        assertEquals(ValueOperand(Value(JsonPrimitive(true))), expr.right)
    }

    @Test
    fun testFromStringDoubleValue() {
        val expr = FilterExpressionBuilder.fromString("rating >= 4.5")
        assertEquals(ExpressionType.GTE, expr.type)
        assertEquals(ValueOperand(Value(JsonPrimitive(4.5))), expr.right)
    }

    @Test
    fun testFromStringOrNullValid() {
        val expr = FilterExpressionBuilder.fromStringOrNull("x == 1")
        assertEquals(ExpressionType.EQ, expr?.type)
    }

    @Test
    fun testFromStringOrNullInvalid() {
        val expr = FilterExpressionBuilder.fromStringOrNull("invalid %%% expression")
        assertNull(expr)
    }

    @Test
    fun testFromStringInvalidThrows() {
        assertFailsWith<FilterParseException> {
            FilterExpressionBuilder.fromString("invalid %%% expression")
        }
    }
}
