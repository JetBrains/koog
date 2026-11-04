package ai.koog.a2a.transport.jsonrpc.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SnakeCaseKeyNormalizerTest {
    private fun json(raw: String): JsonElement = JSONRPCJson.parseToJsonElement(raw)

    private fun assertNormalized(expected: String, input: String) {
        assertEquals(json(expected), SnakeCaseKeyNormalizer.normalize(json(input)))
    }

    @Test
    fun testSnakeCaseKeysAreConvertedInNestedObjectsAndArrays() {
        //language=JSON
        assertNormalized(
            expected = """
                {
                  "contextId": "ctx-1",
                  "status": {"state": "TASK_STATE_COMPLETED", "message": {"messageId": "m-1", "taskId": "t-1"}},
                  "history": [{"messageId": "m-2", "referenceTaskIds": ["t_0"]}, "plain_value", 1],
                  "camelCase": true,
                  "Trailing": null
                }
            """,
            input = """
                {
                  "context_id": "ctx-1",
                  "status": {"state": "TASK_STATE_COMPLETED", "message": {"message_id": "m-1", "taskId": "t-1"}},
                  "history": [{"message_id": "m-2", "reference_task_ids": ["t_0"]}, "plain_value", 1],
                  "camelCase": true,
                  "_trailing_": null
                }
            """,
        )
    }

    @Test
    fun testOpaqueValuesAreKeptUntouched() {
        //language=JSON
        assertNormalized(
            expected = """
                {
                  "parts": [{"data": {"user_key": {"nested_key": 1}}, "mediaType": "application/json", "metadata": {"part_meta": 1}}],
                  "metadata": {"some_key": [{"inner_key": 1}]},
                  "capabilities": {"extensions": [{"uri": "urn:x", "params": {"ext_param": 1}}]},
                  "signatures": [{"protected": "p", "header": {"x_kid": "k"}}],
                  "securityRequirements": [{"schemes": {"my_scheme": {"list": ["read_all"]}}}]
                }
            """,
            input = """
                {
                  "parts": [{"data": {"user_key": {"nested_key": 1}}, "media_type": "application/json", "metadata": {"part_meta": 1}}],
                  "metadata": {"some_key": [{"inner_key": 1}]},
                  "capabilities": {"extensions": [{"uri": "urn:x", "params": {"ext_param": 1}}]},
                  "signatures": [{"protected": "p", "header": {"x_kid": "k"}}],
                  "security_requirements": [{"schemes": {"my_scheme": {"list": ["read_all"]}}}]
                }
            """,
        )
    }

    @Test
    fun testSecuritySchemeNamesAreKeptAndSchemesAreNormalized() {
        //language=JSON
        assertNormalized(
            expected = """
                {
                  "securitySchemes": {
                    "my_oauth": {
                      "oauth2SecurityScheme": {
                        "flows": {"clientCredentials": {"tokenUrl": "https://t", "scopes": {"read_all": "Read all"}}},
                        "oauth2MetadataUrl": "https://m"
                      }
                    }
                  }
                }
            """,
            input = """
                {
                  "security_schemes": {
                    "my_oauth": {
                      "oauth2_security_scheme": {
                        "flows": {"client_credentials": {"token_url": "https://t", "scopes": {"read_all": "Read all"}}},
                        "oauth2_metadata_url": "https://m"
                      }
                    }
                  }
                }
            """,
        )
    }

    @Test
    fun testDuplicateSpellingsAreRejected() {
        assertFailsWith<SerializationException> {
            SnakeCaseKeyNormalizer.normalize(json("""{"task": {"historyLength": 1, "history_length": 2}}"""))
        }
    }
}
