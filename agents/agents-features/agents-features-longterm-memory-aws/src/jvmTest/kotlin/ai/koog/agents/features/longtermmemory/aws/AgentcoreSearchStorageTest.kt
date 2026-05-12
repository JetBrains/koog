package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreCompositeSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreListingSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSimilaritySearchRequest
import ai.koog.rag.base.storage.search.SearchRequest
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import aws.sdk.kotlin.services.bedrockagentcore.model.ListMemoryRecordsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.ListMemoryRecordsResponse
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryContent
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordMetadataValue
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse
import aws.smithy.kotlin.runtime.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentcoreSearchStorageTest {

    private val client = mockk<BedrockAgentCoreClient>(relaxed = true)
    private val memoryId = "test-memory-id"
    private val strategyId = "test-strategy-id"

    private val storage = AgentcoreSearchStorage(
        client = client,
        agentcoreMemoryId = memoryId,
    )

    private fun makeMemoryRecordSummary(
        id: String = "record-1",
        text: String = "Some memory content",
        score: Double = 0.9,
        metadata: Map<String, MemoryRecordMetadataValue>? = null,
    ): MemoryRecordSummary = MemoryRecordSummary {
        memoryRecordId = id
        memoryStrategyId = strategyId
        content = MemoryContent.Text(text)
        this.score = score
        this.metadata = metadata
        createdAt = Instant.fromEpochSeconds(0)
        namespaces = emptyList()
    }

    // ---- SimilaritySearchRequest dispatches to retrieveMemoryRecords ----

    @Test
    fun testSimilaritySearchCallsRetrieveMemoryRecords() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "query",
                limit = 5
            )
        )

        coVerify(exactly = 1) { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) }
        coVerify(exactly = 0) { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) }
    }

    @Test
    fun testSimilaritySearchPassesCorrectParameters() = runTest {
        val requestSlot = slot<RetrieveMemoryRecordsRequest>()
        coEvery { client.retrieveMemoryRecords(capture(requestSlot)) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "find coffee",
                limit = 7
            ),
            namespace = "ns1"
        )

        assertEquals(memoryId, requestSlot.captured.memoryId)
        assertEquals("ns1", requestSlot.captured.namespace)
        assertEquals(strategyId, requestSlot.captured.searchCriteria?.memoryStrategyId)
        assertEquals("find coffee", requestSlot.captured.searchCriteria?.searchQuery)
        assertEquals(7, requestSlot.captured.searchCriteria?.topK)
    }

    @Test
    fun testSimilaritySearchReturnsConvertedResults() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "s1", text = "Semantic result", score = 0.95)
            )
        }

        val results = storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "query",
                limit = 5
            )
        )

        assertEquals(1, results.size)
        assertEquals("Semantic result", (results[0].document as AgentcoreMemoryRecord).content)
        assertEquals(0.95, results[0].score.value)
    }

    @Test
    fun testSimilaritySearchFiltersResultsBelowMinScore() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "r1", score = 0.9),
                makeMemoryRecordSummary(id = "r2", score = 0.3),
                makeMemoryRecordSummary(id = "r3", score = 0.6)
            )
        }

        val results = storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "q",
                limit = 10,
                minScore = 0.5
            )
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.score.value >= 0.5 })
    }

    @Test
    fun testSimilaritySearchWithNoMinScoreReturnsAllResults() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "r1", score = 0.1),
                makeMemoryRecordSummary(id = "r2", score = 0.9)
            )
        }

        val results =
            storage.search(
                AgentcoreSimilaritySearchRequest(
                    strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                    memoryStrategyId = strategyId,
                    queryText = "q",
                    limit = 10
                )
            )

        assertEquals(2, results.size)
    }

    @Test
    fun testSimilaritySearchWithNullNamespacePassesNullToClient() = runTest {
        val requestSlot = slot<RetrieveMemoryRecordsRequest>()
        coEvery { client.retrieveMemoryRecords(capture(requestSlot)) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "query",
                limit = 5
            ),
            namespace = null
        )

        assertEquals(null, requestSlot.captured.namespace)
    }

    // ---- ListingSearchRequest dispatches to listMemoryRecords ----

    @Test
    fun testListingSearchCallsListMemoryRecords() = runTest {
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        storage.search(
            AgentcoreListingSearchRequest(
                strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                memoryStrategyId = strategyId,
                limit = 5
            )
        )

        coVerify(exactly = 1) { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) }
        coVerify(exactly = 0) { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) }
    }

    @Test
    fun testListingSearchPassesCorrectParameters() = runTest {
        val requestSlot = slot<ListMemoryRecordsRequest>()
        coEvery { client.listMemoryRecords(capture(requestSlot)) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        storage.search(
            AgentcoreListingSearchRequest(
                strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                memoryStrategyId = strategyId,
                limit = 10
            ),
            namespace = "ns2"
        )

        assertEquals(memoryId, requestSlot.captured.memoryId)
        assertEquals(strategyId, requestSlot.captured.memoryStrategyId)
        assertEquals("ns2", requestSlot.captured.namespace)
        assertEquals(10, requestSlot.captured.maxResults)
    }

    @Test
    fun testListingSearchReturnsConvertedResults() = runTest {
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "r1", text = "Prefers dark mode", score = 0.8),
                makeMemoryRecordSummary(id = "r2", text = "Likes coffee", score = 0.7)
            )
        }

        val results = storage.search(
            AgentcoreListingSearchRequest(
                strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                memoryStrategyId = strategyId,
                limit = 5
            )
        )

        assertEquals(2, results.size)
        assertEquals("r1", (results[0].document as AgentcoreMemoryRecord).id)
        assertEquals("Prefers dark mode", (results[0].document as AgentcoreMemoryRecord).content)
        assertEquals(0.8, results[0].score.value)
        assertEquals("r2", (results[1].document as AgentcoreMemoryRecord).id)
    }

    // ---- Error handling ----

    @Test
    fun testRetrieveMemoryRecordsExceptionIsWrappedInRetrieveException() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } throws RuntimeException("AWS error")

        assertFailsWith<AgentcoreLongTermMemoryException.RetrieveException> {
            storage.search(
                AgentcoreSimilaritySearchRequest(
                    strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                    memoryStrategyId = strategyId,
                    queryText = "query",
                    limit = 5
                )
            )
        }
    }

    @Test
    fun testListMemoryRecordsExceptionIsWrappedInRetrieveException() = runTest {
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } throws RuntimeException("AWS list error")

        assertFailsWith<AgentcoreLongTermMemoryException.RetrieveException> {
            storage.search(
                AgentcoreListingSearchRequest(
                    strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                    memoryStrategyId = strategyId,
                    limit = 5
                )
            )
        }
    }

    // ---- Empty results ----

    @Test
    fun testEmptyResultsReturnedWhenNoRecordsFound() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        val results = storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "nothing",
                limit = 5
            )
        )

        assertTrue(results.isEmpty())
    }

    // ---- Metadata propagation ----

    @Test
    fun testMetadataIsPropagatedToMemoryRecord() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(
                    metadata = mapOf(
                        "source" to MemoryRecordMetadataValue.StringValue("chat"),
                        "lang" to MemoryRecordMetadataValue.StringValue("en")
                    )
                )
            )
        }

        val results = storage.search(
            AgentcoreSimilaritySearchRequest(
                strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                memoryStrategyId = strategyId,
                queryText = "query",
                limit = 5
            )
        )

        val record = results[0].document as AgentcoreMemoryRecord
        assertEquals("chat", record.metadata["source"])
        assertEquals("en", record.metadata["lang"])
    }

    // ---- Composite request behavior ----

    @Test
    fun testCompositeRequestRejectsUnsupportedRequestType() = runTest {
        val foreign = object : SearchRequest {
            override val limit: Int = 1
            override val offset: Int = 0
        }

        assertFailsWith<IllegalArgumentException> {
            storage.search(foreign)
        }
    }

    @Test
    fun testCompositeUsesPerSubrequestNamespaceAndIgnoresTopLevel() = runTest {
        val retrieveSlot = slot<RetrieveMemoryRecordsRequest>()
        val listSlot = slot<ListMemoryRecordsRequest>()
        coEvery { client.retrieveMemoryRecords(capture(retrieveSlot)) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }
        coEvery { client.listMemoryRecords(capture(listSlot)) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        val composite = AgentcoreCompositeSearchRequest(
            entries = listOf(
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreSimilaritySearchRequest(
                        strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                        memoryStrategyId = "sem-1",
                        queryText = "q",
                        limit = 3,
                    ),
                    namespace = "/ns/sem/",
                ),
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreListingSearchRequest(
                        strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                        memoryStrategyId = "up-1",
                        limit = 5
                    ),
                    namespace = "/ns/list/",
                ),
            ),
        )

        storage.search(composite, namespace = "/top-level-should-be-ignored/")

        assertEquals("/ns/sem/", retrieveSlot.captured.namespace)
        assertEquals("/ns/list/", listSlot.captured.namespace)
    }

    @Test
    fun testCompositeSkipsFailingSubrequestAndReturnsOthers() = runTest {
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } throws RuntimeException("AWS error")
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "survived", text = "from listing", score = 0.5),
            )
        }

        val composite = AgentcoreCompositeSearchRequest(
            entries = listOf(
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreSimilaritySearchRequest(
                        strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                        memoryStrategyId = "sem-1",
                        queryText = "q",
                        limit = 3,
                    ),
                    namespace = "/ns/sem/",
                ),
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreListingSearchRequest(
                        strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                        memoryStrategyId = "up-1",
                        limit = 5
                    ),
                    namespace = "/ns/list/",
                ),
            ),
        )

        val results = storage.search(composite)

        assertEquals(1, results.size)
        assertEquals("survived", (results[0].document as AgentcoreMemoryRecord).id)
    }

    @Test
    fun testCompositeConcatenatesResultsInSubrequestOrder() = runTest {
        // Order-preserving flatten is a documented contract (see AgentcoreSearchStorage KDoc).
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } returns RetrieveMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "sem-a", text = "A", score = 0.9),
                makeMemoryRecordSummary(id = "sem-b", text = "B", score = 0.8),
            )
        }
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = listOf(
                makeMemoryRecordSummary(id = "list-a", text = "C", score = 0.7),
                makeMemoryRecordSummary(id = "list-b", text = "D", score = 0.6),
            )
        }

        val composite = AgentcoreCompositeSearchRequest(
            entries = listOf(
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreSimilaritySearchRequest(
                        strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                        memoryStrategyId = "sem-1",
                        queryText = "q",
                        limit = 2,
                    ),
                    namespace = "/ns/sem/",
                ),
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreListingSearchRequest(
                        strategyType = AgentcoreMemoryStrategy.PREFERENCE,
                        memoryStrategyId = "up-1",
                        limit = 2
                    ),
                    namespace = "/ns/list/",
                ),
            ),
        )

        val results = storage.search(composite)
        val ids = results.map { (it.document as AgentcoreMemoryRecord).id }

        // First subrequest's results come first, preserving per-subrequest order.
        assertEquals(listOf("sem-a", "sem-b", "list-a", "list-b"), ids)
    }

    @Test
    fun testCompositePropagatesCancellationException() = runTest {
        // One subrequest hangs forever, the other would succeed; we cancel the enclosing scope
        // and expect CancellationException to propagate rather than be swallowed as a retrieve failure.
        val neverCompletes = CompletableDeferred<RetrieveMemoryRecordsResponse>()
        val subrequestStarted = CompletableDeferred<Unit>()
        coEvery { client.retrieveMemoryRecords(any<RetrieveMemoryRecordsRequest>()) } coAnswers {
            subrequestStarted.complete(Unit)
            neverCompletes.await()
        }
        coEvery { client.listMemoryRecords(any<ListMemoryRecordsRequest>()) } returns ListMemoryRecordsResponse {
            memoryRecordSummaries = emptyList()
        }

        val composite = AgentcoreCompositeSearchRequest(
            entries = listOf(
                AgentcoreCompositeSearchRequest.Entry(
                    request = AgentcoreSimilaritySearchRequest(
                        strategyType = AgentcoreMemoryStrategy.SEMANTIC,
                        memoryStrategyId = "sem-1",
                        queryText = "q",
                        limit = 3,
                    ),
                    namespace = "/ns/sem/",
                ),
            ),
        )

        val scope = CoroutineScope(Dispatchers.Default)
        var observed: Throwable? = null
        val job = scope.launch {
            try {
                storage.search(composite)
            } catch (e: CancellationException) {
                observed = e
                throw e
            }
        }
        // Wait until the subrequest has actually started before cancelling.
        subrequestStarted.await()
        job.cancelAndJoin()

        assertNotNull(observed, "CancellationException must propagate out of searchComposite")
    }
}
