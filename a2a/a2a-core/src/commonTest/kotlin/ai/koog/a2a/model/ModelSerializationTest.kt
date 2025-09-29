package ai.koog.a2a.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSerializationTest {
    @Suppress("PrivatePropertyName")
    private val TestJson = Json

    @Test
    fun testRequestIdStringId() {
        val requestId = RequestId.StringId("test-id")
        val requestIdJson = """"test-id""""

        val serialized = TestJson.encodeToString<RequestId>(requestId)
        assertEquals(requestIdJson, serialized)

        val deserialized = TestJson.decodeFromString<RequestId>(requestIdJson)
        assertEquals(requestId, deserialized)
    }

    @Test
    fun testRequestIdNumberId() {
        val requestId = RequestId.NumberId(123L)
        val requestIdJson = """123"""

        val serialized = TestJson.encodeToString<RequestId>(requestId)
        assertEquals(requestIdJson, serialized)

        val deserialized = TestJson.decodeFromString<RequestId>(requestIdJson)
        assertEquals(requestId, deserialized)
    }
}
