package ai.koog.prompt.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CacheControlSerializationTest {

    @Serializable
    private sealed interface SampleCacheControl : CacheControl {
        @Serializable
        data object Default : SampleCacheControl

        @Serializable
        data class Ttl(val seconds: Int) : SampleCacheControl
    }

    @Serializable
    private sealed interface OtherCacheControl : CacheControl {
        @Serializable
        data object Default : OtherCacheControl
    }

    @Test
    fun testMessageWithCacheControlRoundTrips() {
        val message = Message.System(
            "You are a helpful assistant.",
            RequestMetaInfo.Empty,
            SampleCacheControl.Ttl(3600),
        )
        val encoded = Json.encodeToString(message)
        val decoded = Json.decodeFromString<Message.System>(encoded)

        assertEquals(message, decoded)
    }

    @Test
    fun testDifferentCacheControlsEncodeDifferently() {
        val defaultDirective = Message.System("hello", RequestMetaInfo.Empty, SampleCacheControl.Default)
        val otherProvider = Message.System("hello", RequestMetaInfo.Empty, OtherCacheControl.Default)
        val withTtl = Message.System("hello", RequestMetaInfo.Empty, SampleCacheControl.Ttl(60))

        assertNotEquals(Json.encodeToString(defaultDirective), Json.encodeToString(otherProvider))
        assertNotEquals(Json.encodeToString(defaultDirective), Json.encodeToString(withTtl))
    }
}
