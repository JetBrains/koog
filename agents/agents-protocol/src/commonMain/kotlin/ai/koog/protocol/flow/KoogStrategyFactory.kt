package ai.koog.protocol.flow

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
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
import ai.koog.protocol.agent.FlowAgentInput
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
        defaultModel: String?
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        // No agents - create an empty strategy
        if (agents.isEmpty()) {
            return createEmptyStrategy(id)
        }

        // No transitions - chain agents sequentially
        if (transitions.isEmpty()) {
            return createSequentialStrategy(id, agents, toolRegistry, defaultModel)
        }

        return strategy(id) {
            // Nodes
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, toolRegistry, defaultModel)
                node
            }

            val firstAgentName = FlowUtil.getFirstAgent(agents, transitions).name
            val firstNode = collectedNodes.find { it.name == firstAgentName }
                ?: error("First agent not found: $firstAgentName")

            // Edges
            // Connect the koog system start node to the first flow node
            edge(nodeStart forwardTo firstNode)

            // Process the rest of transitions and create edges
            transitions.forEach { transition -> transitionToEdge(collectedNodes, transition) }

            // Connect all agents without outgoing transitions to finish
            val agentsWithOutgoingTransitions = transitions.map { it.from }.toSet()
            val nodesWithoutFinish = collectedNodes.filter { node ->
                node.name !in agentsWithOutgoingTransitions
            }

            nodesWithoutFinish.forEach { nodeWithoutFinish ->
                createEdgeToFinish(nodeWithoutFinish, null)
            }
        }
    }

    //region Private Methods

    //region Edges

    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.transitionToEdge(
        collectedNodes: List<AIAgentNodeBase<FlowAgentInput, FlowAgentInput>>,
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

    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.createEdge(
        fromNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
        toNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
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
    private fun AIAgentSubgraphBuilderBase<FlowAgentInput, FlowAgentInput>.createEdgeToFinish(
        fromNode: AIAgentNodeBase<FlowAgentInput, FlowAgentInput>,
        condition: FlowTransitionCondition?
    ) = createEdge(fromNode, nodeFinish, condition)

    //endregion Edges

    //region Nodes

    /**
     * Converts a flow agent into Koog node delegate for a given flow agent type.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.convertFlowAgentToKoogNode(
        agent: FlowAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return when (agent) {
            is FlowTaskAgent -> nodeTask(agent, toolRegistry, defaultModel)
            is FlowVerifyAgent -> nodeVerify(agent, toolRegistry, defaultModel)
            is FlowInputTransformAgent -> nodeTransform(agent)
            is FlowReActAgent -> nodeReAct(agent, toolRegistry, defaultModel)
            else -> error("Parallel agent type is not yet supported")
        }
    }

    //endregion Nodes

    //region Task

    /**
     * Creates a task node which is executed as a subgraphWithTask strategy.
     * Input and output types for flow agent are preserved in the subgraphWithTask strategy.
     *
     * @param agent The flow agent to execute a particular task;
     * @param toolRegistry The tool registry to use for tool selection;
     * @param defaultModel The default model to use if not specified in the agent configuration.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTask(
        agent: FlowTaskAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?,
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model, defaultModel)
        return subgraphWithTask<FlowAgentInput, FlowAgentInput>(
            name = agent.name,
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) { input ->
            agent.parameters.task
        }
    }

    //endregion Task

    //region ReAct

    /**
     * Creates a subgraph with ReAct strategy.
     * The subgraph takes the flow agent input type [FlowAgentInput],
     * converts it into the [reActStrategy] input type [String], and return an expected agent flow type [FlowAgentInput].
     *
     * @param agent The flow agent configuration;
     * @param toolRegistry The tool registry for selecting tools;
     * @param defaultModel The default model to use if not specified in the agent configuration.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeReAct(
        agent: FlowReActAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?,
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model, defaultModel)
        return subgraph(
            name = agent.name,
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) {
            // Node to transform the custom [FlowAgentInput] type into the reAct strategy input of type [String]
            val prepareReActInput by node<FlowAgentInput, String> { input: FlowAgentInput ->
                agent.parameters.task
            }

            // Use the reActStrategy as a nested subgraph
            val reactSubgraph = reActStrategy(
                reasoningInterval = agent.parameters.reasoningInterval,
                name = "${agent.name}_react_strategy"
            )

            // Node to wrap the output from the ReAct strategy back to [FlowAgentInput] flow agent type
            // to send it further into the agent flow.
            val wrapOutput by node<String, FlowAgentInput> { output ->
                FlowAgentInput.InputString(output)
            }

            edge(nodeStart forwardTo prepareReActInput)
            edge(prepareReActInput forwardTo reactSubgraph)
            edge(reactSubgraph forwardTo wrapOutput)
            edge(wrapOutput forwardTo nodeFinish)
        }
    }

    //endregion ReAct

    //region Verify

    /**
     * Creates a node that checks/validates using LLM with structured verification output.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeVerify(
        agent: FlowVerifyAgent,
        toolRegistry: ToolRegistry,
        defaultModel: String?,
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        val resolvedModel = KoogPromptExecutorFactory.resolveModel(agent.model, defaultModel)
        val verifySubgraph by subgraphWithVerification<FlowAgentInput>(
            toolSelectionStrategy = toolRegistry.defineToolSelectionStrategy(toolNames = agent.parameters.toolNames),
            llmModel = resolvedModel,
        ) { input ->
            agent.parameters.task
        }

        return subgraph(name = agent.name) {
            val transformResult by node<CriticResult<FlowAgentInput>, FlowAgentInput> { result ->
                FlowAgentInput.InputCritiqueResult(
                    success = result.successful,
                    feedback = result.feedback,
                    input = result.input
                )
            }

            nodeStart then verifySubgraph then transformResult then nodeFinish
        }
    }

    //endregion Verify

    //region Transformation

    /**
     * Creates a transform node that applies transformations without LLM.
     * The transformation converts input from one FlowAgentInput type to another based on defined rules.
     */
    private fun AIAgentSubgraphBuilderBase<*, *>.nodeTransform(
        agent: FlowInputTransformAgent
    ): AIAgentSubgraphDelegate<FlowAgentInput, FlowAgentInput> {
        return subgraph(name = agent.name) {
            val transform by node<FlowAgentInput, FlowAgentInput> { runtimeInput ->
                transformFlowAgentInput(runtimeInput, agent.parameters.transformations)
            }

            nodeStart then transform then nodeFinish
        }
    }

    /**
     * Transforms a FlowAgentInput based on the provided transformation configuration.
     *
     * @param input The input to transform
     * @param transformations The list of transformations to apply.
     *
     * @return Transformed input, or original input if no matching transformation found
     */
    private fun transformFlowAgentInput(
        input: FlowAgentInput,
        transformations: List<FlowInputTransformation>
    ): FlowAgentInput {
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
                val value = (input as? FlowAgentInput.InputCritiqueResult)?.success
                    ?: error("Unexpected value string: $valueString")

                FlowAgentInput.InputBoolean(value)
            }
            "feedback" -> {
                val value = (input as? FlowAgentInput.InputCritiqueResult)?.feedback
                    ?: error("Unexpected value string: $valueString")

                FlowAgentInput.InputString(value)
            }
            else -> error("Not primitive types are not yet supported")
        }
    }

    //endregion Transformation

    //region Strategy

    /**
     * Creates an empty strategy that immediately finishes.
     */
    private fun createEmptyStrategy(id: String): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        return strategy(id) {
            edge(nodeStart forwardTo nodeFinish)
        }
    }

    /**
     * Creates a sequential strategy that chains all agents one after another.
     * Agents are connected in order: start → agent1 → agent2 → ... → finish
     */
    private fun createSequentialStrategy(
        id: String,
        agents: List<FlowAgent>,
        toolRegistry: ToolRegistry,
        defaultModel: String?
    ): AIAgentGraphStrategy<FlowAgentInput, FlowAgentInput> {
        return strategy(id) {
            val collectedNodes = agents.map { agent ->
                val node by convertFlowAgentToKoogNode(agent, toolRegistry, defaultModel)
                node
            }

            // Chain: start → first agent
            edge(nodeStart forwardTo collectedNodes.first())

            // Chain agents sequentially: agent[i] → agent[i+1]
            collectedNodes.zipWithNext { current, next ->
                edge(current forwardTo next)
            }

            // Chain: last agent → finish
            edge(collectedNodes.last() forwardTo nodeFinish)
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
    private fun evaluateCondition(output: FlowAgentInput, condition: FlowTransitionCondition): Boolean {
        // Extract value from output based on the variable name

        // String / Boolean / Double / Int vs. String / Boolean / Double / Int
        val conditionValue: Comparable<*> = when (condition.value) {
            is FlowAgentInput.InputBoolean -> condition.value.data
            is FlowAgentInput.InputInt -> condition.value.data
            is FlowAgentInput.InputDouble -> condition.value.data
            is FlowAgentInput.InputString -> condition.value.data
            else -> error("Unsupported condition type ${condition.value}")
        }

        // Drop the "input." prefix from the condition variable
        val conditionVariable = condition.variable.split(".").lastOrNull() ?: ""

        // TODO: Add careful validation for conditionVariable
        val outputValue: Comparable<*> = when (output) {
            is FlowAgentInput.InputBoolean -> output.data
            is FlowAgentInput.InputInt -> output.data
            is FlowAgentInput.InputDouble -> output.data
            is FlowAgentInput.InputString -> output.data
            is FlowAgentInput.InputCritiqueResult -> {
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

    //endregion Private Methods
}
