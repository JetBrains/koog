package ai.koog.prompt.structure.json.generator

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonClassDiscriminatorSchemaTest {
    @Serializable
    @JsonClassDiscriminator("message_type")
    sealed class AnnotatedMessage {
        @Serializable
        @SerialName("TextMessage")
        data class Text(val text: String) : AnnotatedMessage()

        @Serializable
        @SerialName("EmptyMessage")
        data object Empty : AnnotatedMessage()
    }

    @Serializable
    @JsonClassDiscriminator("event_type")
    abstract class AnnotatedEvent {
        @Serializable
        @SerialName("CreatedEvent")
        data class Created(val id: String) : AnnotatedEvent()
    }

    @Serializable
    sealed class UnannotatedMessage {
        @Serializable
        @SerialName("PlainMessage")
        data class Text(val text: String) : UnannotatedMessage()
    }

    private val json = Json {
        classDiscriminator = "kind"
        serializersModule = SerializersModule {
            polymorphic(AnnotatedEvent::class) {
                subclass(AnnotatedEvent.Created::class, AnnotatedEvent.Created.serializer())
            }
        }
    }

    @Test
    fun testSealedClassAnnotationOverridesConfiguredDiscriminator() {
        assertSchemaDiscriminator(
            json,
            AnnotatedMessage.serializer(),
            AnnotatedMessage.Text("Hello"),
            "message_type",
            "TextMessage"
        )
        assertSchemaDiscriminator(
            json,
            AnnotatedMessage.serializer(),
            AnnotatedMessage.Empty,
            "message_type",
            "EmptyMessage"
        )
    }

    @Test
    fun testOpenClassAnnotationOverridesConfiguredDiscriminator() {
        assertSchemaDiscriminator(
            json,
            AnnotatedEvent.serializer(),
            AnnotatedEvent.Created("1"),
            "event_type",
            "CreatedEvent"
        )
    }

    @Test
    fun testUnannotatedClassUsesConfiguredDiscriminator() {
        assertSchemaDiscriminator(
            json,
            UnannotatedMessage.serializer(),
            UnannotatedMessage.Text("Hello"),
            "kind",
            "PlainMessage"
        )
    }

    @Test
    fun testAnnotationIsRespectedInAllJsonObjectsMode() {
        assertSchemaDiscriminator(
            Json(json) { classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS },
            AnnotatedMessage.serializer(),
            AnnotatedMessage.Text("Hello"),
            "message_type",
            "TextMessage"
        )
    }

    @Test
    fun testDisabledDiscriminatorDoesNotAddAnnotatedOrConfiguredKey() {
        val jsonWithoutDiscriminator = Json(json) { classDiscriminatorMode = ClassDiscriminatorMode.NONE }
        val schema = StandardJsonSchemaGenerator.generate(
            jsonWithoutDiscriminator,
            "AnnotatedMessage",
            AnnotatedMessage.serializer(),
            emptyMap()
        ).schema

        for (definition in schema.getValue(JsonSchemaConsts.Keys.DEFS).jsonObject.values) {
            val properties = definition.jsonObject.getValue(JsonSchemaConsts.Keys.PROPERTIES).jsonObject
            val required = definition.jsonObject.getValue(JsonSchemaConsts.Keys.REQUIRED).jsonArray
            for (key in listOf("message_type", "kind")) {
                assertFalse(key in properties)
                assertFalse(JsonPrimitive(key) in required)
            }
        }
    }

    private fun <T> assertSchemaDiscriminator(
        json: Json,
        serializer: KSerializer<T>,
        value: T,
        discriminator: String,
        serialName: String,
    ) {
        val schema = StandardJsonSchemaGenerator.generate(json, "TestSchema", serializer, emptyMap()).schema
        val definition = schema.getValue(JsonSchemaConsts.Keys.DEFS).jsonObject.getValue(serialName).jsonObject
        val properties = definition.getValue(JsonSchemaConsts.Keys.PROPERTIES).jsonObject
        val required = definition.getValue(JsonSchemaConsts.Keys.REQUIRED).jsonArray
        val encodedValue = json.encodeToJsonElement(serializer, value).jsonObject

        assertEquals(JsonPrimitive(serialName), encodedValue[discriminator])
        assertEquals(
            encodedValue[discriminator],
            properties.getValue(discriminator).jsonObject.getValue(JsonSchemaConsts.Keys.CONST)
        )
        assertTrue(JsonPrimitive(discriminator) in required)
        if (discriminator != json.configuration.classDiscriminator) {
            assertFalse(json.configuration.classDiscriminator in properties)
            assertFalse(JsonPrimitive(json.configuration.classDiscriminator) in required)
        }
    }
}
