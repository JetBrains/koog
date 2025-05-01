package ai.grazie.code.agents.local.memory.providers

import ai.grazie.code.agents.local.memory.model.Concept
import ai.grazie.code.agents.local.memory.model.Fact
import ai.grazie.code.agents.local.memory.model.MemoryScope
import ai.grazie.code.agents.local.memory.model.MemorySubject
import ai.grazie.utils.mpp.LoggerFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Platform-specific HTTP client engine factory provider.
 * Each platform (JVM, Native, JS) implements this function to provide appropriate HTTP client engine.
 *
 * @return HTTP client engine factory for the current platform
 */
internal expect fun engineFactoryProvider(): HttpClientEngineFactory<*>

/**
 * Implementation of [AgentMemoryProvider] that stores facts in a remote server.
 * Provides HTTP-based access to a centralized memory storage service, enabling
 * fact sharing between different agents and applications.
 *
 * Features:
 * - JSON-based communication with automatic content negotiation
 * - Configurable timeouts for network operations
 * - Automatic error handling and logging
 * - Support for different memory scopes with dedicated endpoints
 *
 * @property config Configuration for the remote memory service connection
 * @property baseClient Base HTTP client to use, defaults to a new client with platform-specific engine
 */
data class SharedRemoteMemoryProvider(
    private val config: SharedRemoteMemoryConfig,
    private val baseClient: HttpClient = HttpClient(engineFactoryProvider())
) : AgentMemoryProvider {

    /**
     * Configured HTTP client with content negotiation and timeout settings.
     * Supports JSON serialization/deserialization and applies timeout configuration
     * from [SharedRemoteMemoryConfig].
     */
    private val httpClient: HttpClient = baseClient.config {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.clientConfig.timeout.requestTimeoutMillis
            connectTimeoutMillis = config.clientConfig.timeout.connectTimeoutMillis
            socketTimeoutMillis = config.clientConfig.timeout.socketTimeoutMillis
        }
    }

    /**
     * JSON serializer configuration for fact serialization.
     * Configured to ignore unknown keys for forward compatibility and
     * use pretty printing for better debugging.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Logger instance for error reporting and debugging.
     */
    private val logger = LoggerFactory.create(this::class.simpleName ?: "SharedRemoteMemory")

    /**
     * Constructs the appropriate API endpoint URL for a given subject and scope.
     * The endpoint structure reflects the hierarchical organization of memory storage:
     * `{serverUrl}/{scope-type}/{scope-identifier}/subject/{subject-name}`
     *
     * @param subject The memory subject to construct endpoint for
     * @param scope The memory scope that determines the endpoint path
     * @return Complete endpoint URL for the given subject and scope
     */
    private fun getEndpoint(subject: MemorySubject, scope: MemoryScope) = when (scope) {
        is MemoryScope.Agent -> "${config.serverUrl}/agent/${scope.name}/subject/${subject.name}"
        is MemoryScope.Feature -> "${config.serverUrl}/feature/${scope.id}/subject/${subject.name}"
        is MemoryScope.Product -> "${config.serverUrl}/product/${scope.name}/subject/${subject.name}"
        MemoryScope.CrossProduct -> "${config.serverUrl}/organization/subject/${subject.name}"
    }

    /**
     * Saves a fact to the remote memory service using HTTP POST request.
     * The fact is serialized to JSON and sent to the appropriate endpoint based on subject and scope.
     *
     * @param fact The fact to save
     * @param subject The subject context for the fact
     * @param scope The memory scope determining the endpoint
     *
     * Note: Logs a warning if the server response indicates failure
     */
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val endpoint = getEndpoint(subject, scope)
        val serialized = json.encodeToString(fact)

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(serialized)
        }

        if (response.status.isSuccess()) {
            val content = response.bodyAsText()
            logger.warning {
                "Failed to save fact to remote memory (status: ${response.status}). Response content: $content"
            }
        }
    }

    /**
     * Loads facts for a specific concept from the remote memory service.
     * Uses HTTP GET request to fetch facts associated with the concept's keyword.
     *
     * @param concept The concept to load facts for
     * @param subject The subject context to search within
     * @param scope The memory scope determining the endpoint
     * @return List of facts associated with the concept, or empty list if request fails
     */
    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val endpoint = "${getEndpoint(subject, scope)}/concept/${concept.keyword}"

        val response = httpClient.get(endpoint)
        if (!response.status.isSuccess()) return emptyList()

        return json.decodeFromString(response.bodyAsText())
    }

    /**
     * Loads all facts from the remote memory service within the specified context.
     * Uses HTTP GET request to fetch all available facts.
     *
     * @param subject The subject context to load facts from
     * @param scope The memory scope determining the endpoint
     * @return List of all facts in the context, or empty list if request fails
     */
    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val endpoint = "${getEndpoint(subject, scope)}/concepts"

        val response = httpClient.get(endpoint)
        if (!response.status.isSuccess()) return emptyList()

        return json.decodeFromString(response.bodyAsText())
    }

    /**
     * Searches for facts in the remote memory service based on a description.
     * Uses HTTP GET request with a query parameter for the search.
     * The search is performed server-side, allowing for sophisticated matching algorithms.
     *
     * @param description The description to search for
     * @param subject The subject context to search within
     * @param scope The memory scope determining the endpoint
     * @return List of matching facts, or empty list if request fails
     *
     * Note: Logs a warning if the server response indicates failure
     */
    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        val endpoint = "${getEndpoint(subject, scope)}/search"

        val response = httpClient.get(endpoint) {
            parameter("query", description)
        }

        if (!response.status.isSuccess()) {
            val content = response.bodyAsText()
            logger.warning {
                "Failed to read concept from remote memory (status: ${response.status}). Response content: $content"
            }

            return emptyList()
        }

        return response.body<List<Fact>>()
    }
}
