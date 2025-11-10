package ai.koog.integration.tests

import aws.sdk.kotlin.services.bedrock.BedrockClient
import aws.sdk.kotlin.services.bedrock.listFoundationModels
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Verifies that the credentials provided to the build can access the Bedrock control-plane
 * API (ListFoundationModels). If the call fails we abort early instead of waiting for a
 * full prompt execution to blow up.
 *
 * This test only runs when AWS credentials are available (typically in heavy-tests workflow).
 */
class BedrockCredentialsSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "KOOG_HEAVY_TESTS", matches = "true")
    fun listFoundationModelsWorks() = runBlocking {
        val region = System.getenv("AWS_REGION") ?: "us-west-2"

        BedrockClient { this.region = region }.use { bedrock ->
            bedrock.listFoundationModels { }.modelSummaries?.shouldNotBeEmpty()
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_BEARER_TOKEN_BEDROCK", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "KOOG_HEAVY_TESTS", matches = "true")
    fun bedrockApiKeyAuthenticationWorks() = runBlocking {
        val region = System.getenv("AWS_REGION") ?: "us-east-1"

        BedrockClient {
            this.region = region
        }.use { bedrock ->
            bedrock.listFoundationModels { }.modelSummaries?.shouldNotBeEmpty()
        }
    }
}
