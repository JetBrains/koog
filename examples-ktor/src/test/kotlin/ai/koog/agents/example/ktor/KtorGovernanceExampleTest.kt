package ai.koog.agents.example.ktor

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorGovernanceExampleTest {

    @Test
    fun testPublicAgentEndpoint() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            module()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                    }
                )
            }
        }

        val response = client.post("/api/v1/agent/public") {
            contentType(ContentType.Application.Json)
            setBody(AgentRequest("Search for Kotlin tutorials"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseText = response.bodyAsText()
        // Should contain the role name in the response since we're testing governance
        assertTrue(responseText.contains("guest") || responseText.contains("Task completed"))
    }

    @Test
    fun testAuthenticatedAgentEndpointWithoutAuth() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            module()
        }

        val response = client.post("/api/v1/agent") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Query the database"}""")
        }

        // Should return unauthorized since no JWT token provided
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
