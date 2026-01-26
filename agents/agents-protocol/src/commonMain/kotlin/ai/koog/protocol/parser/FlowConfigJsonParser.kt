package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt
import ai.koog.protocol.agent.FlowAgentRuntimeKind
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.task.FlowTaskAgentParameters
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformParameters
import ai.koog.protocol.agent.agents.transform.FlowInputTransformation
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgentParameters
import ai.koog.protocol.flow.FlowConfig
import ai.koog.protocol.model.FlowAgentModel
import ai.koog.protocol.model.FlowModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for JSON flow configuration files.
 */
public class FlowJsonConfigParser : FlowConfigParser {

    private val logger = KotlinLogging.logger { }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun parse(input: String): FlowConfig {
        // Decode JSON description
        val model = json.decodeFromString<FlowModel>(input)

        return FlowConfig(
            id = model.id,
            version = model.version,
            defaultModel = model.defaultModel,
            agents = model.agents.map { agentModel ->
                agentModel.toFlowAgent(model.defaultModel)
            },
            tools = model.tools.map { toolModel -> toolModel.toFlowTool() },
            transitions = model.transitions.map { transitionModel -> transitionModel.toFlowTransition() }
        )
    }

    //region Private Methods

    private fun FlowAgentModel.toFlowAgent(defaultModel: String?): FlowAgent {
        return when (runtime) {
            FlowAgentRuntimeKind.KOOG,
            null -> createKoogFlowAgent(defaultModel)

            else -> error("Unknown runtime: $runtime")
        }
    }

    private fun FlowAgentModel.createKoogFlowAgent(defaultModel: String?): FlowAgent {
        // Resolve model: agent.model -> flow.defaultModel -> error
        val resolvedModel = model ?: defaultModel
            ?: error("Model must be specified either on agent '$name' or as flow's defaultModel")

        val agentConfig = config ?: FlowAgentConfig()
        val agentPrompt = prompt ?: FlowAgentPrompt("")

        return when (type) {
            FlowAgentKind.TASK -> {
                val task = params?.get("task")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""

                val toolNames = params?.get("toolNames")
                    ?.let { it as? JsonArray }
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()

                val params = FlowTaskAgentParameters(task, toolNames)

                FlowTaskAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = params
                )
            }

            FlowAgentKind.VERIFY -> {
                val task = params?.get("task")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""

                val toolNames = params?.get("toolNames")
                    ?.let { it as? JsonArray }
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()

                val params = FlowVerifyAgentParameters(task, toolNames)

                FlowVerifyAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = params
                )
            }

            FlowAgentKind.TRANSFORM -> {
                val transformations = params?.get("transformations")?.jsonArray?.mapNotNull { transformation ->
                    val transformation = try {
                        transformation.jsonObject
                    } catch (e: IllegalArgumentException) {
                        logger.error(e) { "Invalid transformation: $transformation" }
                        null
                    } ?: return@mapNotNull null

                    val value = transformation["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    FlowInputTransformation(value)
                } ?: emptyList()

                val params = FlowInputTransformParameters(transformations)

                FlowInputTransformAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = params
                )
            }

            FlowAgentKind.PARALLEL -> error("PARALLEL agent type is not yet supported")
        }
    }

    //endregion Private Methods
}
