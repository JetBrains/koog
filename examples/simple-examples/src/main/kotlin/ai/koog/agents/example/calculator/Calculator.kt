package ai.koog.agents.example.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import java.time.Duration

suspend fun main() {
    val logger = LoggerFactory.getLogger("Calculator")
    logger.debug("Starting Calculator example with DEBUG level enabled")
    // Create a tool registry with calculator tools
    val toolRegistry = ToolRegistry {
        // Special tool, required with this type of agent.
        tool(AskUser)
        tool(SayToUser)
        tools(CalculatorTools().asTools())
    }

    // Create agent config with proper prompt
    val agentConfig = AIAgentConfig(
        prompt = prompt("test") {
            system("You are a calculator.")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 50
    )

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        // Create the runner
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = CalculatorStrategy.strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        ) {
            handleEvents {
                onToolCallStarting { eventContext ->
                    println("Tool called: tool ${eventContext.toolName}, args ${eventContext.toolArgs}")
                }

                onAgentExecutionFailed { eventContext ->
                    println(
                        "An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}"
                    )
                }

                onAgentCompleted { eventContext ->
                    println("Result: ${eventContext.result}")
                }
            }

            install(OpenTelemetry) {
                setServiceInfo(
                    "calculator",
                    "0.0.1"
                )
                addResourceAttributes(
                    mapOf(AttributeKey.stringKey("service.instance.id") to "run-1")
                )
                addMetricExporter(
                    OtlpGrpcMetricExporter.builder()
                        .setEndpoint("http://localhost:17011")
                        .setTimeout(2, TimeUnit.SECONDS)
                        .build(),
                    Duration.ofSeconds(1)
                )
                addMetricFilter(
                    "koog.tool.count",
                    setOf("koog.tool.call.status")
                )
                addSpanExporter(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint("http://localhost:17011")
                        .setTimeout(2, TimeUnit.SECONDS)
                        .build()
                )
            }
        }

        val expression = "(10 + 20) * (5 + 5) / (2 - 11) * 445 / 23 + 2334 / 23 + 3"
        val result = agent.run(expression)
        logger.info("Agent result: {}", result)
    }
}
