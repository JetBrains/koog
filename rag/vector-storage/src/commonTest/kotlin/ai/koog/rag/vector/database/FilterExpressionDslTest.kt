package ai.koog.rag.vector.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FilterExpressionDslTest {

    @Test
    fun `eq creates equality expression`() {
        val filter = filterExpression {
            "category" eq "books"
        }

        assertEquals(ExpressionType.EQ, filter.type)
        assertIs<Key>(filter.left)
        assertEquals("category", (filter.left as Key).name)
        assertIs<Value>(filter.right)
        assertEquals("books", (filter.right as Value).value)
    }

    @Test
    fun `ne creates not-equal expression`() {
        val filter = filterExpression {
            "status" ne "deleted"
        }

        assertEquals(ExpressionType.NE, filter.type)
        assertEquals("status", (filter.left as Key).name)
        assertEquals("deleted", (filter.right as Value).value)
    }

    @Test
    fun `gt creates greater-than expression`() {
        val filter = filterExpression {
            "price" gt 100
        }

        assertEquals(ExpressionType.GT, filter.type)
        assertEquals("price", (filter.left as Key).name)
        assertEquals(100, (filter.right as Value).value)
    }

    @Test
    fun `gte creates greater-than-or-equal expression`() {
        val filter = filterExpression {
            "rating" gte 4.5
        }

        assertEquals(ExpressionType.GTE, filter.type)
        assertEquals("rating", (filter.left as Key).name)
        assertEquals(4.5, (filter.right as Value).value)
    }

    @Test
    fun `lt creates less-than expression`() {
        val filter = filterExpression {
            "quantity" lt 10
        }

        assertEquals(ExpressionType.LT, filter.type)
        assertEquals("quantity", (filter.left as Key).name)
        assertEquals(10, (filter.right as Value).value)
    }

    @Test
    fun `lte creates less-than-or-equal expression`() {
        val filter = filterExpression {
            "discount" lte 0.5
        }

        assertEquals(ExpressionType.LTE, filter.type)
        assertEquals("discount", (filter.left as Key).name)
        assertEquals(0.5, (filter.right as Value).value)
    }

    @Test
    fun `isIn creates IN expression with list`() {
        val filter = filterExpression {
            "category" isIn listOf("books", "electronics", "clothing")
        }

        assertEquals(ExpressionType.IN, filter.type)
        assertEquals("category", (filter.left as Key).name)
        assertEquals(listOf("books", "electronics", "clothing"), (filter.right as Value).value)
    }

    @Test
    fun `isIn creates IN expression with varargs`() {
        val filter = filterExpression {
            "category".isIn("books", "electronics")
        }

        assertEquals(ExpressionType.IN, filter.type)
        assertEquals("category", (filter.left as Key).name)
        assertEquals(listOf("books", "electronics"), (filter.right as Value).value)
    }

    @Test
    fun `notIn creates NIN expression with list`() {
        val filter = filterExpression {
            "status" notIn listOf("deleted", "archived")
        }

        assertEquals(ExpressionType.NIN, filter.type)
        assertEquals("status", (filter.left as Key).name)
        assertEquals(listOf("deleted", "archived"), (filter.right as Value).value)
    }

    @Test
    fun `notIn creates NIN expression with varargs`() {
        val filter = filterExpression {
            "status".notIn("deleted", "archived")
        }

        assertEquals(ExpressionType.NIN, filter.type)
        assertEquals("status", (filter.left as Key).name)
        assertEquals(listOf("deleted", "archived"), (filter.right as Value).value)
    }

    @Test
    fun `isNull creates IS NULL expression`() {
        val filter = filterExpression {
            "deletedAt".isNull()
        }

        assertEquals(ExpressionType.ISNULL, filter.type)
        assertEquals("deletedAt", (filter.left as Key).name)
    }

    @Test
    fun `isNotNull creates IS NOT NULL expression`() {
        val filter = filterExpression {
            "createdAt".isNotNull()
        }

        assertEquals(ExpressionType.ISNOTNULL, filter.type)
        assertEquals("createdAt", (filter.left as Key).name)
    }

    @Test
    fun `and combines two expressions`() {
        val filter = filterExpression {
            ("category" eq "books") and ("price" lt 100)
        }

        assertEquals(ExpressionType.AND, filter.type)

        val left = filter.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals("category", (left.left as Key).name)

        val right = filter.right as Expression
        assertEquals(ExpressionType.LT, right.type)
        assertEquals("price", (right.left as Key).name)
    }

    @Test
    fun `or combines two expressions`() {
        val filter = filterExpression {
            ("category" eq "books") or ("category" eq "electronics")
        }

        assertEquals(ExpressionType.OR, filter.type)

        val left = filter.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals("books", (left.right as Value).value)

        val right = filter.right as Expression
        assertEquals(ExpressionType.EQ, right.type)
        assertEquals("electronics", (right.right as Value).value)
    }

    @Test
    fun `not negates expression`() {
        val filter = filterExpression {
            not("status" eq "deleted")
        }

        assertEquals(ExpressionType.NOT, filter.type)

        val inner = filter.left as Expression
        assertEquals(ExpressionType.EQ, inner.type)
        assertEquals("status", (inner.left as Key).name)
        assertEquals("deleted", (inner.right as Value).value)
    }

    @Test
    fun `complex expression with multiple operators`() {
        val filter = filterExpression {
            (("category" eq "books") or ("category" eq "electronics")) and ("price" lte 50)
        }

        assertEquals(ExpressionType.AND, filter.type)

        val orExpr = filter.left as Expression
        assertEquals(ExpressionType.OR, orExpr.type)

        val priceExpr = filter.right as Expression
        assertEquals(ExpressionType.LTE, priceExpr.type)
        assertEquals("price", (priceExpr.left as Key).name)
        assertEquals(50, (priceExpr.right as Value).value)
    }

    @Test
    fun `group creates grouped expression`() {
        val filter = filterExpression {
            group(("category" eq "books") or ("category" eq "electronics"))
        }

        // After build(), the top-level group is removed, so we get the inner OR expression
        assertEquals(ExpressionType.OR, filter.type)
    }

    @Test
    fun `filterOp returns FilterOp for composition`() {
        val op1 = filterOp { "category" eq "books" }
        val op2 = filterOp { "price" lt 100 }

        val combined = op1 and op2
        val filter = combined.build()

        assertEquals(ExpressionType.AND, filter.type)
    }

    @Test
    fun `convenience functions work outside DSL scope`() {
        val filter = (eq("category", "books") and lt("price", 100)).build()

        assertEquals(ExpressionType.AND, filter.type)

        val left = filter.left as Expression
        assertEquals(ExpressionType.EQ, left.type)

        val right = filter.right as Expression
        assertEquals(ExpressionType.LT, right.type)
    }

    @Test
    fun `toExpression converts FilterOp to Expression`() {
        val op = eq("category", "books")
        val expression = op.toExpression()

        assertEquals(ExpressionType.EQ, expression.type)
        assertEquals("category", (expression.left as Key).name)
        assertEquals("books", (expression.right as Value).value)
    }

    @Test
    fun `works with boolean values`() {
        val filter = filterExpression {
            "inStock" eq true
        }

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("inStock", (filter.left as Key).name)
        assertEquals(true, (filter.right as Value).value)
    }

    @Test
    fun `works with numeric values`() {
        val filter = filterExpression {
            ("intValue" eq 42) and ("longValue" eq 123456789L) and ("doubleValue" eq 3.14)
        }

        assertEquals(ExpressionType.AND, filter.type)
    }

    @Test
    fun `and function in scope combines expressions`() {
        val filter = filterExpression {
            and("category" eq "books", "price" lt 100)
        }

        assertEquals(ExpressionType.AND, filter.type)
    }

    @Test
    fun `or function in scope combines expressions`() {
        val filter = filterExpression {
            or("category" eq "books", "category" eq "electronics")
        }

        assertEquals(ExpressionType.OR, filter.type)
    }

    // ==================== String Parsing Tests ====================

    @Test
    fun `toFilterExpression parses simple equality with string value`() {
        val filter = "category == 'books'".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("category", (filter.left as Key).name)
        assertEquals("books", (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses equality with double quotes`() {
        val filter = """name == "John Doe"""".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("name", (filter.left as Key).name)
        assertEquals("John Doe", (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses not-equal operator`() {
        val filter = "status != 'deleted'".toFilterExpression()

        assertEquals(ExpressionType.NE, filter.type)
        assertEquals("status", (filter.left as Key).name)
        assertEquals("deleted", (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses greater-than with integer`() {
        val filter = "price > 100".toFilterExpression()

        assertEquals(ExpressionType.GT, filter.type)
        assertEquals("price", (filter.left as Key).name)
        assertEquals(100L, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses greater-than-or-equal`() {
        val filter = "rating >= 4".toFilterExpression()

        assertEquals(ExpressionType.GTE, filter.type)
        assertEquals("rating", (filter.left as Key).name)
        assertEquals(4L, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses less-than with decimal`() {
        val filter = "discount < 0.5".toFilterExpression()

        assertEquals(ExpressionType.LT, filter.type)
        assertEquals("discount", (filter.left as Key).name)
        assertEquals(0.5, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses less-than-or-equal`() {
        val filter = "quantity <= 10".toFilterExpression()

        assertEquals(ExpressionType.LTE, filter.type)
        assertEquals("quantity", (filter.left as Key).name)
        assertEquals(10L, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses negative numbers`() {
        val filter = "temperature > -10".toFilterExpression()

        assertEquals(ExpressionType.GT, filter.type)
        assertEquals("temperature", (filter.left as Key).name)
        assertEquals(-10L, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses boolean true`() {
        val filter = "active == true".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("active", (filter.left as Key).name)
        assertEquals(true, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses boolean false`() {
        val filter = "deleted == false".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("deleted", (filter.left as Key).name)
        assertEquals(false, (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses IN operator with string list`() {
        val filter = "category in ['books', 'electronics']".toFilterExpression()

        assertEquals(ExpressionType.IN, filter.type)
        assertEquals("category", (filter.left as Key).name)
        assertEquals(listOf("books", "electronics"), (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses IN operator with number list`() {
        val filter = "id in [1, 2, 3]".toFilterExpression()

        assertEquals(ExpressionType.IN, filter.type)
        assertEquals("id", (filter.left as Key).name)
        assertEquals(listOf(1L, 2L, 3L), (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses NOT IN operator`() {
        val filter = "status not in ['deleted', 'archived']".toFilterExpression()

        assertEquals(ExpressionType.NIN, filter.type)
        assertEquals("status", (filter.left as Key).name)
        assertEquals(listOf("deleted", "archived"), (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses IS NULL`() {
        val filter = "deletedAt is null".toFilterExpression()

        assertEquals(ExpressionType.ISNULL, filter.type)
        assertEquals("deletedAt", (filter.left as Key).name)
    }

    @Test
    fun `toFilterExpression parses IS NOT NULL`() {
        val filter = "createdAt is not null".toFilterExpression()

        assertEquals(ExpressionType.ISNOTNULL, filter.type)
        assertEquals("createdAt", (filter.left as Key).name)
    }

    @Test
    fun `toFilterExpression parses AND operator`() {
        val filter = "category == 'books' and price < 100".toFilterExpression()

        assertEquals(ExpressionType.AND, filter.type)

        val left = filter.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals("category", (left.left as Key).name)

        val right = filter.right as Expression
        assertEquals(ExpressionType.LT, right.type)
        assertEquals("price", (right.left as Key).name)
    }

    @Test
    fun `toFilterExpression parses OR operator`() {
        val filter = "category == 'books' or category == 'electronics'".toFilterExpression()

        assertEquals(ExpressionType.OR, filter.type)

        val left = filter.left as Expression
        assertEquals(ExpressionType.EQ, left.type)
        assertEquals("books", (left.right as Value).value)

        val right = filter.right as Expression
        assertEquals(ExpressionType.EQ, right.type)
        assertEquals("electronics", (right.right as Value).value)
    }

    @Test
    fun `toFilterExpression parses NOT operator`() {
        val filter = "not active == true".toFilterExpression()

        assertEquals(ExpressionType.NOT, filter.type)

        val inner = filter.left as Expression
        assertEquals(ExpressionType.EQ, inner.type)
        assertEquals("active", (inner.left as Key).name)
    }

    @Test
    fun `toFilterExpression parses grouped expressions`() {
        val filter = "(category == 'books' or category == 'electronics') and price < 100".toFilterExpression()

        assertEquals(ExpressionType.AND, filter.type)

        val orExpr = filter.left as Expression
        assertEquals(ExpressionType.OR, orExpr.type)

        val priceExpr = filter.right as Expression
        assertEquals(ExpressionType.LT, priceExpr.type)
    }

    @Test
    fun `toFilterExpression parses complex nested expression`() {
        val filter =
            "(status == 'active' or status == 'pending') and (price >= 10 and price <= 100)".toFilterExpression()

        assertEquals(ExpressionType.AND, filter.type)

        val statusOr = filter.left as Expression
        assertEquals(ExpressionType.OR, statusOr.type)

        val priceAnd = filter.right as Expression
        assertEquals(ExpressionType.AND, priceAnd.type)
    }

    @Test
    fun `toFilterExpression handles case-insensitive keywords`() {
        val filter = "category == 'books' AND price < 100 OR status == 'sale'".toFilterExpression()

        // OR has lower precedence, so it's the root
        assertEquals(ExpressionType.OR, filter.type)
    }

    @Test
    fun `toFilterExpression parses field names with dots`() {
        val filter = "metadata.author == 'John'".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("metadata.author", (filter.left as Key).name)
    }

    @Test
    fun `toFilterExpression parses field names with underscores`() {
        val filter = "created_at is not null".toFilterExpression()

        assertEquals(ExpressionType.ISNOTNULL, filter.type)
        assertEquals("created_at", (filter.left as Key).name)
    }

    @Test
    fun `toFilterOp returns FilterOp wrapper`() {
        val op = "category == 'books'".toFilterOp()
        val filter = op.build()

        assertEquals(ExpressionType.EQ, filter.type)
    }

    @Test
    fun `toFilterOp can be combined with DSL operations`() {
        val op1 = "category == 'books'".toFilterOp()
        val op2 = "price < 100".toFilterOp()

        val combined = (op1 and op2).build()

        assertEquals(ExpressionType.AND, combined.type)
    }

    @Test
    fun `toFilterExpressionOrNull returns null for invalid input`() {
        val result = "invalid expression without operator".toFilterExpressionOrNull()

        assertNull(result)
    }

    @Test
    fun `toFilterExpressionOrNull returns expression for valid input`() {
        val result = "category == 'books'".toFilterExpressionOrNull()

        assertIs<Expression>(result)
        assertEquals(ExpressionType.EQ, result.type)
    }

    @Test
    fun `toFilterExpression handles escaped quotes in strings`() {
        val filter = """name == 'John\'s Book'""".toFilterExpression()

        assertEquals(ExpressionType.EQ, filter.type)
        assertEquals("John's Book", (filter.right as Value).value)
    }

    @Test
    fun `toFilterExpression handles whitespace variations`() {
        val filter = "  category   ==   'books'   and   price  <  100  ".toFilterExpression()

        assertEquals(ExpressionType.AND, filter.type)
    }

    @Test
    fun `toFilterExpression parses empty list`() {
        val filter = "tags in []".toFilterExpression()

        assertEquals(ExpressionType.IN, filter.type)
        assertEquals(emptyList<Any>(), (filter.right as Value).value)
    }
}
