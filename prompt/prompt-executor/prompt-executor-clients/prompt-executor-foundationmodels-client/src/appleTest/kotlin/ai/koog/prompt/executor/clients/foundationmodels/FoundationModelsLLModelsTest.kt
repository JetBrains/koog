package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.prompt.llm.LLMCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoundationModelsLLModelsTest {

    @Test
    fun testProviderIdentity() {
        assertEquals("apple", AppleLLMProvider.id)
        assertEquals("Apple", AppleLLMProvider.display)
    }

    @Test
    fun testSystemDefaultAdvertisesOnlyCompletion() {
        val caps = AppleLLModels.SystemDefault.capabilities
        assertEquals(listOf(LLMCapability.Completion), caps)
    }

    @Test
    fun testSystemDefaultBelongsToCatalog() {
        assertTrue(AppleLLModels.SystemDefault in AppleLLModels.models)
        assertEquals(AppleLLMProvider, AppleLLModels.SystemDefault.provider)
    }
}
