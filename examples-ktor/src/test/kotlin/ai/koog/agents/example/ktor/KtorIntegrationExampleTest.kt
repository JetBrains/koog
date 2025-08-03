package ai.koog.agents.example.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorIntegrationExampleTest {

    @Test
    fun testApiUserEndpoint() = testApplication {
        application {
            main()
        }

        val response = client.get("/api/v1/user")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, user!", response.bodyAsText())
    }

    @Test
    fun testApiOrganizationEndpoint() = testApplication {
        application {
            main()
        }

        val response = client.get("/api/v1/organization")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, organization!", response.bodyAsText())
    }

    // Note: Agentic endpoints (/agents/v1/*) require LLM configuration and are not tested here
    // In a production environment, you would set up proper mocks for these endpoints
}
