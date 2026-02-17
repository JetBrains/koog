package ai.koog.agents.example.streaming

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.reflect.typeOf

/**
 * Example: Streaming AI Agent with Ktor Server
 * Demonstrates real-time text streaming from an LLM through a web endpoint
 */
fun main() {
    println("Starting Koog Streaming Server on http://localhost:8080")
    println("Available endpoints:")
    println(" - SSE /stream: Streaming agent")
    println()

    createServer().start(wait = true)
}


private fun createServer(): EmbeddedServer<*, *> {
    val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
    )

    return embeddedServer(
        factory = CIO,
        port = 8080,
        host = "127.0.0.1",
    ) {
        install(SSE)

        monitor.subscribe(ApplicationStopped) {
            promptExecutor.close()
        }

        configureRouting(
            promptExecutor = promptExecutor,
        )
    }
}

/**
 * Defines the /stream endpoint that receives text and streams AI responses
 */
private fun Application.configureRouting(
    promptExecutor: PromptExecutor,
) {
    routing {
        post("/stream") {
            val request = call.receiveText()
            println("Received request:\n$request")

            call.response.header("Content-Type", "text/plain; charset=UTF-8")

            // Stream response chunks as they arrive from the LLM
            call.respondOutputStream {
                val agent = createAgent(
                    promptExecutor = promptExecutor,
                    sendChunk = { text ->
                        write(text.toByteArray())
                        flush()
                    }
                )

                val result = agent.run(request)
                println("Agent result:\n$result")
            }
        }
    }
}

private fun createAgent(
    promptExecutor: PromptExecutor,
    sendChunk: suspend (String) -> Unit,
): AIAgent<String, String> {
    val agentConfig = AIAgentConfig(
        prompt = prompt("assistant-prompt") {
            system {
                +"You are a helpful assistant."
            }
        },
        model = OpenAIModels.Chat.GPT5_2,
        maxAgentIterations = 10,
    )

    return GraphAIAgent(
        typeOf<String>(),
        typeOf<String>(),
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = createStrategy(
            sendChunk = sendChunk,
        ),
    )
}

/**
 * Simple strategy that forwards the input to the LLM and streams the output chunks.
 */
private fun createStrategy(
    sendChunk: suspend (String) -> Unit,
): AIAgentGraphStrategy<String, String> = strategy<String, String>(
    name = "assistant-strategy"
) {
    val processStream by node<Flow<StreamFrame>, String> { input ->
        val totalText = StringBuilder()

        // Filter for text append frames and stream each chunk immediately
        input.filterIsInstance<StreamFrame.TextDelta>()
            .collect {
                sendChunk(it.text)
                totalText.append(it.text)
            }

        totalText.toString()
    }

    val requestLLMStream by nodeLLMRequestStreaming()

    nodeStart then requestLLMStream then processStream then nodeFinish
}
