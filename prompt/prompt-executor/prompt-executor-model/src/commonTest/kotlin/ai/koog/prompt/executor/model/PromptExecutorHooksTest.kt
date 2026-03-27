package ai.koog.prompt.executor.model

import ai.koog.prompt.dsl.Prompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PromptExecutorHooksTest {

    private val outerPrompt = Prompt.build("outer") { user("outer") }
    private val nestedPrompt = Prompt.build("nested") { user("nested") }

    @Test
    fun testCombineWithNoOverridesAndNoOverridesReturnsNoOverrides() {
        val result = ExecutionArgOverrides.NoOverrides.combineWith(ExecutionArgOverrides.NoOverrides)
        assertSame(ExecutionArgOverrides.NoOverrides, result)
    }

    @Test
    fun testCombineWithUseDifferentPromptAndNoOverridesKeepsOuter() {
        val outer = ExecutionArgOverrides.UseDifferentPrompt(outerPrompt)
        val result = outer.combineWith(ExecutionArgOverrides.NoOverrides)
        val override = assertIs<ExecutionArgOverrides.UseDifferentPrompt>(result)
        assertEquals(outerPrompt, override.prompt)
    }

    @Test
    fun testCombineWithNoOverridesAndUseDifferentPromptReturnsNested() {
        val nested = ExecutionArgOverrides.UseDifferentPrompt(nestedPrompt)
        val result = ExecutionArgOverrides.NoOverrides.combineWith(nested)
        val override = assertIs<ExecutionArgOverrides.UseDifferentPrompt>(result)
        assertEquals(nestedPrompt, override.prompt)
    }

    @Test
    fun testCombineWithNestedPromptOverrideTakesPrecedenceOverOuter() {
        val outer = ExecutionArgOverrides.UseDifferentPrompt(outerPrompt)
        val nested = ExecutionArgOverrides.UseDifferentPrompt(nestedPrompt)
        val result = outer.combineWith(nested)
        val override = assertIs<ExecutionArgOverrides.UseDifferentPrompt>(result)
        assertEquals(nestedPrompt, override.prompt)
    }
}
