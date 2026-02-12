package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowAgentConfig
import ai.koog.protocol.agent.FlowAgentKind
import ai.koog.protocol.agent.FlowAgentPrompt
import ai.koog.protocol.agent.FlowAgentRuntimeKind
import ai.koog.protocol.agent.agents.parallel.FlowParallelAgent
import ai.koog.protocol.agent.agents.parallel.FlowParallelAgentParameters
import ai.koog.protocol.agent.agents.parallel.ParallelMergeCondition
import ai.koog.protocol.agent.agents.react.FlowReActAgent
import ai.koog.protocol.agent.agents.react.FlowReActAgentParameters
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.task.FlowTaskAgentParameters
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.transform.FlowTransformParameters
import ai.koog.protocol.agent.agents.transform.FlowDataTransformation
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgentParameters
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.flow.FlowConfig
import ai.koog.protocol.model.FlowAgentModel
import ai.koog.protocol.model.FlowModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
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
        val resolvedModel = model ?: defaultModel
            ?: error("Model for an agent is node defined. Please specify whether an agent model or a default model for a flow.")

        val agentConfig = config ?: FlowAgentConfig()
        val agentPrompt = prompt ?: FlowAgentPrompt("")

        return when (type) {
            FlowAgentKind.TASK -> {
                val task = extractTask() ?: error("Missing <task> parameter")
                val toolNames = extractToolNames()
                FlowTaskAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = FlowTaskAgentParameters(task, toolNames)
                )
            }

            FlowAgentKind.VERIFY -> {
                val task = extractTask() ?: error("Missing <task> parameter")
                val toolNames = extractToolNames()
                FlowVerifyAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = FlowVerifyAgentParameters(task, toolNames)
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
                    FlowDataTransformation(value)
                } ?: emptyList()

                val params = FlowTransformParameters(transformations)

                FlowInputTransformAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = params
                )
            }

            FlowAgentKind.REACT -> {
                val task = extractTask() ?: error("Missing <task> parameter")
                val toolNames = extractToolNames()
                val reasoningInterval = params?.get("reasoningInterval")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                FlowReActAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = FlowReActAgentParameters(task, toolNames, reasoningInterval)
                )
            }

            FlowAgentKind.PARALLEL -> {
                val agentNames = params?.get("agents")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: error("Missing <agents> parameter for parallel agent")

                // Parse merge condition
                val mergeCondition = params["merge"]?.jsonObject?.let { mergeJson ->

                    val variable = mergeJson["variable"]?.jsonPrimitive?.content
                        ?: error("Missing <variable> in merge condition")

                    val operation = mergeJson["operation"]?.jsonPrimitive?.content
                        ?: error("Missing <operation> in merge condition")

                    val valueElement = mergeJson["value"]
                        ?: error("Missing <value> in merge condition")

                    val operationKind = ConditionOperationKind.entries
                        .find { it.id.equals(operation, ignoreCase = true) }
                        ?: error("Unsupported operation: $operation")

                    ParallelMergeCondition(
                        variable = variable,
                        operation = operationKind,
                        value = valueElement.toFlowPrimitiveType()
                    )
                }

                FlowParallelAgent(
                    name = name,
                    model = resolvedModel,
                    config = agentConfig,
                    prompt = agentPrompt,
                    parameters = FlowParallelAgentParameters(agentNames, mergeCondition)
                )
            }
        }
    }

    /**
     * Extracts common task and toolNames parameters from agent params.
     * Used by both TASK and VERIFY agent types.
     */
    private fun FlowAgentModel.extractTask(): String? {
        return params?.get("task")?.jsonPrimitive?.contentOrNull
    }

    private fun FlowAgentModel.extractToolNames(): List<String>? {
        return params?.get("toolNames")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    //endregion Private Methods
}
