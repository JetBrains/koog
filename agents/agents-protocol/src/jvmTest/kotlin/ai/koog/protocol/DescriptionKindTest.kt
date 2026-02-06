package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptionKindTest {

    @Test
    fun testFlowAgentInputDescriptorIsClass() {
        val descriptor = FlowAgentInput.serializer().descriptor
        // FlowAgentInput uses PrimitiveKind.STRING to hide internal structure from tool schemas
        // This allows LLMs to see it as a simple string value rather than a complex object
        assertEquals(PrimitiveKind.STRING, descriptor.kind, "FlowAgentInput descriptor should be PrimitiveKind.STRING")
        println("✓ FlowAgentInput descriptor kind: ${descriptor.kind}")
        println("✓ Descriptor name: ${descriptor.serialName}")
    }

}
