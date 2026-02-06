package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.descriptors.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptionKindTest {

    @Test
    fun testFlowAgentInputDescriptorIsClass() {
        val descriptor = FlowAgentInput.serializer().descriptor
        assertEquals(StructureKind.CLASS, descriptor.kind, "FlowAgentInput descriptor should be StructureKind.CLASS")
        println("✓ FlowAgentInput descriptor kind: ${descriptor.kind}")
        println("✓ Descriptor name: ${descriptor.serialName}")
        println("✓ Elements:")
        for (i in 0 until descriptor.elementsCount) {
            println("  - ${descriptor.getElementName(i)} (optional: ${descriptor.isElementOptional(i)})")
        }
    }

}
