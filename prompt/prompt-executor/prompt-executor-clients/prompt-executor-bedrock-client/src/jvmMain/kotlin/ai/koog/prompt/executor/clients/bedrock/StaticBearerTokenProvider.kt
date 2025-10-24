package ai.koog.prompt.executor.clients.bedrock

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.http.auth.BearerToken
import aws.smithy.kotlin.runtime.http.auth.BearerTokenProvider
import aws.smithy.kotlin.runtime.time.Instant

/**
 * A [BearerTokenProvider] for a fixed Bedrock API key token.
 *
 * @param token The Bedrock API key token value
 */
public class StaticBearerTokenProvider(
    private val token: String
) : BearerTokenProvider {

    override suspend fun resolve(attributes: Attributes): BearerToken {
        if (token.isBlank()) {
            throw IllegalStateException("StaticBearerTokenProvider - token must not be blank")
        }
        return object : BearerToken {
            override val token: String = this@StaticBearerTokenProvider.token
            override val attributes: Attributes = emptyAttributes()
            override val expiration: Instant? = null
        }
    }
}
