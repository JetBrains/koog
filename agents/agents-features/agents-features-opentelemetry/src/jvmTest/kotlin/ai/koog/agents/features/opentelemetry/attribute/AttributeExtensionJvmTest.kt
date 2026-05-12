package ai.koog.agents.features.opentelemetry.attribute

import kotlin.test.Test

/**
 * JVM-only attribute-conversion tests that depend on `Float` being a distinct runtime type from `Double`.
 */
class AttributeExtensionJvmTest {

    @Test
    fun `test convert DOUBLE attribute`() =
        testAttributeType(
            key = "doubleKey",
            value = 123.45,
            expectedValue = 123.45,
            verbose = true
        )

    @Test
    fun `test convert FLOAT attribute with verbose true`() =
        testAttributeType(
            key = "floatKey",
            value = 12.34f,
            expectedValue = 12.34f.toDouble(),
            verbose = true
        )

    @Test
    fun `test convert ARRAY OF DOUBLE attribute`() =
        testAttributeType(
            key = "doubleArrayKey",
            value = listOf(123.45, 678.90),
            expectedValue = listOf(123.45, 678.90),
            verbose = true
        )
}
