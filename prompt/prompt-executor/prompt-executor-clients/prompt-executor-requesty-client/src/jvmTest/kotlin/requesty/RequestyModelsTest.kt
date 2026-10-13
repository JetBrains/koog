package requesty

import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.executor.clients.requesty.RequestyModels
import ai.koog.prompt.llm.LLMProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestyModelsTest {

    @Test
    fun `Requesty models should have Requesty provider`() {
        val models = RequestyModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Requesty,
                actual = model.provider,
                message = "Requesty model ${model.id} doesn't have Requesty provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `RequestyModels should return all declared models`() {
        val reflectionModels = RequestyModels.list().map { it.id }

        val models = RequestyModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }

    @Test
    fun `Requesty model ids should be prefixed with the upstream provider`() {
        RequestyModels.models.forEach { model ->
            assertTrue(model.id.contains('/'), "Requesty model id ${model.id} should look like provider/model")
        }
    }

    @Test
    fun `Requesty models should include the main entries`() {
        RequestyModels.models.map { it.id } shouldContainAll listOf(
            "openai/gpt-4o-mini",
            "anthropic/claude-sonnet-4-5",
            "google/gemini-2.5-flash",
        )
    }
}
