package ai.koog.a2a.client

import ai.koog.a2a.consts.A2AConsts
import ai.koog.a2a.model.AgentCard
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Represents a resolver capable of fetching an [AgentCard].
 *
 * Implementations of this interface are responsible for providing the mechanism to retrieve
 * the [AgentCard], which may include network requests, local lookups, or other means of resolution.
 */
public interface AgentCardResolver {
    /**
     * Resolves and retrieves an [AgentCard].
     */
    public suspend fun resolve(): AgentCard
}

/**
 * An [AgentCardResolver] that always returns the provided [agentCard].
 */
public class ExplicitAgentCardResolver(public val agentCard: AgentCard) : AgentCardResolver {
    override suspend fun resolve(): AgentCard = agentCard
}

/**
 * An [AgentCardResolver] that fetches the [AgentCard] from the provided [baseUrl] at [path].
 */
public class UrlAgentCardResolver(
    public val baseUrl: String,
    public val path: String = A2AConsts.AGENT_CARD_WELL_KNOWN_PATH,
    baseHttpClient: HttpClient = HttpClient(),
) : AgentCardResolver {
    private val httpClient: HttpClient = baseHttpClient.config {
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }

        expectSuccess = true
    }

    override suspend fun resolve(): AgentCard {
        return httpClient.get(path).body<AgentCard>()
    }
}
