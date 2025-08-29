package ai.koog.agents.example.ktor

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.ktor.llm
import ai.koog.ktor.mcp
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.EngineMain
import io.ktor.server.request.receive
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
    // Install the Koog plugin
    // LLM configurations will be loaded from application.yaml
    // You can still provide additional configuration or override settings from the YAML file
    install(Koog) {
        // The following configurations are optional and will override any settings from application.yaml
        llm {
            // Example of overriding a configuration from application.yaml
            // This will take precedence over the configuration in the YAML file
            openAI(apiKey = System.getenv("OPENAI_API_KEY") ?: "override-from-code") {
                // Override baseUrl only if needed
                // baseUrl = "custom-override-url"
            }

            fallback { }
        }

        agentConfig {
            mcp {
                sse("put some url here...")
            }

            registerTools {
                tool(::searchInGoogle)
                tool(::executeBash)
                tool(::doSomethingElse)
            }

            prompt {
                system("You are professional joke based on user's request")
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
        val userRequest = call.receive<String>()

        val isHarmful = llm().moderate(
            prompt("id") {
                user(userRequest)
            },
            OpenAIModels.Moderation.Omni
        ).isHarmful

        if (isHarmful) {
            call.respond(HttpStatusCode.BadRequest, "Harmful content detected")
            return@get
        }

        val updatedRequest = llm().execute(
            prompt("id") {
                system(
                    "You are a helpful assistant that can correct user answers. " +
                        "You will get a user's question and your task is to make it more clear for the further processing."
                )
                user(userRequest)
            },
            OllamaModels.Meta.LLAMA_3_2
        ).single()

        val output = aiAgent(updatedRequest.content, OpenAIModels.Chat.GPT4_1)
        call.respond(HttpStatusCode.OK, output)
    }
    get("organization") {
        val orgName = call.parameters["name"]!!
        val output = aiAgent(reActStrategy(), OpenAIModels.Chat.GPT4_1, "What's new in $orgName organization")
        call.respond(HttpStatusCode.OK, output)
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
