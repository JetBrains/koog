package ai.koog.prompt.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CacheControlSerializerJvmTest {

    @Serializable
    private sealed interface JvmSampleCacheControl : CacheControl {
        @Serializable
        data object Default : JvmSampleCacheControl
    }

    @Test
    fun testJvmResolvesCacheControlFromItsSerialName() {
        val serialName = JvmSampleCacheControl.Default.serializer().descriptor.serialName
        val serializer = loadCacheControlSerializer(serialName)

        assertNotNull(serializer, "JVM should resolve a @Serializable CacheControl from its serial name")
        val decoded = Json.decodeFromJsonElement(serializer, JsonObject(emptyMap()))
        assertEquals(JvmSampleCacheControl.Default, decoded)
    }
}
