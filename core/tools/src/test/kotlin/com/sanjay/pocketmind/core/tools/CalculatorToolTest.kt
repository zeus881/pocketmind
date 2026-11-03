package com.sanjay.pocketmind.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorToolTest {
    private fun calc(expression: String) = Expression.format(Expression.evaluate(expression))

    @Test
    fun `decimal arithmetic is exact`() {
        assertEquals("6910.4", calc("123.4 * 56"))
        assertEquals("6910.4", calc("123.4 × 56"))
        assertEquals("0.3", calc("0.1 + 0.2"))
        assertEquals("51", calc("25 + 26"))
        assertEquals("1000000", calc("1,000 * 1,000"))
    }

    @Test
    fun `precedence, parentheses, powers and signs`() {
        assertEquals("14", calc("2 + 3 * 4"))
        assertEquals("20", calc("(2 + 3) * 4"))
        assertEquals("512", calc("2 ^ 3 ^ 2")) // right-associative
        assertEquals("0.125", calc("2^-3"))
        assertEquals("-9", calc("-3^2 * -1 * -1"))
        assertEquals("1500", calc("1.5e3"))
    }

    @Test
    fun `division, percent and functions`() {
        assertEquals("0.333333333333333", calc("1/3"))
        assertEquals("7.5", calc("50 * 15%"))
        assertEquals("12", calc("sqrt(144)"))
        assertEquals("3", calc("round(2.5)"))
        assertEquals("2", calc("log(100)"))
        assertEquals("3.14159265358979", calc("pi"))
        assertEquals("0", calc("sin(0)"))
    }

    @Test
    fun `bad input becomes a readable error for the model`() {
        val tool = CalculatorTool()
        fun error(json: String) =
            Json.parseToJsonElement(ToolRunner.run(tool, json)).jsonObject.getValue("error").jsonPrimitive.content

        assertEquals("division by zero", error("""{"expression": "1/0"}"""))
        assertEquals("unknown name 'foo'", error("""{"expression": "foo(2)"}"""))
        assertTrue(error("""{"expression": "2 +"}""").startsWith("expected a number"))
        assertEquals("'expression' is missing", error("{}"))
        assertTrue(error("not json").startsWith("arguments are not valid JSON"))
        assertEquals("square root of a negative number", error("""{"expression": "sqrt(-1)"}"""))
    }

    @Test
    fun `the result names the expression it answers`() {
        val result = ToolRunner.run(CalculatorTool(), """{"expression": "123.4 * 56"}""")

        assertEquals("""{"expression":"123.4 * 56","result":"6910.4"}""", result)
    }
}
