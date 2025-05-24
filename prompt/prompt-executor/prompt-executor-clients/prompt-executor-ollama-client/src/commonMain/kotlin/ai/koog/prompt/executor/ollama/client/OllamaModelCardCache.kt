package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.ollama.client.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Manages Ollama model card cache
 */
internal class OllamaModelCardCache(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private val logger = KotlinLogging.logger { }

    private var modelCards: List<OllamaModelCard> = listOf()

    @OptIn(ExperimentalTime::class)
    private var lastCacheUpdate: Instant = Instant.DISTANT_PAST
    private val cacheValidity = 5.minutes

    /**
     * Returns the model cards for all the available models on the server. The model cards are cached.
     * @param refreshCache true if you want to force refresh the cached model cards, false otherwise
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getModels(refreshCache: Boolean): List<OllamaModelCard> {
        maybeReloadModelCards(refreshCache)
        return modelCards
    }

    /**
     * Returns a model card by its model name, on null if no such model exists on the server.
     * @param refreshCache true if you want to force refresh the cached model cards, false otherwise
     * @param pullIfMissing true if you want to pull the model from the Ollama registry, false otherwise
     */
    suspend fun getModelOrNull(
        name: String,
        refreshCache: Boolean,
        pullIfMissing: Boolean,
    ): OllamaModelCard? {
        maybeReloadModelCards(refreshCache)

        var modelCard = modelCards.findByNameOrNull(name)

        if (modelCard == null && pullIfMissing) {
            pullModel(name)
            maybeReloadModelCards(true)
            modelCard = modelCards.findByNameOrNull(name)
        }

        return modelCard
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun maybeReloadModelCards(refreshCache: Boolean) {
        val now = Clock.System.now()
        if (!refreshCache && now - lastCacheUpdate <= cacheValidity) return

        try {
            val tagsResponse = client.get("$baseUrl/api/tags") {
                contentType(ContentType.Application.Json)
            }.body<OllamaModelsListResponseDTO>()

            modelCards = tagsResponse.models.map { model ->
                loadModalCard(model.name, model.size)
            }
            lastCacheUpdate = now

            logger.info { "Reloaded ${modelCards.size} Ollama model cards" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch model cards from Ollama" }
            throw e
        }
    }

    private suspend fun loadModalCard(name: String, size: Long): OllamaModelCard {
        val showResponse = client.post("$baseUrl/api/show") {
            contentType(ContentType.Application.Json)
            setBody(OllamaShowModelRequestDTO(name = name))
        }.body<OllamaShowModelResponseDTO>()

        return showResponse.toOllamaModelCard(name, size)
    }

    private suspend fun pullModel(name: String) {
        try {
            val response = client.post("$baseUrl/api/pull") {
                contentType(ContentType.Application.Json)
                setBody(OllamaPullModelRequestDTO(name = name, stream = false))
            }.body<OllamaPullModelResponseDTO>()

            if ("success" !in response.status) error("Failed to pull model: '$name'")

            logger.info { "Pulled model '$name'" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to pull model '$name'" }
            throw e
        }
    }
}
