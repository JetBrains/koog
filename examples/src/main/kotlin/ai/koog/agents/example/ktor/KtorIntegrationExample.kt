package ai.koog.agents.example.ktor

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

@Tool
@LLMDescription("Searches in Google and returns the most relevant page URLs")
fun searchInGoogle(request: String, numberOfPages: String): List<String> {
    TODO()
}

@Tool
@LLMDescription("Executes bash command")
fun executeBash(command: String): String {
    TODO()
}


@Tool
@LLMDescription("Secret function -- call it and you'll see the output")
fun doSomethingElse(input: String): String {
    TODO()
}

fun main() {
    embeddedServer(CIO) {
        install(Koog) {
            llm {
                openAI(apiKey = "sk-1234567890") {
                    baseUrl = "localhost:8080"

                    timeouts {
                        requestTimeoutMillis = 10000
                    }
                }

                anthropic(apiKey = "sk-1234567890") {
                    timeouts {
                        requestTimeoutMillis = 10000
                    }
                }

                // Ollama, Google, OpenRouter implementations
                ollama {
                    baseUrl = "http://localhost:11434"
                }

                google(apiKey = "google-api-key") {
                    baseUrl = "https://generativelanguage.googleapis.com"
                }

                openRouter(apiKey = "openrouter-api-key") {
                    baseUrl = "https://openrouter.ai"
                }
            }

            agent {
                mcp {
                    sse("url")
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
                    addSpanExporter(object : SpanExporter {
                        override fun export(spans: Collection<SpanData?>): CompletableResultCode? {
                            TODO("Not yet implemented")
                        }

                        override fun flush(): CompletableResultCode? {
                            TODO("Not yet implemented")
                        }

                        override fun shutdown(): CompletableResultCode? {
                            TODO("Not yet implemented")
                        }
                    })
                }
            }
        }

        routing {
            route("api/v1") {
                get("user") {
                    call.respondText { "Hello, user!" }
                }
                get("organization") {
                    call.respondText { "Hello, organization!" }
                }
            }
            route("agents/v1") {
                get("user") {
                    val userRequest = call.receive<String>()

                    val isHarmful = moderateWithLLM(OpenAIModels.Moderation.Omni) {
                        user(userRequest)
                    }.isHarmful

                    if (isHarmful) {
                        call.respond(HttpStatusCode.BadRequest, "Harmful content detected")
                    }

                    val updatedRequest = askLLM(OllamaModels.Meta.LLAMA_3_2) {
                        system(
                            "You are a helpful assistant that can correct user answers. " +
                                    "You will get a user's question and your task is to make it more clear for the further processing."
                        )
                        user(userRequest)
                    }.single() as Message.Assistant

                    call.agentRespond(updatedRequest.content)
                }
                get("organization") {
                    val orgName = call.parameters["name"]!!

                    // custom strategy (and even custom Input/Output are also allowed for each agent in each route)
                    call.agentRespond("What's new in $orgName organization", strategy = reActStrategy())
                }
            }
        }
    }
}