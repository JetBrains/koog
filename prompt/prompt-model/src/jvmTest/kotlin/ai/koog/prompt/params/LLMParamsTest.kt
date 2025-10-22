package ai.koog.prompt.params

import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
class LLMParamsTest {

    companion object {
        private const val EMPTY_STRING = ""
        private const val BLANK_STRING = "   "
        private const val TEST_SPECULATION = "test speculation"
        private const val TEST_USER = "test_user"
        private const val VALID_TOOL_NAME = "valid_tool_name"
        private const val VALID_SCHEMA_NAME = "valid_schema_name"
        private const val VALID_SPECULATION_TEXT = "valid speculation text"
        private const val VALID_USER_ID = "valid_user_id"
        private const val ANOTHER_VALID_NAME = "another_valid_name"
        private const val DEFAULT_SPECULATION = "default speculation"
        private const val DEFAULT_USER = "default_user"

        private val STRING_JSON_SCHEMA = buildJsonObject { put("type", "string") }
        private val OBJECT_JSON_SCHEMA = buildJsonObject { put("type", "object") }

        @JvmStatic
        fun invalidTemperature(): Stream<Double> {
            return Stream.of(
                -0.1,
                2.1,
                -10.0,
                100.0
            )
        }

        @JvmStatic
        fun invalidStringParam(): Stream<String> {
            return Stream.of(
                EMPTY_STRING,
                BLANK_STRING
            )
        }
    }

    @Test
    fun testAllNullValues() {
        val params = LLMParams(
            temperature = null,
            numberOfChoices = null,
            speculation = null,
            schema = null,
            toolChoice = null,
            user = null
        )

        assertNull(params.temperature)
        assertNull(params.numberOfChoices)
        assertNull(params.speculation)
        assertNull(params.schema)
        assertNull(params.toolChoice)
        assertNull(params.user)
    }

    @Test
    fun testValidValues() {
        val schema = LLMParams.Schema.JSON.Basic(
            "test-schema",
            STRING_JSON_SCHEMA
        )
        val toolChoice = LLMParams.ToolChoice.Auto
        val temperature = 1.0
        val nrOfChoices = 5

        val params = LLMParams(
            temperature = temperature,
            numberOfChoices = nrOfChoices,
            speculation = TEST_SPECULATION,
            schema = schema,
            toolChoice = toolChoice,
            user = TEST_USER
        )

        assertEquals(temperature, params.temperature)
        assertEquals(nrOfChoices, params.numberOfChoices)
        assertEquals(TEST_SPECULATION, params.speculation)
        assertEquals(schema, params.schema)
        assertEquals(toolChoice, params.toolChoice)
        assertEquals(TEST_USER, params.user)
    }

    @Test
    fun testValidTemperature() {
        val tMin = 0.0
        val tMax = 2.0
        val paramsMinTemp = LLMParams(temperature = tMin)
        val paramsMaxTemp = LLMParams(temperature = tMax)

        assertEquals(tMin, paramsMinTemp.temperature)
        assertEquals(tMax, paramsMaxTemp.temperature)
    }

    @ParameterizedTest
    @MethodSource("invalidTemperature")
    fun testInvalidTemperature(temperature: Double) {
        assertThrows<IllegalArgumentException> {
            LLMParams(temperature = temperature)
        }
    }

    @Test
    fun testValidNumberOfChoices() {
        val params1 = LLMParams(numberOfChoices = 1)
        val params10 = LLMParams(numberOfChoices = 10)

        assertEquals(1, params1.numberOfChoices)
        assertEquals(10, params10.numberOfChoices)
    }

    @Test
    fun testInvalidNumberOfChoices() {
        assertThrows<IllegalArgumentException> {
            LLMParams(numberOfChoices = 0)
        }
    }

    @Test
    fun testValidSpeculation() {
        val params = LLMParams(speculation = VALID_SPECULATION_TEXT)
        assertEquals(VALID_SPECULATION_TEXT, params.speculation)
    }

