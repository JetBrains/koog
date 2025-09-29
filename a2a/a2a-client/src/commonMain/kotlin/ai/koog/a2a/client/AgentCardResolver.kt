@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.a2a.client

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

public interface AgentCardResolver {
    public suspend fun resolve(): AgentCard
}

public class ExplicitAgentCardResolver(public val agentCard: AgentCard) : AgentCardResolver {
    override suspend fun resolve(): AgentCard = agentCard
}

public class UrlAgentCardResolver(
    public val baseUrl: String,
    public val path: String = wellKnownPath,
    baseHttpClient: HttpClient = HttpClient(),
) : AgentCardResolver {
    public companion object {
        @Suppress("ConstPropertyName")
        public const val wellKnownPath: String = "/.well-known/agent-card.json"
    }

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
