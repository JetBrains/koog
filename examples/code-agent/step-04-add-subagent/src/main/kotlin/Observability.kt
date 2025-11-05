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

        val publicKey = System.getenv("LANGFUSE_PUBLIC_KEY")
        val secretKey = System.getenv("LANGFUSE_SECRET_KEY")
        val host = System.getenv("LANGFUSE_HOST")
        val sessionId = System.getenv("LANGFUSE_SESSION_ID")
        val missing = buildList {
            if (publicKey.isNullOrBlank()) add("LANGFUSE_PUBLIC_KEY")
            if (secretKey.isNullOrBlank()) add("LANGFUSE_SECRET_KEY")
            if (host.isNullOrBlank()) add("LANGFUSE_HOST")
            if (sessionId.isNullOrBlank()) add("LANGFUSE_SESSION_ID")
        }
        if (missing.isEmpty()) {
            addLangfuseExporter(
                langfuseUrl = host!!,
                langfusePublicKey = publicKey!!,
                langfuseSecretKey = secretKey!!,
                traceAttributes = listOf(
                    CustomAttribute("langfuse.session.id", sessionId!!),
                    CustomAttribute("agent.name", agentName),
                )
            )
        } else {
            println("Observability: Langfuse disabled — missing env var(s): ${missing.joinToString(", ")}.")
        }
    }
    handleEvents {
        onToolCallStarting { ctx ->
            println("Tool '${ctx.tool.name}' called with args: ${ctx.toolArgs.toString().take(100)}")
        }
    }
}
