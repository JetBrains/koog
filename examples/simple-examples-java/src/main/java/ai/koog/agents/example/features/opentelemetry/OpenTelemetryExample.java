package ai.koog.agents.example.features.opentelemetry;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

public class OpenTelemetryExample {

    /**
     * Example of using OpenTelemetry with Koog agents
     * <p>
     * Example has two trace exporters:
     * - Log (can be viewed in standard log output)
     * - Otlp (can be viewed on a deployed local server)
     * <p>
     * To start a local OTLP server, please follow the instruction below.
     * Before running this example, start the OpenTelemetry services with:
     * ```
     * ./docker-compose up -d
     * ```
     * <p>
     * After running, you can view traces in the Jaeger UI at: <a href="http://localhost:16686">...</a>
     * <p>
     * To stop the services:
     * ```
     * docker-compose down
     * ```
     */
    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .systemPrompt("You are a helpful assistant.")
            .llmModel(OpenAIModels.Chat.GPT4o)
            .install(OpenTelemetry.Feature, config -> {
                // Add a console logger for local debugging
                config.addSpanExporter(LoggingSpanExporter.create());

                // Send traces to OpenTelemetry collector
                config.addSpanExporter(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint("http://localhost:4317")
                        .build()
                );
            })
            .build();

        System.out.println("Running agent with OpenTelemetry tracing.");

        var result = agent.run("Tell me a joke about programming");
        System.out.println(
            "Agent run completed with result: '" + result + "'." +
                "\nCheck Jaeger UI at http://localhost:16686 to view traces"
        );
    }
}
