package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.params.EmbeddingParams
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [GoogleEmbeddingParams] validation and behavior.
 *
 * This test class follows the pattern established by [GoogleLLMClientTest] for consistency
 * across the google-client package.
 */
class GoogleEmbeddingParamsTest {

    @Test
    fun `GoogleEmbeddingParams should accept valid dimensions`() {
        val params = GoogleEmbeddingParams(dimensions = 256)
        params.dimensions shouldBe 256
    }

    @Test
    fun `GoogleEmbeddingParams should reject invalid dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            GoogleEmbeddingParams(dimensions = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GoogleEmbeddingParams(dimensions = -1)
        }
    }

    @Test
    fun `GoogleEmbeddingParams should allow taskType without title`() {
        val params = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_QUERY
        )
        params.dimensions shouldBe 256
        params.taskType shouldBe GoogleEmbeddingTaskType.RETRIEVAL_QUERY
        params.title shouldBe null
    }

    @Test
    fun `GoogleEmbeddingParams should reject title without RETRIEVAL_DOCUMENT taskType`() {
        assertFailsWith<IllegalArgumentException> {
            GoogleEmbeddingParams(
                taskType = GoogleEmbeddingTaskType.RETRIEVAL_QUERY,
                title = "My Title"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GoogleEmbeddingParams(
                taskType = GoogleEmbeddingTaskType.SEMANTIC_SIMILARITY,
                title = "My Title"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GoogleEmbeddingParams(
                taskType = null,
                title = "My Title"
            )
        }
    }

    @Test
    fun `GoogleEmbeddingParams should accept title with RETRIEVAL_DOCUMENT taskType`() {
        val params = GoogleEmbeddingParams(
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT,
            title = "My Document Title"
        )
        params.taskType shouldBe GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT
        params.title shouldBe "My Document Title"
    }

    @Test
    fun `GoogleEmbeddingParams copy should preserve all fields`() {
        val original = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT,
            title = "Title"
        )
        val copied = original.copy(dimensions = 512)

        copied.dimensions shouldBe 512
        copied.taskType shouldBe GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT
        copied.title shouldBe "Title"
        original.dimensions shouldBe 256 // original unchanged
    }

    @Test
    fun `GoogleEmbeddingParams equality should compare all fields`() {
        val params1 = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_QUERY
        )
        val params2 = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_QUERY
        )
        val params3 = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.SEMANTIC_SIMILARITY
        )

        (params1 == params2) shouldBe true
        params1.hashCode() shouldBe params2.hashCode()
        (params1 == params3) shouldBe false
    }

    @Test
    fun `toGoogleEmbeddingParams should return same instance for GoogleEmbeddingParams`() {
        val googleParams = GoogleEmbeddingParams(
            dimensions = 256,
            taskType = GoogleEmbeddingTaskType.RETRIEVAL_QUERY
        )
        val result = googleParams.toGoogleEmbeddingParams()

        (result === googleParams) shouldBe true
    }

    @Test
    fun `toGoogleEmbeddingParams should construct default GoogleEmbeddingParams when given EmbeddingParams_None`() {
        val result = EmbeddingParams.None.toGoogleEmbeddingParams()

        result.dimensions shouldBe null
        result.taskType shouldBe null
        result.title shouldBe null
    }
}