    @ParameterizedTest
    @MethodSource("invalidStringParam")
    fun testInvalidSpeculation(speculation: String) {
        assertThrows<IllegalArgumentException> {
            LLMParams(speculation = speculation)
        }
    }

    @Test
    fun testValidUser() {
        val params = LLMParams(user = VALID_USER_ID)
        assertEquals(VALID_USER_ID, params.user)
    }

    @ParameterizedTest
    @MethodSource("invalidStringParam")
    fun testInvalidUser(user: String) {
        assertThrows<IllegalArgumentException> {
            LLMParams(user = user)
        }
    }

    @Test
    fun testValidToolChoiceNamed() {
        val params = LLMParams(toolChoice = LLMParams.ToolChoice.Named(VALID_TOOL_NAME))
        assertEquals(VALID_TOOL_NAME, (params.toolChoice as LLMParams.ToolChoice.Named).name)
    }

    @ParameterizedTest
    @MethodSource("invalidStringParam")
    fun testInvalidToolChoiceNamed(name: String) {
        assertThrows<IllegalArgumentException> {
            LLMParams(toolChoice = LLMParams.ToolChoice.Named(name))
        }
    }

    @Test
    fun testValidSchemaNames() {
        val simpleSchema = LLMParams.Schema.JSON.Basic(
            VALID_SCHEMA_NAME,
            STRING_JSON_SCHEMA
        )
        val fullSchema = LLMParams.Schema.JSON.Standard(
            ANOTHER_VALID_NAME,
            OBJECT_JSON_SCHEMA
        )

        val paramsBasic = LLMParams(schema = simpleSchema)
        val paramsFull = LLMParams(schema = fullSchema)

        assertEquals(VALID_SCHEMA_NAME, paramsBasic.schema?.name)
        assertEquals(ANOTHER_VALID_NAME, paramsFull.schema?.name)
    }

    @ParameterizedTest
    @MethodSource("invalidStringParam")
    fun testInvalidBasicSchemaName(name: String) {
        assertThrows<IllegalArgumentException> {
            LLMParams.Schema.JSON.Basic(
                name,
                STRING_JSON_SCHEMA
            )
        }
    }

    @ParameterizedTest
    @MethodSource("invalidStringParam")
    fun testInvalidFullSchemaName(name: String) {
        assertThrows<IllegalArgumentException> {
            LLMParams.Schema.JSON.Standard(
                name,
                OBJECT_JSON_SCHEMA
            )
        }
    }

    @Test
    fun testMultipleValidationErrors() {
        assertThrows<IllegalArgumentException> {
            LLMParams(
                temperature = -1.0,
                numberOfChoices = 0,
                speculation = EMPTY_STRING,
                user = BLANK_STRING
            )
        }
    }

    @Test
    fun testDefaultFunction() {
        val temperature = 1.0
        val nrOfChoices = 3

        val defaultParams = LLMParams(
            temperature = 0.7,
            numberOfChoices = nrOfChoices,
            speculation = DEFAULT_SPECULATION,
            user = DEFAULT_USER
        )

        val params = LLMParams(
            temperature = temperature,
            numberOfChoices = null,
            speculation = null,
            user = TEST_USER
        )

        val result = params.default(defaultParams)

        assertEquals(temperature, result.temperature)
        assertEquals(nrOfChoices, result.numberOfChoices)
        assertEquals(DEFAULT_SPECULATION, result.speculation)
        assertEquals(TEST_USER, result.user)
    }

    @Test
    fun testCopyWithNoChanges() {
        val originalSchema = LLMParams.Schema.JSON.Basic(
            VALID_SCHEMA_NAME,
            STRING_JSON_SCHEMA
        )
        val originalToolChoice = LLMParams.ToolChoice.Auto

        val original = LLMParams(
            temperature = 1.5,
            maxTokens = 100,
            numberOfChoices = 3,
            speculation = TEST_SPECULATION,
            schema = originalSchema,
            toolChoice = originalToolChoice,
            user = TEST_USER,
            additionalProperties = mapOf("foo" to JsonPrimitive("bar"))
        )

        val copied = original.copy()

        copied shouldBeEqualToComparingFields original
    }
}
