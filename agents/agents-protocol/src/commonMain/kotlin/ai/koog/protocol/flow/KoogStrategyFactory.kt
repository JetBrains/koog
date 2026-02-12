package ai.koog.protocol.flow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.ParallelResult
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
import ai.koog.protocol.agent.agents.parallel.ParallelMergeCondition
import ai.koog.protocol.agent.agents.react.FlowReActAgent
import ai.koog.protocol.agent.agents.task.FlowTaskAgent
import ai.koog.protocol.agent.agents.transform.FlowInputTransformAgent
import ai.koog.protocol.agent.agents.transform.FlowDataTransformation
import ai.koog.protocol.agent.agents.verify.FlowVerifyAgent
import ai.koog.protocol.transition.FlowTransition
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlin.error

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
                val node by convertFlowAgentToKoogNode(agent, agents, toolRegistry)
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
            createEdgeToFinish(fromNode, transition.condition, transition.transformation)
            return
        }
        val toNode = collectedNodes.find { it.name == transition.to }
            ?: error("Unable to find 'to' node for transition '${transition.transitionString}': ${transition.to}")

        createEdge(fromNode, toNode, transition.condition, transition.transformation)
    }

    private fun AIAgentSubgraphBuilderBase<FlowDataType, FlowDataType>.createEdge(
        fromNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        toNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        condition: FlowTransitionCondition?,
        transformation: FlowDataTransformation?,
    ) {
        when {
            // No condition, no transformation - simple forward
            condition == null && transformation == null -> {
                edge(fromNode forwardTo toNode)
            }
            // Only transformation, no condition
            condition == null && transformation != null -> {
                edge(
                    fromNode forwardTo toNode transformed { output ->
                        transformFlowDataType(output, listOf(transformation))
                    }
                )
            }
            // Only condition, no transformation
            condition != null && transformation == null -> {
                edge(
                    fromNode forwardTo toNode onCondition { output ->
                        evaluateCondition(output, condition)
                    }
                )
            }
            // Both condition and transformation
            else -> {
                edge(
                    fromNode forwardTo toNode onCondition { output ->
                        evaluateCondition(output, condition!!)
                    } transformed { output ->
                        transformFlowDataType(output, listOf(transformation!!))
                    }
                )
            }
        }
    }

    /**
     * Creates an edge from a node to the finish node, optionally with a condition and transformation.
     */
    private fun AIAgentSubgraphBuilderBase<FlowDataType, FlowDataType>.createEdgeToFinish(
        fromNode: AIAgentNodeBase<FlowDataType, FlowDataType>,
        condition: FlowTransitionCondition?,
        transformation: FlowDataTransformation? = null
    ) = createEdge(fromNode, nodeFinish, condition, transformation)

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
            // Get the child agents that should run in parallel
            val parallelAgents = agents
                .filter { flowAgent -> flowAgent.name in agent.parameters.agents }
                .map { flowAgent ->
                    val nodeDelegate = convertFlowAgentToKoogNode(flowAgent, agents, toolRegistry)
                    val node by nodeDelegate
                    node
                }
                .toTypedArray()

            // Create a parallel node with merge logic
            val nodeParallel by parallel(
                name = agent.name,
                nodes = parallelAgents,
                merge = {
                    // Evaluate the merge condition to select the appropriate result
                    val selectedOutput = evaluateMergeCondition(results, agent.parameters.merge)

                    ParallelNodeExecutionResult(
                        selectedOutput,
                        results.first().nodeResult.context
                    )
                }
            )

            // Connect the nodes: start -> parallel -> finish
            nodeStart then nodeParallel then nodeFinish
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
        val conditionVariable = extractOutputValue(outputString = condition.variable)

        // Extract the value from the output based on the property path
        val outputValue: Comparable<*> = when (output) {
            is FlowDataType.FlowCritiqueResult -> {
                // Special handling for FlowCritiqueResult with nested properties
                when (conditionVariable) {
                    "success" -> output.success
                    "feedback" -> output.feedback
                    else -> error("Unsupported condition variable: $conditionVariable")
                }
            }
            else -> {
                // For primitive types, extract the value directly using a shared helper
                extractPrimitiveValue(output)
            }
        }

        // Extract condition value using a shared helper
        val conditionValue = extractPrimitiveValue(condition.value)

        // Use shared comparison logic
        return compareValues(outputValue, conditionValue, condition.operation)
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
        transformations: List<FlowDataTransformation>
    ): FlowDataType {
        if (transformations.isEmpty()) {
            return input
        }

        val transformation = transformations.singleOrNull()?.value
            ?: error("Unsupported transformation configuration")

        // Parse the transformation string (e.g., "output.success" or "input.feedback")
        val parts = transformation.split(".")

        if (parts.size != 2) {
            error("Expected format for transformation: '<input|output>.<variable>', got: $transformation")
        }

        val keyword = parts[0]
        val valueString = parts[1]

        // Validate keyword (support both "input" and "output")
        if (keyword != "input" && keyword != "output") {
            error("Expected 'input' or 'output' keyword for transformation, got: $keyword")
        }

        if (valueString.isBlank()) {
            return input
        }

        return when (valueString) {
            "success" -> {
                val value = (input as? FlowDataType.FlowCritiqueResult)?.success
                    ?: error("Cannot extract property '$valueString' from ${input::class.simpleName}")

                FlowDataType.FlowBoolean(value)
            }
            "feedback" -> {
                val value = (input as? FlowDataType.FlowCritiqueResult)?.feedback
                    ?: error("Cannot extract property '$valueString' from ${input::class.simpleName}")

                FlowDataType.FlowString(value)
            }
            else -> error("Property '$valueString' is not yet supported for transformation")
        }
    }

    /**
     * Evaluates a merge condition for parallel execution results.
     *
     * Parses the variable notation (e.g., "results.1.output") to:
     * 1. Extract the index and property path
     * 2. Navigate to the specified result and property
     * 3. Extract the primitive value for comparison
     * 4. Return the full FlowDataType output if the condition matches
     *
     * @param results The list of parallel execution results
     * @param condition The merge condition to evaluate
     * @return The FlowDataType output from the matching result
     * @throws IllegalArgumentException if the condition format is invalid or no match is found
     */
    private fun evaluateMergeCondition(
        results: List<ParallelResult<*, *>>,
        condition: ParallelMergeCondition
    ): FlowDataType {
        // Parse the variable: "results.1.output" -> ["results", "1", "output"]
        val parts = condition.variable.split(".")

        if (parts.size != 3) {
            error("Expected format for merge condition variable: 'results.<index>.<property>', got: ${condition.variable}")
        }

        if (parts[0] != "results") {
            error("Expected 'results' keyword for accessing parallel results: 'results.<index>.<property>', got: ${condition.variable}")
        }

        val index = parts[1].toIntOrNull()
            ?: error("Invalid index in merge condition variable: ${parts[1]}")

        if (index < 0 || index >= results.size) {
            error("Index out of bounds in merge condition: $index (available: 0..${results.size - 1})")
        }

        val property: String = parts[2]

        // Take the Koog parallel nodes execution result by index
        val result = results[index]

        // Extract the value from an actual Koog parallel nodes result based on the property name.
        val flowDataType = extractPropertyFromParallelResult(result, property)
            ?: error("Failed to get an actual parallel nodes execution result by the property (index: $index, property: $property)")

        // Extract the primitive value for condition matching
        val primitiveValue = extractPrimitiveValue(flowDataType)

        // Extract condition value
        val conditionValue = extractPrimitiveValue(condition.value)

        val isMatches = compareValues(primitiveValue, conditionValue, condition.operation)

        if (!isMatches) {
            error("Merge condition not satisfied (condition: ${condition.variable} ${condition.operation} $conditionValue)")
        }

        // Return the full output FlowDataType from the result
        return result.nodeResult.output as FlowDataType
    }

    /**
     * Extracts a property from a parallel execution result.
     *
     * @param result The parallel execution result
     * @param property The property name to extract (output, input, or name)
     * @return The FlowDataType representing the requested property
     */
    private fun extractPropertyFromParallelResult(
        result: ParallelResult<*, *>,
        property: String,
    ): FlowDataType? {
        return when (property) {
            "output" -> result.nodeResult.output as? FlowDataType
            "input" -> result.nodeInput as? FlowDataType
            "name" -> FlowDataType.FlowString(result.nodeName)
            else -> error("Unsupported property in merge condition: $property (supported values: 'output', 'input', 'name')")
        }
    }

    /**
     * Extracts a primitive comparable value from a FlowDataType.
     *
     * @param flowDataType The FlowDataType to extract from
     * @return The primitive value (Boolean, Int, Double, or String)
     */
    private fun extractPrimitiveValue(flowDataType: FlowDataType): Comparable<*> {
        return when (flowDataType) {
            is FlowDataType.FlowBoolean -> flowDataType.data
            is FlowDataType.FlowInteger -> flowDataType.data
            is FlowDataType.FlowDouble -> flowDataType.data
            is FlowDataType.FlowString -> flowDataType.data
            else -> error("Cannot extract primitive value from FlowDataType: ${flowDataType::class.simpleName}")
        }
    }

    /**
     * Compares two primitive values using the specified operation.
     *
     * @param value1 The first value to compare
     * @param value2 The second value to compare
     * @param operation The comparison operation to perform
     * @return true if the comparison is satisfied, false otherwise
     */
    private fun compareValues(
        value1: Comparable<*>,
        value2: Comparable<*>,
        operation: ConditionOperationKind
    ): Boolean {
        return when (operation) {
            ConditionOperationKind.EQUALS -> value1 == value2
            ConditionOperationKind.NOT_EQUALS -> value1 != value2
            ConditionOperationKind.MORE -> {
                when {
                    value1 is Number && value2 is Number ->
                        value1.toDouble() > value2.toDouble()
                    value1 is String && value2 is String ->
                        value1.compareTo(value2, ignoreCase = true) > 0
                    else -> false
                }
            }
            ConditionOperationKind.LESS -> {
                when {
                    value1 is Number && value2 is Number ->
                        value1.toDouble() < value2.toDouble()
                    value1 is String && value2 is String ->
                        value1.compareTo(value2, ignoreCase = true) < 0
                    else -> false
                }
            }
            ConditionOperationKind.MORE_OR_EQUAL -> {
                when {
                    value1 is Number && value2 is Number ->
                        value1.toDouble() >= value2.toDouble()
                    value1 is String && value2 is String ->
                        value1.compareTo(value2, ignoreCase = true) >= 0
                    else -> false
                }
            }
            ConditionOperationKind.LESS_OR_EQUAL -> {
                when {
                    value1 is Number && value2 is Number ->
                        value1.toDouble() <= value2.toDouble()
                    value1 is String && value2 is String ->
                        value1.compareTo(value2, ignoreCase = true) <= 0
                    else -> false
                }
            }
            ConditionOperationKind.NOT -> {
                when {
                    value1 is Boolean && value2 is Boolean -> value1 != value2
                    else -> false
                }
            }
            ConditionOperationKind.AND -> {
                when {
                    value1 is Boolean && value2 is Boolean -> value1 && value2
                    else -> false
                }
            }
            ConditionOperationKind.OR -> {
                when {
                    value1 is Boolean && value2 is Boolean -> value1 || value2
                    else -> false
                }
            }
        }
    }

    //endregion Private Methods
}
