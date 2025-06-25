package ai.koog.agents.test

import ai.koog.agents.core.agent.AIAgentException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AIAgentExceptionTest {
    fun formErrorMessage(problem: String) = "AI Agent has run into a problem: $problem"

    @Test
    fun `test AIAgentException with problem only`() {
        val problems = listOf(
            "Error in processing request",
            "Invalid input data",
            "Network connection failed",
            "Timeout occurred",
            ""
        )
        for (problem in problems) {
            val exception = AIAgentException(problem)
            assertEquals(formErrorMessage(problem), exception.message)
            assertEquals(null, exception.cause)
        }
    }

    @Test
    fun `test AIAgentException with problem and throwable`() {
        val problem = "Error in processing request"
        val cause = RuntimeException("Test error cause")
        val exception = AIAgentException(problem, cause)
        assertEquals(formErrorMessage(problem), exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun `test AIAgentException with cause chain`() {
        val rootCause = IllegalArgumentException("Root cause")
        val intermediateCause = IllegalStateException("Intermediate cause", rootCause)
        val problem = "Top level problem"
        val exception = AIAgentException(problem, intermediateCause)
        assertEquals(formErrorMessage(problem), exception.message)
        assertSame(intermediateCause, exception.cause)
        assertSame(rootCause, exception.cause?.cause)
    }
}