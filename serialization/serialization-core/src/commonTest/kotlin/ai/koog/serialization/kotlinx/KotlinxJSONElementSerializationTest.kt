package ai.koog.serialization.kotlinx

import ai.koog.serialization.JSONElementSerializationTestBase
import kotlinx.serialization.json.Json
import kotlin.test.Test

class KotlinxJSONElementSerializationTest : JSONElementSerializationTestBase() {
    override val serializer = KotlinxSerializer(Json)

    @Test
    override fun testJSONNull() {
        super.testJSONNull()
    }

    @Test
    override fun testJSONLiteralString() {
        super.testJSONLiteralString()
    }

    @Test
    override fun testJSONLiteralNumber() {
        super.testJSONLiteralNumber()
    }

    @Test
    override fun testJSONLiteralBoolean() {
        super.testJSONLiteralBoolean()
    }

    @Test
    override fun testJSONPrimitiveString() {
        super.testJSONPrimitiveString()
    }

    @Test
    override fun testJSONPrimitiveNull() {
        super.testJSONPrimitiveNull()
    }

    @Test
    override fun testJSONArrayEmpty() {
        super.testJSONArrayEmpty()
    }

    @Test
    override fun testJSONArrayWithPrimitives() {
        super.testJSONArrayWithPrimitives()
    }

    @Test
    override fun testJSONObjectEmpty() {
        super.testJSONObjectEmpty()
    }

    @Test
    override fun testJSONObjectWithPrimitives() {
        super.testJSONObjectWithPrimitives()
    }

    @Test
    override fun testJSONElementNestedStructure() {
        super.testJSONElementNestedStructure()
    }

    @Test
    override fun testJSONElementArray() {
        super.testJSONElementArray()
    }

    @Test
    override fun testJSONUnquotedPrimitive() {
        super.testJSONUnquotedPrimitive()
    }

    @Test
    override fun testRoundTripSerialization() {
        super.testRoundTripSerialization()
    }
}
