package ai.koog.a2a.client

import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.test.runTest
import me.kpavlov.aimocks.a2a.MockAgentServer
import me.kpavlov.aimocks.a2a.model.AgentCard
import me.kpavlov.aimocks.a2a.model.create
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class A2AClientMokksyTest {
    val a2aServer = MockAgentServer(verbose = false)

    private val httpClient = HttpClient {
        install(Logging) {
            this.level = LogLevel.BODY
        }
    }

    private val transport = HttpJSONRPCClientTransport(
        url = a2aServer.baseUrl(),
        baseHttpClient = httpClient
    )

    val client = A2AClient(
        transport = transport,
        agentCardResolver = UrlAgentCardResolver(
            baseUrl = a2aServer.baseUrl(),
            baseHttpClient = httpClient,
        ),
    )

    @Test
    fun `should get card`() = runTest {
        // given
        val agentCard = AgentCard.create {
            name = "test-agent"
            description = "test-agent-description"
            url = a2aServer.baseUrl()
            documentationUrl = "https://example.com/documentation"
            version = "1.0.1"
            security = listOf(
                mapOf("oauth" to listOf("read")),
                mapOf("api-key" to listOf("mtls")),
            )
            provider {
                organization = "Acme, Inc."
                url = "https://example.com/organization"
            }
            capabilities {
                streaming = true
                pushNotifications = true
                stateTransitionHistory = true
            }
            skills += skill {
                id = "walk"
                name = "Walk the walk"
                description = "Walk the walk description"
                tags = listOf("walk", "tag")
            }
            skills += skill {
                id = "talk"
                name = "Talk the talk"
                description = "Talk the talk description"
                tags = listOf("walk", "tag")
            }
        }

        // Configure the mock server to respond with the AgentCard
        a2aServer.agentCard() responds {
            delay = 1.milliseconds
            card = agentCard
        }

        // when
        val actualAgentCard = client.loadAgentCard()

        // then
        actualAgentCard shouldNotBeNull {
            name shouldBe agentCard.name
            description shouldBe agentCard.description
            url shouldBe agentCard.url
            documentationUrl shouldBe agentCard.documentationUrl
            version shouldBe agentCard.version
            security shouldBe agentCard.security

            provider shouldNotBeNull {
                organization shouldBe agentCard.provider?.organization
                url shouldBe agentCard.provider?.url
            }

            capabilities shouldNotBeNull {
                streaming shouldBe agentCard.capabilities.streaming
                pushNotifications shouldBe agentCard.capabilities.pushNotifications
                stateTransitionHistory shouldBe agentCard.capabilities.stateTransitionHistory
            }

            skills shouldHaveSize agentCard.skills.size
            skills.zip(agentCard.skills).forEach { (actualSkill, skill) ->
                actualSkill shouldNotBeNull {
                    id shouldBe skill.id
                    name shouldBe skill.name
                    description shouldBe skill.description
                    tags shouldBe skill.tags
                }
            }
        }
    }
}
