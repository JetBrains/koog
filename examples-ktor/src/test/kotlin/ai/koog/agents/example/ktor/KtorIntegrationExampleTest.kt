package ai.koog.agents.example.ktor

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testAgentsUserEndpoint() = testApplication {
        application {
            main()
        }

        // Test the agentic user endpoint with mock LLM responses
        val response = client.post("/agents/v1/user") {
            contentType(ContentType.Application.Json)
            setBody("\"Tell me a joke about programming\"")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        val responseText = response.bodyAsText()
        // Should get a mock response since we're using mock mode
        assertTrue(responseText.isNotEmpty(), "Response should not be empty")
    }

    @Test
    fun testAgentsOrganizationEndpoint() = testApplication {
        application {
            main()
        }

        // Test the agentic organization endpoint with mock LLM responses
        val response = client.get("/agents/v1/organization?name=TestOrg")
        
        assertEquals(HttpStatusCode.OK, response.status)
        val responseText = response.bodyAsText()
        // Should get a mock response since we're using mock mode
        assertTrue(responseText.isNotEmpty(), "Response should not be empty")
    }
}
