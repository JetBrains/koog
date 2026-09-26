package ai.koog.rag.vector.embedder

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.mocks.MockDocument
import ai.koog.rag.vector.mocks.MockDocumentProvider
import ai.koog.rag.vector.mocks.MockFileSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TextDocumentEmbedderBatchTest {

    /**
     * Records whether the batch [embed] (List) or the single [embed] (String) path was used,
     * so we can prove the wrapper forwards the whole list instead of looping single calls.
     */
    private class SpyEmbedder : Embedder {
        var singleCalls: Int = 0
            private set
        var batchCalls: Int = 0
            private set
        var lastBatchInput: List<String>? = null
            private set

        override suspend fun embed(text: String): Vector {
            singleCalls++
            return Vector(listOf(text.length.toDouble()))
        }

        override suspend fun embed(texts: List<String>): List<Vector> {
            batchCalls++
            lastBatchInput = texts
            return texts.map { Vector(listOf(it.length.toDouble())) }
        }

        override fun diff(embedding1: Vector, embedding2: Vector): Double = 0.0
    }

    @Test
    fun testEmbedTextsForwardsBatchToInnerEmbedder() = runTest {
        val spy = SpyEmbedder()
        val embedder = TextDocumentEmbedder<MockDocument, String>(
            documentReader = MockDocumentProvider(MockFileSystem()),
            embedder = spy,
        )

        val result = embedder.embed(listOf("aa", "bbb"))

        // Forwarded as one batch to the inner embedder, not decomposed into single calls.
        assertEquals(1, spy.batchCalls, "should forward to the inner embedder's batch method once")
        assertEquals(0, spy.singleCalls, "should not loop single embed(text) calls")
        assertEquals(listOf("aa", "bbb"), spy.lastBatchInput)
        assertEquals(listOf(Vector(listOf(2.0)), Vector(listOf(3.0))), result)
    }
}
