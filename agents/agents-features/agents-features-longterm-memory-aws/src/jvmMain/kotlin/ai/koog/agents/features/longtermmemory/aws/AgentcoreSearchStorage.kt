package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreCompositeSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreListingSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSearchRequest
import ai.koog.agents.features.longtermmemory.aws.request.AgentcoreSimilaritySearchRequest
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import aws.sdk.kotlin.services.bedrockagentcore.model.ListMemoryRecordsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.MemoryRecordSummary
import aws.sdk.kotlin.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest
import aws.sdk.kotlin.services.bedrockagentcore.model.SearchCriteria
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * A [SearchStorage] implementation backed by AWS Bedrock AgentCore memory.
 *
 * Supports three search request shapes:
 * - [AgentcoreSimilaritySearchRequest]: semantic similarity search via the Bedrock `RetrieveMemoryRecords` API.
 * - [AgentcoreListingSearchRequest]: listing via the Bedrock `ListMemoryRecords` API.
 * - [AgentcoreCompositeSearchRequest]: fans out concurrently into multiple per-subrequest calls,
 *   each with its own [AgentcoreCompositeSearchRequest.Entry.namespace] and its own
 *   sub-request (`memoryStrategyId`, limits, etc.). Useful for AgentCore strategies that
 *   require several calls (e.g., EPISODIC episodes + reflections) or for cross-strategy
 *   retrieval (e.g., USER_PREFERENCE listing merged with SEMANTIC similarity).
 *
 * For composite requests the `namespace` argument of [search] is ignored in favor of the
 * per-subrequest namespaces. subrequests run in parallel under [coroutineScope]; a failure in one subrequest
 * is logged and its results are skipped, but other subrequests still return their results.
 * [CancellationException] always propagates.
 *
 * @param client the [BedrockAgentCoreClient] used to communicate with the AWS Bedrock AgentCore service.
 * @param agentcoreMemoryId the identifier of the AgentCore memory store to search.
 */
public class AgentcoreSearchStorage(
    public val client: BedrockAgentCoreClient,
    public val agentcoreMemoryId: String,
) : SearchStorage<TextDocument, SearchRequest> {

    private val logger = LoggerFactory.getLogger("AgentcoreSearchStorage")

    override suspend fun search(
        request: SearchRequest,
        namespace: String?
    ): List<SearchResult<TextDocument>> {
        require(request is AgentcoreSearchRequest) {
            "AgentcoreSearchStorage only accepts AgentcoreSearchRequest, got ${request::class.simpleName}"
        }
        return when (request) {
            is AgentcoreSimilaritySearchRequest -> searchSimilarity(request, namespace)
            is AgentcoreListingSearchRequest -> searchListing(request, namespace)
            is AgentcoreCompositeSearchRequest -> searchComposite(request)
        }
    }

    private suspend fun searchSimilarity(
        request: AgentcoreSimilaritySearchRequest,
        namespace: String?,
    ): List<SearchResult<TextDocument>> = runRetrieve(request.memoryStrategyId, namespace) {
        retrieveMemoryRecords(
            agentcoreMemoryStrategyId = request.memoryStrategyId,
            topK = request.limit,
            searchQuery = request.queryText,
            filterExpression = request.filterExpression,
            namespace = namespace
        )
            .map { AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(it, request.strategyType) }
            .filter { it.score.value >= (request.minScore ?: 0.0) }
    }

    private suspend fun searchListing(
        request: AgentcoreListingSearchRequest,
        namespace: String?,
    ): List<SearchResult<TextDocument>> = runRetrieve(request.memoryStrategyId, namespace) {
        listMemoryRecords(
            agentcoreMemoryStrategyId = request.memoryStrategyId,
            maxResults = request.limit,
            namespace = namespace
        )
            .map { AgentcoreMemoryRecordConverter.memoryRecordSummaryToSearchResult(it, request.strategyType) }
    }

    /**
     * Runs each subrequest of [request] in parallel. A subrequest failure is isolated: it is logged and
     * its results are dropped, but other subrequests still return their results. Cancellation
     * propagates unchanged.
     *
     * Results are concatenated in subrequest order (this keeps prompt placement predictable;
     * cross-subrequest score blending is intentionally left to the augmenter or a caller-supplied
     * merger, since scores across strategies/metrics are not directly comparable).
     */
    private suspend fun searchComposite(
        request: AgentcoreCompositeSearchRequest,
    ): List<SearchResult<TextDocument>> = coroutineScope {
        request.entries
            .map { subrequest ->
                async {
                    try {
                        search(subrequest.request, subrequest.namespace)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AgentcoreLongTermMemoryException.RetrieveException) {
                        logger.warn(
                            "Composite subrequest failed for strategyId=${subrequest.request.memoryStrategyId}, " +
                                "namespace=${subrequest.namespace}; skipping this subrequest.",
                            e,
                        )
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private inline fun <T> runRetrieve(
        strategyId: String,
        namespace: String?,
        block: () -> T,
    ): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw AgentcoreLongTermMemoryException.RetrieveException(
            "Failed to search memory records: memoryId=$agentcoreMemoryId, strategyId=$strategyId, namespace=$namespace",
            e,
        )
    }

    private suspend fun retrieveMemoryRecords(
        agentcoreMemoryStrategyId: String,
        topK: Int,
        searchQuery: String?,
        filterExpression: String?,
        namespace: String?,
    ): List<MemoryRecordSummary> {
        val request = RetrieveMemoryRecordsRequest {
            memoryId = agentcoreMemoryId
            this.namespace = namespace
            searchCriteria = SearchCriteria {
                memoryStrategyId = agentcoreMemoryStrategyId
                metadataFilters = AgentcoreMemoryRecordConverter.parseFilterExpression(filterExpression)
                this.searchQuery = searchQuery
                this.topK = topK
            }
        }

        logger.debug("Retrieving memory records for searchQuery $searchQuery and namespace $namespace")
        val memoryRecordSummaries = client.retrieveMemoryRecords(request).memoryRecordSummaries // TODO: paginated?
        logger.debug("Retrieved ${memoryRecordSummaries.size} memory records")

        return memoryRecordSummaries
    }

    private suspend fun listMemoryRecords(
        agentcoreMemoryStrategyId: String,
        maxResults: Int?,
        namespace: String?
    ): List<MemoryRecordSummary> {
        val request = ListMemoryRecordsRequest {
            memoryId = agentcoreMemoryId
            memoryStrategyId = agentcoreMemoryStrategyId
            this.namespace = namespace
            this.maxResults = maxResults
        }

        logger.debug("Listing memory records for namespace $namespace")
        val memoryRecordSummaries = client.listMemoryRecords(request).memoryRecordSummaries // TODO: paginated?
        logger.debug("Listed ${memoryRecordSummaries.size} memory records")

        return memoryRecordSummaries
    }
}
