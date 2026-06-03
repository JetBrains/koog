package ai.koog.a2a.serialization

import ai.koog.a2a.model.APIKeySecurityScheme
import ai.koog.a2a.model.In
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.SecurityScheme
import ai.koog.a2a.model.TextPart
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializersTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun testPropertyPresencePolymorphicSerializer() {
        // PartSerializer is a real PropertyPresencePolymorphicSerializer; the "text" property selects the TextPart variant.
        val part: Part = TextPart(text = "Hello")

        //language=JSON
        val expectedJson = """
            {
                "text": "Hello"
            }
        """.trimIndent()

        json.encodeToString<Part>(part) shouldEqualJson expectedJson
        assertEquals(part, json.decodeFromString<Part>(expectedJson))
    }

    @Test
    fun testPropertyWrappingPolymorphicSerializer() {
        // SecuritySchemeSerializer is a real PropertyWrappingPolymorphicSerializer; the variant is wrapped in a single-property object.
        val scheme: SecurityScheme = APIKeySecurityScheme(`in` = In.Header, name = "Authorization")

        //language=JSON
        val expectedJson = """
            {
                "apiKeySecurityScheme": {
                    "in": "header",
                    "name": "Authorization"
                }
            }
        """.trimIndent()

        json.encodeToString<SecurityScheme>(scheme) shouldEqualJson expectedJson
        assertEquals(scheme, json.decodeFromString<SecurityScheme>(expectedJson))
    }
}
