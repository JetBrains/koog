package ai.koog.agents.features.opentelemetry.attribute

import kotlin.test.Test
import kotlin.test.assertEquals

class KoogAttributesTest {
    @Test
    fun `test status attribute`() {
        val attribute = KoogAttributes.Koog.Tool.Call.Status(KoogAttributes.Koog.Tool.Call.StatusType.SUCCESS)
        assertEquals("koog.tool.call.status", attribute.key)
        assertEquals("success", attribute.value)
    }
}
