package ai.koog.agents.example.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorIntegrationExampleTest {

    @Test
    fun testApiUserEndpoint() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            main()
        }

        val response = client.get("/api/v1/user")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, user!", response.bodyAsText())
    }

    @Test
    fun testApiOrganizationEndpoint() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            main()
        }

        val response = client.get("/api/v1/organization")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, organization!", response.bodyAsText())
    }

    @Test
    fun testAgentsUserEndpoint() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            main()
        }

        val response = client.get("/agents/v1/user?query=test")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseText = response.bodyAsText()
        assertTrue(responseText.contains("Mock response for user request: test"))
    }

    @Test
    fun testAgentsOrganizationEndpoint() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test" to "true")
        }
        application {
            main()
        }

        val response = client.get("/agents/v1/organization?name=acme")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseText = response.bodyAsText()
        assertTrue(responseText.contains("Mock response for organization: acme"))
    }
}
