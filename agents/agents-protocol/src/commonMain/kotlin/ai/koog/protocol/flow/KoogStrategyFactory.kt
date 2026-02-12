package ai.koog.protocol.flow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.protocol.agent.FlowAgent
import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.agent.agents.parallel.FlowParallelAgent
import ai.koog.protocol.agent.agents.react.FlowReActAgent
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformation
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.transition.FlowTransition
import ai.koog.protocol.transition.FlowTransitionCondition

/**
 * Factory for creating AI agent graph strategies from flow configurations.
 */
public object KoogStrategyFactory {

    /**
     * Builds an agent graph strategy from flow configuration.
     */
    public fun buildStrategy(
        id: String,
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
        toolRegistry: ToolRegistry,
    ): AIAgentGraphStrategy<FlowDataType, FlowDataType> =
        strategy(id) {
            // Nodes
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, toolRegistry)
                node
            }

            if (agents.isEmpty() || transitions.isEmpty()) {
                connectNodesSequentially(collectedNodes)
                return@strategy
            }

            connectNodesWithTransitions(collectedNodes, agents, transitions)
        }

    //region Private Methods

    //region Edges

    private fun AIAgentSubgraphBuilderBase<FlowDataType, FlowDataType>.transitionToEdge(
        collectedNodes: List<AIAgentNodeBase<FlowDataType, FlowDataType>>,
        transition: FlowTransition,
    ) {
        val fromNode = collectedNodes.find { it.name == transition.from }
            ?: error("Unable to find 'from' node for transition '${transition.transitionString}': ${transition.from}")

        if (transition.to == FINISH_NODE_PREFIX) {
            createEdgeToFinish(fromNode, transition.condition)
            return
        }
        val toNode = collectedNodes.find { it.name == transition.to }
            ?: error("Unable to find 'to' node for transition '${transition.transitionString}': ${transition.to}")

        createEdge(fromNode, toNode, transition.condition)
    }

    private fun AIAgentSubgraphBuilderBase<FlowDataType, FlowDataType>.createEdge(
        fromNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        toNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        condition: FlowTransitionCondition?
    ) {
        if (condition == null) {
            edge(fromNode forwardTo toNode)
            return
        }

        edge(
            fromNode forwardTo toNode onCondition { output -> evaluateCondition(output, condition) }
        )
    }

    /**
     * Creates an edge from a node to the finish node, optionally with a condition.
     */
    private fun AIAgentSubgraphBuilderBase<FlowDataType, FlowDataType>.createEdgeToFinish(
        fromNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        condition: FlowTransitionCondition?
    ) = createEdge(fromNode, nodeFinish, condition)

    //endregion Edges

    //region Nodes

    /**
     * Converts a flow agent into Koog node delegate for a given flow agent type.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.convertFlowAgentToKoogNode(
        agent: FlowAgent,
        agents: List<FlowAgent>,
        toolRegistry: ToolRegistry,
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        return when (agent) {
            is FlowTaskAgent -> nodeTask(agent, toolRegistry)
            is FlowVerifyAgent -> nodeVerify(agent, toolRegistry)
            is FlowInputTransformAgent -> nodeTransform(agent)
            is FlowReActAgent -> nodeReAct(agent, toolRegistry)
            is FlowParallelAgent -> nodeParallel(agent, agents, toolRegistry)
            else -> error("Agent type '${agent::type}' is not yet supported")
        }
    }

    /**
     * Creates a task node which is executed as a subgraphWithTask strategy.
     * Input and output types for flow agent are preserved in the subgraphWithTask strategy.
     *
     * @param agent The flow agent to execute a particular task;
     * @param toolRegistry The tool registry to use for tool selection;
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTask(
        agent: FlowTaskAgent,
        toolRegistry: ToolRegistry,
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model)
        return subgraphWithTask<FlowDataType, FlowDataType>(
            name = agent.name,
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) { _: FlowDataType ->
            agent.parameters.task
        }
    }

    /**
     * Creates a subgraph with 'ReAct' strategy.
     * The subgraph takes the flow agent input type [FlowDataType],
     * converts it into the [reActStrategy] input type [String], and return an expected agent flow type [FlowDataType].
     *
     * @param agent The flow agent configuration;
     * @param toolRegistry The tool registry for selecting tools;
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeReAct(
        agent: FlowReActAgent,
        toolRegistry: ToolRegistry,
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model)
        return subgraph(
            name = agent.name,
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) {
            // Node to transform the custom [FlowDataType] type into the 'ReAct' strategy input of type [String]
            val prepareReActInput by node<FlowDataType, String> { _: FlowDataType ->
                agent.parameters.task
            }

            // Use the reActStrategy as a nested subgraph
            val reactSubgraph = reActStrategy(
                reasoningInterval = agent.parameters.reasoningInterval,
                name = "${agent.name}_react_strategy"
            )

            // Node to wrap the output from the 'ReAct' strategy back to [FlowDataType] flow agent type
            // to send it further into the agent flow.
            val wrapOutput by node<String, FlowDataType> { output ->
                FlowDataType.FlowString(output)
            }

            edge(nodeStart forwardTo prepareReActInput)
            edge(prepareReActInput forwardTo reactSubgraph)
            edge(reactSubgraph forwardTo wrapOutput)
            edge(wrapOutput forwardTo nodeFinish)
        }
    }

    /**
     * Creates a node that checks/validates using LLM with structured verification output.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeVerify(
        agent: FlowVerifyAgent,
        toolRegistry: ToolRegistry,
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model)
        val verifySubgraph by subgraphWithVerification<FlowDataType>(
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) { _: FlowDataType ->
            agent.parameters.task
        }

        return subgraph(name = agent.name) {
            val transformResult by node<CriticResult<FlowDataType>, FlowDataType> { result ->
                FlowDataType.FlowCritiqueResult(
                    success = result.successful,
                    feedback = result.feedback,
                    input = result.input
                )
            }

            nodeStart then verifySubgraph then transformResult then nodeFinish
        }
    }

    /**
     * Creates a transform node that applies transformations without LLM.
     * The transformation converts input from one FlowDataType type to another based on defined rules.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTransform(
        agent: FlowInputTransformAgent
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        return subgraph(name = agent.name) {
            val nodeTransform by node<FlowDataType, FlowDataType> { runtimeInput ->
                transformFlowDataType(runtimeInput, agent.parameters.transformations)
            }

            nodeStart then nodeTransform then nodeFinish
        }
    }

    private fun AIAgentSubgraphBuilderBase<*, *>.nodeParallel(
        agent: FlowParallelAgent,
        agents: List<FlowAgent>,
        toolRegistry: ToolRegistry,
    ): AIAgentSubgraphDelegate<FlowDataType, FlowDataType> {
        return subgraph(name = agent.name) {

            val parallelNodes = agents
                .filter { flowAgent -> flowAgent.name in agent.parameters.agents }
                .map { flowAgent -> convertFlowAgentToKoogNode(flowAgent, agents, toolRegistry) }

            val nodeParallel = parallel(parallelNodes)
        }
    }

    //endregion Nodes

    //region Strategy

    private fun AIAgentGraphStrategyBuilder<FlowDataType, FlowDataType>.connectNodesSequentially(
        nodes: List<AIAgentSubgraph<FlowDataType, FlowDataType>>,
    ) {
        if (nodes.isEmpty()) {
            edge(nodeStart forwardTo nodeFinish)
            return
        }

        // Get the first agent to connect to a start node
        edge(nodeStart forwardTo nodes.first())

        // Collect each other agent in a chain.
        nodes.zipWithNext { current, next ->
            edge(current forwardTo next)
        }

        edge(nodes.last() forwardTo nodeFinish)
    }

    private fun AIAgentGraphStrategyBuilder<FlowDataType, FlowDataType>.connectNodesWithTransitions(
        nodes: List<AIAgentSubgraph<FlowDataType, FlowDataType>>,
        agents: List<FlowAgent>,
        transitions: List<FlowTransition>,
    ) {
        val firstAgentName = FlowUtil.getFirstAgent(agents, transitions).name
        val firstNode = nodes.find { it.name == firstAgentName }
            ?: error("First agent not found: $firstAgentName")

        // Connect the koog system start node to the first flow node
        edge(nodeStart forwardTo firstNode)

        // Process the rest of transitions and create edges
        transitions.forEach { transition -> transitionToEdge(nodes, transition) }

        // Connect all agents without outgoing transitions to finish
        val agentsWithOutgoingTransitions = transitions.map { it.from }.toSet()
        val nodesWithoutFinish = nodes.filter { node ->
            node.name !in agentsWithOutgoingTransitions
        }

        nodesWithoutFinish.forEach { nodeWithoutFinish ->
            createEdgeToFinish(nodeWithoutFinish, null)
        }
    }

    //endregion Strategy

    private fun ToolRegistry.defineToolSelectionStrategy(toolNames: List<String>?): ToolSelectionStrategy {
        if (toolNames == null) {
            return ToolSelectionStrategy.ALL
        }

        if (toolNames.isEmpty()) {
            return ToolSelectionStrategy.NONE
        }

        val selectedTools = this.tools.filter { tool ->
            tool.name in toolNames
        }

        return ToolSelectionStrategy.Tools(selectedTools.map { it.descriptor })
    }

    /**
     * Evaluates a transition condition against the current output.
     *
     * @param output The current agent output
     * @param condition The condition to evaluate
     * @return true if the condition is satisfied
     */
    private fun evaluateCondition(output: FlowDataType, condition: FlowTransitionCondition): Boolean {
        // String / Boolean / Double / Int vs. String / Boolean / Double / Int
        val conditionValue: Comparable<*> = when (condition.value) {
            is FlowDataType.FlowBoolean -> condition.value.data
            is FlowDataType.FlowInteger -> condition.value.data
            is FlowDataType.FlowDouble -> condition.value.data
            is FlowDataType.FlowString -> condition.value.data
            else -> error("Unsupported condition type ${condition.value}")
        }

        val conditionVariable = extractOutputValue(outputString = condition.variable)

        // TODO: Add validation for conditionVariable
        val outputValue: Comparable<*> = when (output) {
            is FlowDataType.FlowBoolean -> output.data
            is FlowDataType.FlowInteger -> output.data
            is FlowDataType.FlowDouble -> output.data
            is FlowDataType.FlowString -> output.data
            is FlowDataType.FlowCritiqueResult -> {
                when (conditionVariable) {
                    "success" -> output.success
                    "feedback" -> output.feedback
                    else -> error("Unsupported condition variable: $conditionVariable")
                }
            }
            else -> error("Not primitive types are not yet supported")
        }

        return when (condition.operation) {
            ConditionOperationKind.EQUALS -> outputValue == conditionValue
            ConditionOperationKind.NOT_EQUALS -> outputValue != conditionValue
            ConditionOperationKind.MORE -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() > conditionValue.toDouble()
                    outputValue is String && conditionValue is String ->
                        outputValue.compareTo(conditionValue, ignoreCase = true) > 0
                    else -> false
                }
            }
            ConditionOperationKind.LESS -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() < conditionValue.toDouble()
                    outputValue is String && conditionValue is String ->
                        outputValue.compareTo(conditionValue, ignoreCase = true) < 0
                    else -> false
                }
            }
            ConditionOperationKind.MORE_OR_EQUAL -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() >= conditionValue.toDouble()
                    outputValue is String && conditionValue is String ->
                        outputValue.compareTo(conditionValue, ignoreCase = true) >= 0
                    else -> false
                }
            }
            ConditionOperationKind.LESS_OR_EQUAL -> {
                when {
                    outputValue is Number && conditionValue is Number ->
                        outputValue.toDouble() <= conditionValue.toDouble()
                    outputValue is String && conditionValue is String ->
                        outputValue.compareTo(conditionValue, ignoreCase = true) <= 0
                    else -> false
                }
            }
            ConditionOperationKind.NOT -> {
                when {
                    outputValue is Boolean && conditionValue is Boolean -> outputValue != conditionValue
                    else -> false
                }
            }
            ConditionOperationKind.AND -> {
                when {
                    outputValue is Boolean && conditionValue is Boolean -> outputValue && conditionValue
                    else -> false
                }
            }
            ConditionOperationKind.OR -> {
                when {
                    outputValue is Boolean && conditionValue is Boolean -> outputValue || conditionValue
                    else -> false
                }
            }
        }
    }

    /**
     * Extracts the variable name from a formatted output string.
     *
     * This method expects an input string in the format: "output.<variable_name>".
     * It splits the string on the "." character and validates its structure to ensure the prefix
     * is "output". If the format is invalid, it throws an error.
     *
     * @param outputString The string representing the output variable to get a value from;
     * @return The variable name extracted from the input string;
     *
     * @throws IllegalArgumentException if the input string is not in the expected format.
     */
    private fun extractOutputValue(outputString: String): String {
        val outputParts = outputString.split(".")

        if (outputParts.size != 2) {
            error("Expected format for condition variable: 'output.<variable>', got: $outputString")
        }

        if (outputParts[0] != "output") {
            error("Expected to get the 'output' keyword for accessing the output data properties: 'output.<variable>', got: $outputString")
        }

        return outputParts[1]
    }

    /**
     * Transforms a FlowDataType based on the provided transformation configuration.
     *
     * @param input The input to transform
     * @param transformations The list of transformations to apply.
     *
     * @return Transformed input, or original input if no matching transformation found
     */
    private fun transformFlowDataType(
        input: FlowDataType,
        transformations: List<FlowInputTransformation>
    ): FlowDataType {
        if (transformations.isEmpty()) {
            return input
        }

        val transformation = transformations.singleOrNull()?.value
            ?: error("Unsupported transformation configuration")

        // Drop the "input." prefix from the condition variable
        val valueString = transformation.split(".").lastOrNull() ?: ""

        if (valueString.isBlank()) {
            return input
        }

        return when (valueString) {
            "success" -> {
                val value = (input as? FlowDataType.FlowCritiqueResult)?.success
                    ?: error("Unexpected value string: $valueString")

                FlowDataType.FlowBoolean(value)
            }
            "feedback" -> {
                val value = (input as? FlowDataType.FlowCritiqueResult)?.feedback
                    ?: error("Unexpected value string: $valueString")

                FlowDataType.FlowString(value)
            }
            else -> error("Not primitive types are not yet supported")
        }
    }

    //endregion Private Methods
}
