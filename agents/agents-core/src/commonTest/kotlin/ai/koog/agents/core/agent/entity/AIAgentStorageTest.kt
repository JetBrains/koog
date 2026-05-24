package ai.koog.agents.core.agent.entity

import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test

class AIAgentStorageTest {
    @Serializable
    private data class TestPerson(val name: String, val age: Int)

    @Serializable
    private data class TestAddress(val city: String, val country: String)

    private class Unserializable(val value: String)

    @Test
    fun testPutAllSerializedThenGetTwice() = runTest {
        val serializer = KotlinxSerializer()
        val storage = AIAgentStorage(serializer)

        val personElement = serializer.encodeToJSONElement(TestPerson("Alice", 30), typeToken<TestPerson>())
        storage.putAllSerialized(mapOf("person" to personElement))

        val key = createStorageKey<TestPerson>("person")

        // First get — deserializes from serializedStorageMap and caches in storageMap
        storage.get(key) shouldBe TestPerson("Alice", 30)

        // Second get — served from storageMap
        storage.get(key) shouldBe TestPerson("Alice", 30)
    }

    @Test
    fun testSetThenToSerializedMap() = runTest {
        val serializer = KotlinxSerializer()
        val storage = AIAgentStorage(serializer)

        val key = createStorageKey<TestPerson>("person")
        storage.set(key, TestPerson("Bob", 25))

        val serialized = storage.toSerializedMap()
        serialized shouldContainKey "person"

        serializer.encodeJSONElementToString(serialized["person"]!!) shouldEqualJson """{"name":"Bob","age":25}"""
    }

    @Test
    fun testMixedStatePreservesAllKeys() = runTest {
        val serializer = KotlinxSerializer()
        val storage = AIAgentStorage(serializer)

        val personElement = serializer.encodeToJSONElement(TestPerson("Alice", 30), typeToken<TestPerson>())
        val addressElement = serializer.encodeToJSONElement(TestAddress("London", "UK"), typeToken<TestAddress>())
        storage.putAllSerialized(mapOf("person" to personElement, "address" to addressElement))

        val personKey = createStorageKey<TestPerson>("person")
        storage.get(personKey) // moves "person" to storageMap, "address" stays in serializedStorageMap

        val scoreKey = createStorageKey<Int>("score")
        storage.set(scoreKey, 42)

        val result = storage.toSerializedMap()
        result shouldContainKey "person" // re-serialized from storageMap
        result shouldContainKey "address" // passed through from serializedStorageMap
        result shouldContainKey "score" // newly added

        serializer.encodeJSONElementToString(result["score"]!!) shouldEqualJson "42"
    }

    @Test
    fun testGetWithWrongTypeThrows() = runTest {
        val serializer = KotlinxSerializer()
        val storage = AIAgentStorage(serializer)

        val personElement = serializer.encodeToJSONElement(TestPerson("Alice", 30), typeToken<TestPerson>())
        storage.putAllSerialized(mapOf("person" to personElement))

        val wrongKey = createStorageKey<Int>("person")
        shouldThrow<Exception> { storage.get(wrongKey) }
    }

    @Test
    fun testToSerializedMapSkipsUnserializableKeys() = runTest {
        val serializer = KotlinxSerializer()
        val storage = AIAgentStorage(serializer)

        val goodKey = createStorageKey<TestPerson>("good")
        storage.set(goodKey, TestPerson("Carol", 20))

        val badKey = createStorageKey<Unserializable>("bad")
        storage.set(badKey, Unserializable("x"))

        val result = storage.toSerializedMap()
        result shouldContainKey "good"
        result shouldNotContainKey "bad"
    }
}
