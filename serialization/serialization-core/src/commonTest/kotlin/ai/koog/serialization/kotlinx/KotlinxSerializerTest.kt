package ai.koog.serialization.kotlinx

import ai.koog.serialization.JSONArray
import ai.koog.serialization.JSONLiteral
import ai.koog.serialization.JSONNull
import ai.koog.serialization.JSONObject
import ai.koog.serialization.typeToken
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test

class KotlinxSerializerTest {
    @Serializable
    data class TestPerson(
        val name: String,
        val age: Int,
        val isActive: Boolean,
        val email: String?,
    )

    @Serializable
    data class TestCompany(
        val name: String,
        val employees: List<TestPerson>,
        val founded: Int
    )

    val serializer = KotlinxSerializer(Json)

    @Test
    fun testSerializeDeserialize() {
        val original = TestCompany(
            name = "Tech Corp",
            employees = listOf(
                TestPerson("Alice", 30, true, "alice@techcorp.com"),
                TestPerson("Bob", 25, false, null)
            ),
            founded = 2010
        )
        val serialized = serializer.encodeToString(original, typeToken<TestCompany>())
        //language=JSON
        serialized shouldEqualJson """
            {
              "name": "Tech Corp",
              "employees": [
                {
                  "name": "Alice",
                  "age": 30,
                  "isActive": true,
                  "email": "alice@techcorp.com"
                },
                {
                  "name": "Bob",
                  "age": 25,
                  "isActive": false,
                  "email": null
                }
              ],
              "founded": 2010
            }
        """
        val deserialized = serializer.decodeFromString<TestCompany>(serialized, typeToken<TestCompany>())
        deserialized shouldBe original
    }

    @Test
    fun testSerializeDeserializeJSONElement() {
        val original = TestCompany(
            name = "Innovate Inc",
            employees = listOf(
                TestPerson("Charlie", 35, true, "charlie@innovate.com"),
                TestPerson("Diana", 28, false, null)
            ),
            founded = 2015
        )
        val jsonElement = serializer.encodeToJSONElement(original, typeToken<TestCompany>())
        jsonElement shouldBe JSONObject(
            mapOf(
                "name" to JSONLiteral("Innovate Inc", isString = true),
                "employees" to JSONArray(
                    listOf(
                        JSONObject(
                            mapOf(
                                "name" to JSONLiteral("Charlie", isString = true),
                                "age" to JSONLiteral("35", isString = false),
                                "isActive" to JSONLiteral("true", isString = false),
                                "email" to JSONLiteral("charlie@innovate.com", isString = true)
                            )
                        ),
                        JSONObject(
                            mapOf(
                                "name" to JSONLiteral("Diana", isString = true),
                                "age" to JSONLiteral("28", isString = false),
                                "isActive" to JSONLiteral("false", isString = false),
                                "email" to JSONNull
                            )
                        )
                    )
                ),
                "founded" to JSONLiteral("2015", isString = false)
            )
        )
        val deserialized =
            serializer.decodeFromJSONElement<TestCompany>(jsonElement, typeToken<TestCompany>())
        deserialized shouldBe original
    }

    @Serializable
    data class First<T>(
        val foo: String,
        val value: T
    )

    @Serializable
    data class Second<T>(
        val bar: String,
        val value: T
    )

    @Serializable
    data class Third(
        val baz: String
    )

    @Test
    fun testSerializeDeserializeGenericClassesWithManuallyConstructedTypeTokens() {
        val original = First(
            foo = "foo",
            value = Second(
                bar = "bar",
                value = Third(
                    baz = "baz"
                )
            )
        )

        // Construct type token generic structure manually to see that it will be understood
        val typeToken = typeToken(
            klass = First::class,
            typeArguments = listOf(
                typeToken(
                    klass = Second::class,
                    typeArguments = listOf(
                        typeToken<Third>()
                    )
                )
            )
        )

        val serialized = serializer.encodeToString(original, typeToken)
        // language=JSON
        serialized shouldEqualJson """
        {
            "foo": "foo",
            "value": { 
                "bar": "bar",
                "value": {
                    "baz": "baz"
                }
            }
        }
        """.trimIndent()

        val deserialized = serializer.decodeFromString<First<Second<Third>>>(serialized, typeToken)
        deserialized shouldBe original
    }
}
