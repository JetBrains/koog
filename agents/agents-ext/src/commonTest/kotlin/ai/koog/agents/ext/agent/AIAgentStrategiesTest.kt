package ai.koog.agents.ext.agent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AIAgentStrategiesTest {
    private val defaultName = "re_act"

    @Test
    fun testChatStrategyDefaultName() = runTest {
        val strategy = chatAgentStrategy()
        assertEquals("chat", strategy.name)
    }

    @Test
    fun testReActStrategyDefaultName() = runTest {
        val strategy = reActStrategy()
        assertEquals(defaultName, strategy.name)
    }

    @Test
    fun testReActStrategyCustomName() = runTest {
        val customName = "custom_$defaultName"
        val strategy = reActStrategy(name = customName)
        assertEquals(customName, strategy.name)
    }

    @Test
    fun testReActStrategyWithCustomReasoningInterval() = runTest {
        val strategy = reActStrategy(reasoningInterval = 2)
        assertEquals(defaultName, strategy.name)
    }

    @Test
    fun testReActStrategyInvalidReasoningInterval() = runTest {
        assertFailsWith<IllegalArgumentException> {
            reActStrategy(reasoningInterval = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            reActStrategy(reasoningInterval = -1)
        }
    }
}