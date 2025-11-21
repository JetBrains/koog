package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.typeOf
import kotlin.test.Test

class SerializationUtilsTestJvm {

    @Test
    fun `encodeDataToStringOrNull should return null when serialization fails`() {
        @Suppress("unused")
        val data = object {
            val name = "test-name"
        }

        val mockJson: Json = mockk()
        val mockSerializerModule: SerializersModule = mockk(relaxed = true)

        every { mockJson.encodeToString(any<KSerializer<Any>>(), any()) } throws IllegalArgumentException("Expected")
        every { mockJson.serializersModule } returns mockSerializerModule

        val actualString =
            @OptIn(InternalAgentsApi::class)
            SerializationUtils.encodeDataToStringOrNull(data, typeOf<Any>(), json = mockJson)

        actualString shouldBe null
    }
}
