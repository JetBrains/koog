package ai.koog.agents.core.agent.entity

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIAgentStorageSerializationTest {

    @Serializable
    private data class Config(val host: String, val port: Int)

    // Serializable keys
    private val intKey = createSerializableStorageKey<Int>("count")
    private val stringKey = createSerializableStorageKey<String>("label")
    private val configKey = createSerializableStorageKey<Config>("config")

    // Non-serializable key — same value type as stringKey to make the contrast clear
    private val plainKey = createStorageKey<String>("plain")

    // region serializeToJson

    @Test
    fun testSerializeToJsonEmptyStorage() = runTest {
        val storage = AIAgentStorage()
        assertTrue(storage.serializeToJson().isEmpty())
    }

    @Test
    fun testSerializeToJsonNoSerializableKeys() = runTest {
        val storage = AIAgentStorage()
        storage.set(plainKey, "ignored")

        assertTrue(storage.serializeToJson().isEmpty())
    }

    @Test
    fun testSerializeToJsonPrimitiveTypes() = runTest {
        val storage = AIAgentStorage()
        storage.set(intKey, 42)
        storage.set(stringKey, "hello")

        val json = storage.serializeToJson()

        assertEquals(2, json.size)
        assertEquals(JsonPrimitive(42), json["count"])
        assertEquals(JsonPrimitive("hello"), json["label"])
    }

    @Test
    fun testSerializeToJsonComplexType() = runTest {
        val storage = AIAgentStorage()
        val config = Config("localhost", 8080)
        storage.set(configKey, config)

        val json = storage.serializeToJson()

        val decoded = Json.decodeFromJsonElement(Config.serializer(), json["config"]!!)
        assertEquals(config, decoded)
    }

    @Test
    fun testSerializeToJsonSkipsNonSerializableKeys() = runTest {
        val storage = AIAgentStorage()
        storage.set(intKey, 7)
        storage.set(plainKey, "skip me")

        val json = storage.serializeToJson()

        assertEquals(1, json.size)
        assertTrue(json.containsKey("count"))
        assertFalse(json.containsKey("plain"))
    }

    // endregion

    // region restoreFromJson

    @Test
    fun testRestoreFromJsonRestoresEntries() = runTest {
        val storage = AIAgentStorage()
        val jsonObject = buildJsonObject {
            put("count", 99)
            put("label", "restored")
        }

        storage.restoreFromJson(jsonObject, listOf(intKey, stringKey))

        assertEquals(99, storage.get(intKey))
        assertEquals("restored", storage.get(stringKey))
    }

    @Test
    fun testRestoreFromJsonIgnoresUnknownJsonProperties() = runTest {
        val storage = AIAgentStorage()
        val jsonObject = buildJsonObject {
            put("count", 5)
            put("unknown-field", "ignored")
        }

        storage.restoreFromJson(jsonObject, listOf(intKey))

        assertEquals(5, storage.get(intKey))
    }

    @Test
    fun testRestoreFromJsonIgnoresJsonPropertiesNotInKeySet() = runTest {
        val storage = AIAgentStorage()
        val jsonObject = buildJsonObject { put("count", 5) }

        // "count" is present in JSON but intKey is not in the provided set
        storage.restoreFromJson(jsonObject, listOf(stringKey))

        assertNull(storage.get(intKey))
    }

    @Test
    fun testRestoreFromJsonPreservesUnrelatedStorageEntries() = runTest {
        val storage = AIAgentStorage()
        storage.set(plainKey, "untouched")

        storage.restoreFromJson(buildJsonObject { put("count", 1) }, listOf(intKey))

        assertEquals("untouched", storage.get(plainKey))
    }

    @Test
    fun testRestoreFromJsonOverwritesExistingValue() = runTest {
        val storage = AIAgentStorage()
        storage.set(intKey, 1)

        storage.restoreFromJson(buildJsonObject { put("count", 99) }, listOf(intKey))

        assertEquals(99, storage.get(intKey))
    }

    @Test
    fun testRestoreFromJsonWithEmptyJsonChangesNothing() = runTest {
        val storage = AIAgentStorage()
        storage.set(intKey, 42)

        storage.restoreFromJson(buildJsonObject { }, listOf(intKey))

        assertEquals(42, storage.get(intKey))
    }

    // endregion

    // region round-trip

    @Test
    fun testRoundTrip() = runTest {
        val original = AIAgentStorage()
        original.set(intKey, 123)
        original.set(stringKey, "world")
        original.set(configKey, Config("example.com", 443))

        val json = original.serializeToJson()

        val restored = AIAgentStorage()
        restored.restoreFromJson(json, listOf(intKey, stringKey, configKey))

        assertEquals(123, restored.get(intKey))
        assertEquals("world", restored.get(stringKey))
        assertEquals(Config("example.com", 443), restored.get(configKey))
    }

    @Test
    fun testRoundTripNonSerializableKeyNotRestored() = runTest {
        val storage = AIAgentStorage()
        storage.set(intKey, 10)
        storage.set(plainKey, "ephemeral")

        val json = storage.serializeToJson()

        val restored = AIAgentStorage()
        restored.restoreFromJson(json, listOf(intKey, stringKey, configKey))

        assertEquals(10, restored.get(intKey))
        assertNull(restored.get(plainKey))
    }

    // endregion
}
