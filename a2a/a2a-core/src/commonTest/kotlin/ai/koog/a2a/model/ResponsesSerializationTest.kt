package ai.koog.a2a.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsesSerializationTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testEmptyListTaskPushNotificationConfigsResponseDecodes() {
        // ProtoJSON omits empty repeated fields, so an empty list arrives as `{}`.
        assertEquals(
            ListTaskPushNotificationConfigsResponse(),
            json.decodeFromString<ListTaskPushNotificationConfigsResponse>("{}")
        )
    }
}
