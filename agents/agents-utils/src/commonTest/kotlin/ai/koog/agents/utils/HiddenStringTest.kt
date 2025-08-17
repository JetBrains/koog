package ai.koog.agents.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class HiddenStringTest {

    @Test
    fun testToStringReturnsPlaceholderForNonEmpty() {
        val hiddenString = HiddenString("secret")
        assertEquals(HiddenString.HIDDEN_STRING_PLACEHOLDER, hiddenString.toString())
        assertEquals("secret", hiddenString.value)
    }

    @Test
    fun testToStringReturnsEmptyForEmptyValue() {
        val hiddenString = HiddenString("")
        assertEquals("", hiddenString.toString())
        assertEquals("", hiddenString.value)
    }

    @Test
    fun testCustomPlaceholder() {
        val hiddenString = HiddenString("secret", placeholder = "CUSTOM_PLACEHOLDER")
        assertEquals("CUSTOM_PLACEHOLDER", hiddenString.toString())
        assertEquals("secret", hiddenString.value)
    }
}
