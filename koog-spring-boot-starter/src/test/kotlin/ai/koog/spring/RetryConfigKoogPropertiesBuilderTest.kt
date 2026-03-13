package ai.koog.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RetryConfigKoogPropertiesBuilderTest {

    @Test
    fun testBuilderDefaults() {
        val props = RetryConfigKoogProperties.builder().build()

        assertFalse(props.enabled)
        assertNull(props.maxAttempts)
        assertNull(props.initialDelay)
        assertNull(props.maxDelay)
        assertNull(props.backoffMultiplier)
        assertNull(props.jitterFactor)
    }

    @Test
    fun testBuilderWithAllFields() {
        val props = RetryConfigKoogProperties.builder()
            .enabled(true)
            .maxAttempts(5)
            .initialDelay(Duration.ofSeconds(2))
            .maxDelay(Duration.ofSeconds(30))
            .backoffMultiplier(2.0)
            .jitterFactor(0.1)
            .build()

        assertTrue(props.enabled)
        assertEquals(5, props.maxAttempts)
        assertEquals(Duration.ofSeconds(2), props.initialDelay)
        assertEquals(Duration.ofSeconds(30), props.maxDelay)
        assertEquals(2.0, props.backoffMultiplier)
        assertEquals(0.1, props.jitterFactor)
    }

    @Test
    fun testBuilderWithPartialFields() {
        val props = RetryConfigKoogProperties.builder()
            .enabled(true)
            .maxAttempts(3)
            .build()

        assertTrue(props.enabled)
        assertEquals(3, props.maxAttempts)
        assertNull(props.initialDelay)
        assertNull(props.maxDelay)
        assertNull(props.backoffMultiplier)
        assertNull(props.jitterFactor)
    }

    @Test
    fun testBuilderChainingReturnsSameInstance() {
        val builder = RetryConfigKoogProperties.builder()
        val returned = builder.enabled(true)

        assertTrue(builder === returned)
    }
}
