package ai.koog.agents.core.agent.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentResourceConfigTest {

    @Test
    fun testToolResourceDelegates() {
        val testCategories = object : ToolCategories() {
            override val unlimited by ToolResourceDelegate.unlimited()
            val customLimited by ToolResourceDelegate.limited(10, Dispatchers.IO)
            val customShared by ToolResourceDelegate.shared(Semaphore(5), Dispatchers.Default)
        }
        
        // Test unlimited delegate
        assertNull(testCategories.unlimited.semaphore)
        
        // Test limited delegate
        assertNotNull(testCategories.customLimited.semaphore)
        assertEquals(10, testCategories.customLimited.semaphore?.availablePermits)
        assertEquals(Dispatchers.IO, testCategories.customLimited.dispatcher)
        
        // Test shared delegate
        assertNotNull(testCategories.customShared.semaphore)
        assertEquals(5, testCategories.customShared.semaphore?.availablePermits)
        assertEquals(Dispatchers.Default, testCategories.customShared.dispatcher)
    }

    @Test
    fun testLimitedDelegateValidation() {
        // Valid positive limit - test through ToolCategories
        val validCategories = object : ToolCategories() {
            val valid by ToolResourceDelegate.limited(1)
        }
        assertNotNull(validCategories.valid)
        
        // Invalid zero limit
        assertFailsWith<IllegalArgumentException> {
            ToolResourceDelegate.limited(0)
        }
        
        // Invalid negative limit
        assertFailsWith<IllegalArgumentException> {
            ToolResourceDelegate.limited(-1)
        }
    }

    @Test
    fun testCustomToolClassifier() {
        val categories = ToolCategories()
        val config = AgentResourceConfig(
            toolCategories = categories,
            toolClassifier = { toolName ->
                when {
                    toolName.startsWith("test_") -> categories.cpu
                    else -> categories.default
                }
            }
        )
        
        val testToolHandle = config.toolClassifier("test_tool")
        val defaultToolHandle = config.toolClassifier("other_tool")
        
        // Should return different handles based on classification
        assertNotNull(testToolHandle)
        assertNotNull(defaultToolHandle)
        // They should be different handles
        assertTrue(testToolHandle !== defaultToolHandle)
    }
}