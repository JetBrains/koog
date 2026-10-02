package ai.koog.a2a.model

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestsSerializationTest {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testSendMessageRequestWithInlinePushConfigWithoutTaskId() {
        val request = SendMessageRequest(
            message = Message(
                messageId = "message-1",
                role = Role.ROLE_USER,
                parts = listOf(TextPart("Generate the report")),
            ),
            configuration = SendMessageConfiguration(
                taskPushNotificationConfig = TaskPushNotificationConfig(
                    url = "https://client.example.com/webhook",
                    authentication = AuthenticationInfo(scheme = "Bearer", credentials = "token"),
                ),
            ),
        )

        //language=JSON
        val expectedJson = """
            {
                "message": {
                    "messageId": "message-1",
                    "role": "ROLE_USER",
                    "parts": [{"text": "Generate the report"}]
                },
                "configuration": {
                    "taskPushNotificationConfig": {
                        "url": "https://client.example.com/webhook",
                        "authentication": {"scheme": "Bearer", "credentials": "token"}
                    }
                }
            }
        """.trimIndent()

        json.encodeToString(request) shouldEqualJson expectedJson
        val decoded = json.decodeFromString<SendMessageRequest>(expectedJson)
        assertEquals(request, decoded)
        assertNull(decoded.configuration?.taskPushNotificationConfig?.taskId)
    }
}
