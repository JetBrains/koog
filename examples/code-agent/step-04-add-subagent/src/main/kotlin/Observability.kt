package ai.koog.agents.examples.codeagent.step04

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter

/**
 * Extracted observability setup used by agents in this module.
 * Logic is kept identical to the original blocks; only the agent name is parameterized.
 */
fun GraphAIAgent.FeatureContext.setupObservability(agentName: String) {
    install(OpenTelemetry) {
        setVerbose(true) // Enable verbose mode to send full strings instead of HIDDEN placeholders
        addLangfuseExporter(
            langfuseUrl = System.getenv("LANGFUSE_HOST") ?: "https://cloud.langfuse.com",
            langfusePublicKey = System.getenv("LANGFUSE_PUBLIC_KEY"),
            langfuseSecretKey = System.getenv("LANGFUSE_SECRET_KEY"),
            traceAttributes = listOf(
                CustomAttribute("langfuse.session.id", System.getenv("LANGFUSE_SESSION_ID") ?: ""),
                CustomAttribute("agent.name", agentName),
            )
        )
    }
    handleEvents {
        onToolCallStarting { ctx ->
            println("Tool '${ctx.tool.name}' called with args: ${ctx.toolArgs.toString().take(100)}")
        }
    }
}
