package ai.koog.config.parser

import ai.koog.agent.FlowAgent
import ai.koog.agent.FlowAgentKind
import ai.koog.agent.koog.KoogTaskAgent
import ai.koog.agent.koog.KoogVerifyAgent
import ai.koog.agent.koog.KoogTransformAgent
import ai.koog.flow.FlowConfig
import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentModel
import ai.koog.model.FlowConfigModel
import ai.koog.model.toFlowAgentConfig
import kotlinx.serialization.json.Json

/**
 * Parser for JSON flow configuration files.
 */
public class FlowJsonConfigParser : FlowConfigParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun parse(input: String): FlowConfig {
        val model = json.decodeFromString<FlowConfigModel>(input)
        return FlowConfig(
            id = model.id,
            version = model.version,
            agents = model.agents.map { it.toFlowAgent() },
            transitions = model.transitions
        )
    }

    private fun FlowAgentModel.toFlowAgent(): FlowAgent {
        return when (runtime) {
            "koog", null -> createKoogAgent()
            else -> error("Unknown runtime: $runtime")
        }
    }

    private fun FlowAgentModel.createKoogAgent(): FlowAgent {
        return when (type) {
            FlowAgentKind.TASK -> KoogTaskAgent(
                name = name,
                model = model,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.VERIFY -> KoogVerifyAgent(
                name = name,
                model = model,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.TRANSFORM -> KoogTransformAgent(
                name = name,
                prompt = prompt,
                input = input ?: FlowAgentInput(),
                config = config.toFlowAgentConfig()
            )
            FlowAgentKind.PARALLEL -> error("Parallel agent type is not yet supported")
        }
    }
}
