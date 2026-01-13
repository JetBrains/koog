package ai.koog.agents.example.codeagent.step05

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("code-agent-events")

/**
 * Extracted observability setup used by agents in this module.
 * Logic is kept identical to the original blocks; only the agent name is parameterized.
 */
fun GraphAIAgent.FeatureContext.setupObservability(agentName: String) {
    install(OpenTelemetry) {
        setVerbose(true) // Send full strings instead of HIDDEN placeholders
        addLangfuseExporter(
            traceAttributes = listOf(
                CustomAttribute("langfuse.session.id", System.getenv("LANGFUSE_SESSION_ID") ?: ""),
            )
        )
    }
    handleEvents {
        onToolCallStarting { ctx ->
            logger.info { "[$agentName] Tool '${ctx.toolName}' called with args: ${ctx.toolArgs.toString().take(100)}" }
        }
        onNodeExecutionStarting { ctx ->
            logger.info { "[$agentName] Node '${ctx.node.name}' STARTING" }
            if (ctx.node.name == "compressHistory") {
                logger.info { "[$agentName] Pre-compression history size: ${ctx.context.config.prompt.messages.size} messages" }
                logger.info { "[$agentName] Pre-compression total chars: ${ctx.context.config.prompt.messages.sumOf { it.content.length }}" }
            }
        }
        onNodeExecutionCompleted { ctx ->
            logger.info { "[$agentName] Node '${ctx.node.name}' COMPLETED" }
            if (ctx.node.name == "compressHistory") {
                logger.info { "[$agentName] Post-compression history size: ${ctx.context.config.prompt.messages.size} messages" }
                logger.info { "[$agentName] Post-compression total chars: ${ctx.context.config.prompt.messages.sumOf { it.content.length }}" }
            }
        }
    }
}
