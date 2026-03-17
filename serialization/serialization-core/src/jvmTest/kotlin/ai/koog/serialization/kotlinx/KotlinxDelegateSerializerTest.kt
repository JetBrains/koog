package ai.koog.serialization.kotlinx

import ai.koog.serialization.annotations.InternalKoogSerializationApi
import ai.koog.serialization.jackson.JacksonSerializer
import ai.koog.serialization.typeToken
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

@OptIn(InternalKoogSerializationApi::class)
class KotlinxDelegateSerializerTest {
    val jacksonSerializer = JacksonSerializer(jacksonObjectMapper())
    val json = Json.Default

    // Jackson serialized
    data class UserData(
        val foo: String,
        val bar: Int
    )

    // Kotlinx serialized
    @Serializable
    data class Container<T>(
        val id: String,
        val value: T
    )

    @Test
    fun testSerializationWithDelegation() {
        val container = Container(
            id = "1",
            UserData(
                foo = "foo",
                bar = 1
            )
        )
        val containerSerialized = buildJsonObject {
            put("id", "1")
            putJsonObject("value") {
                put("foo", "foo")
                put("bar", 1)
            }
        }

        val userDataType = typeToken<UserData>()
        // Serializes Container with kotlinx, delegates UserData serialization to Jackson
        val containerSerializer: KSerializer<Container<UserData>> =
            Container.serializer(KotlinxDelegateSerializer(jacksonSerializer, userDataType))

        json.encodeToJsonElement(containerSerializer, container) shouldBe containerSerialized
        json.decodeFromJsonElement(containerSerializer, containerSerialized) shouldBe container
    }
}
