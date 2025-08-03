package ai.koog.agents.example.ktor

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.ktor.Koog
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.opentelemetry.exporter.logging.LoggingSpanExporter

@Tool
@LLMDescription("Searches in Google and returns the most relevant page URLs")
fun searchInGoogle(request: String, numberOfPages: String): List<String> {
    return emptyList()
}

@Tool
@LLMDescription("Executes bash command")
fun executeBash(command: String): String {
    return "bash not supported"
}

@Tool
@LLMDescription("Secret function -- call it and you'll see the output")
fun doSomethingElse(input: String): String {
    return "Surprise! I do nothing. Never call me again -_-"
}

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.main() {
    configureKoog()

    defineRoutes()
}

fun Application.configureKoog() {
    // Install Koog with mock mode enabled by default
    // This allows the example to run without requiring real LLM API keys
    // LLM configurations can still be loaded from application.yaml if available
    install(Koog) {
        mockMode()

        // Optional: Override with real LLM configuration if API keys are available
        // Uncomment and provide real API keys to use actual LLMs instead of mocks
        /*
        llm {
            openAI(apiKey = System.getenv("OPENAI_API_KEY") ?: error("OpenAI API key required")) {
                // Configure OpenAI settings if needed
            }

            fallback { }
        }
         */

        agentConfig {
            // MCP configuration - optional for this example
            // mcp {
            //     sse("put some url here...")
            // }

            registerTools {
                tool(::searchInGoogle)
                tool(::executeBash)
                tool(::doSomethingElse)
            }

            prompt {
                system("You are a professional assistant that can help with various tasks and provide informative responses based on user requests.")
            }

            install(OpenTelemetry) {
                addSpanExporter(LoggingSpanExporter())
            }
        }
    }
}

fun Application.defineRoutes() {
    routing {
        route("api/v1") {
            normalRoutes()
        }

        route("agents/v1") {
            agenticRoutes()
        }
    }
}

private fun Route.agenticRoutes() {
    get("user") {
        // For demo purposes, just return a simple response
        val userRequest = call.parameters["query"] ?: "default query"
        call.respond(HttpStatusCode.OK, "Mock response for user request: $userRequest")
    }
    get("organization") {
        val orgName = call.parameters["name"] ?: "unknown"
        call.respond(HttpStatusCode.OK, "Mock response for organization: $orgName")
    }
}

private fun Route.normalRoutes() {
    get("user") {
        call.respondText { "Hello, user!" }
    }
    get("organization") {
        call.respondText { "Hello, organization!" }
    }
}
