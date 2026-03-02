package ai.koog.rag.base.filter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Exception thrown when filter expression parsing fails.
 */
public class FilterParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Parser for filter expression strings.
 *
 * Use [FilterExpressionBuilder.fromString] or [FilterExpressionBuilder.fromStringOrNull] instead of using this class directly.
 */
internal class FilterExpressionParser(private val input: String) {
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

        if (peek() == '(') {
            advance()
            skipWhitespace()
            val expr = parseOrExpression()
            skipWhitespace()
            if (peek() != ')') {
                throw FilterParseException("Expected ')' at position $pos")
            }
            advance()
            return expr
        }

        return parseComparison()
    }

    private fun parseComparison(): Expression {
        val key = parseIdentifier()
        skipWhitespace()

        if (matchKeyword("is")) {
            skipWhitespace()
            if (matchKeyword("not")) {
                skipWhitespace()
                if (!matchKeyword("null")) {
                    throw FilterParseException("Expected 'null' after 'is not' at position $pos")
                }
                return Expression(ExpressionType.ISNOTNULL, KeyOperand(Key(key)))
            } else if (matchKeyword("null")) {
                return Expression(ExpressionType.ISNULL, KeyOperand(Key(key)))
            } else {
                throw FilterParseException("Expected 'null' or 'not null' after 'is' at position $pos")
            }
        }

        if (matchKeyword("not")) {
            skipWhitespace()
            if (!matchKeyword("in")) {
                throw FilterParseException("Expected 'in' after 'not' at position $pos")
            }
            skipWhitespace()
            val values = parseList()
            return Expression(ExpressionType.NIN, KeyOperand(Key(key)), ValueOperand(Value(values)))
        }

        if (matchKeyword("in")) {
            skipWhitespace()
            val values = parseList()
            return Expression(ExpressionType.IN, KeyOperand(Key(key)), ValueOperand(Value(values)))
        }

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

        return Expression(type, KeyOperand(Key(key)), ValueOperand(Value(value)))
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

    private fun parseValue(): JsonPrimitive {
        skipWhitespace()
        return when {
            peek() == '"' || peek() == '\'' -> JsonPrimitive(parseString())
            peek()?.isDigit() == true || peek() == '-' -> parseNumber()
            matchKeyword("true") -> JsonPrimitive(true)
            matchKeyword("false") -> JsonPrimitive(false)
            matchKeyword("null") -> JsonPrimitive("null")
            else -> throw FilterParseException("Expected value at position $pos")
        }
    }

    private fun parseString(): String {
        val quote = advance()
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != quote) {
            if (input[pos] == '\\' && pos + 1 < input.length) {
                pos++
                sb.append(input[pos])
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        if (pos >= input.length) {
            throw FilterParseException("Unterminated string at position $pos")
        }
        advance()
        return sb.toString()
    }

    private fun parseNumber(): JsonPrimitive {
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
            return JsonPrimitive(input.substring(start, pos).toDouble())
        }
        return JsonPrimitive(input.substring(start, pos).toLong())
    }

    private fun parseList(): JsonArray {
        if (peek() != '[') {
            throw FilterParseException("Expected '[' at position $pos")
        }
        advance()
        skipWhitespace()

        val values = mutableListOf<JsonPrimitive>()
        if (peek() != ']') {
            values.add(parseValue())
            skipWhitespace()
            while (peek() == ',') {
                advance()
                skipWhitespace()
                values.add(parseValue())
                skipWhitespace()
            }
        }

        if (peek() != ']') {
            throw FilterParseException("Expected ']' at position $pos")
        }
        advance()
        return JsonArray(values)
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
            val nextPos = pos + keyword.length
            if (nextPos >= input.length || !input[nextPos].isLetterOrDigit()) {
                pos = nextPos
                return true
            }
        }
        return false
    }
}
