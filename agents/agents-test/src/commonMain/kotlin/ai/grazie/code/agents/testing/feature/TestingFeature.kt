package ai.grazie.code.agents.testing.feature

import ai.grazie.code.agents.core.agent.AIAgentBase.FeatureContext
import ai.grazie.code.agents.core.agent.entity.LocalAgentStateManager
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorage
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.createStorageKey
import ai.grazie.code.agents.core.agent.entity.FinishNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentLLMContext
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStage
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.dsl.builder.BaseBuilder
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.feature.AgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.code.agents.core.feature.PromptExecutorProxy
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.testing.tools.MockEnvironment
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.message.Message
import org.jetbrains.annotations.TestOnly


class DummyAgentStageContext(
    private val builder: LocalAgentStageContextMockBuilder,
) : LocalAgentStageContext {
    val isLLMDefined = builder.llm != null
    val isEnvironmentDefined = builder.environment != null

    override val environment: AgentEnvironment
        get() = builder.environment
            ?: throw NotImplementedError("Environment is not mocked")

    override val stageInput: String
        get() = builder.stageInput
            ?: throw NotImplementedError("Stage input is not mocked")

    override val config: LocalAgentConfig
        get() = builder.config
            ?: throw NotImplementedError("Config is not mocked")

    override val llm: LocalAgentLLMContext
        get() = builder.llm
            ?: throw NotImplementedError("LLM is not mocked")

    override val stateManager: LocalAgentStateManager
        get() = builder.stateManager
            ?: throw NotImplementedError("State manager is not mocked")

    override val storage: LocalAgentStorage
        get() = builder.storage
            ?: throw NotImplementedError("Storage is not mocked")

    override val sessionUuid: UUID
        get() = builder.sessionUuid
            ?: throw NotImplementedError("Session UUID is not mocked")

    override val strategyId: String
        get() = builder.strategyId
            ?: throw NotImplementedError("Strategy ID is not mocked")

    override val stageName: String
        get() = builder.stageName
            ?: throw NotImplementedError("Stage name is not mocked")

    /**
     * @suppress
     */
    @InternalAgentsApi
    override val pipeline: AgentPipeline = AgentPipeline()

    override fun <Feature : Any> feature(key: LocalAgentStorageKey<Feature>): Feature? =
        throw NotImplementedError("feature() getting in runtime is not supported for mock")

    override fun <Feature : Any> feature(feature: KotlinAIAgentFeature<*, Feature>): Feature? =
        throw NotImplementedError("feature()  getting in runtime is not supported for mock")

    override fun copy(
        environment: AgentEnvironment?,
        stageInput: String?,
        config: LocalAgentConfig?,
        llm: LocalAgentLLMContext?,
        stateManager: LocalAgentStateManager?,
        storage: LocalAgentStorage?,
        sessionUuid: UUID?,
        strategyId: String?,
        stageName: String?,
        pipeline: AgentPipeline?
    ): LocalAgentStageContext = DummyAgentStageContext(
        builder.copy().apply {
            environment?.let { this.environment = it }
            stageInput?.let { this.stageInput = it }
            config?.let { this.config = it }
            llm?.let { this.llm = it }
            stateManager?.let { this.stateManager = it }
            storage?.let { this.storage = it }
            sessionUuid?.let { this.sessionUuid = it }
            strategyId?.let { this.strategyId = it }
        }
    )
}

@TestOnly
interface LocalAgentStageContextMockBuilderBase : BaseBuilder<LocalAgentStageContext> {
    var environment: AgentEnvironment?
    var stageInput: String?
    var config: LocalAgentConfig?
    var llm: LocalAgentLLMContext?
    var stateManager: LocalAgentStateManager?
    var storage: LocalAgentStorage?
    var sessionUuid: UUID?
    var strategyId: String?
    var stageName: String?

    fun copy(): LocalAgentStageContextMockBuilderBase

    override fun build(): LocalAgentStageContext
}

@TestOnly
class LocalAgentStageContextMockBuilder() : LocalAgentStageContextMockBuilderBase {
    override var environment: AgentEnvironment? = null
    override var stageInput: String? = null
    override var config: LocalAgentConfig? = null
    override var llm: LocalAgentLLMContext? = null
    override var stateManager: LocalAgentStateManager? = null
    override var storage: LocalAgentStorage? = null
    override var sessionUuid: UUID? = null
    override var strategyId: String? = null
    override var stageName: String? = null

    override fun copy(): LocalAgentStageContextMockBuilder {
        return LocalAgentStageContextMockBuilder().also {
            it.environment = environment
            it.stageInput = stageInput
            it.config = config
            it.llm = llm
            it.stateManager = stateManager
            it.storage = storage
            it.sessionUuid = sessionUuid
            it.strategyId = strategyId
        }
    }

    override fun build(): DummyAgentStageContext {
        return DummyAgentStageContext(this.copy())
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T : Any> createDummyProxy(name: String): T {
            return ProxyHandler<T>(name).createProxy()
        }
    }

    class ProxyHandler<T : Any>(private val name: String) {
        @Suppress("UNCHECKED_CAST")
        fun createProxy(): T {
            return object : Any() {
                override fun toString() = "DummyProxy<${name}>"

                override fun equals(other: Any?): Boolean {
                    return this === other
                }

                @Suppress("UNUSED_PARAMETER")
                operator fun get(propertyName: String): Any? {
                    error("Unimplemented property access: $name.$propertyName")
                }

                @Suppress("UNUSED_PARAMETER")
                fun invoke(methodName: String, vararg args: Any?): Any? {
                    error("Unimplemented method call: $name.$methodName(${args.joinToString()})")
                }
            } as T
        }
    }
}


@TestOnly
sealed class NodeReference<Input, Output> {
    abstract fun resolve(stage: LocalAgentStage): LocalAgentNode<Input, Output>

    object Start : NodeReference<Unit, Unit>() {
        override fun resolve(stage: LocalAgentStage) = stage.start
    }

    object Finish : NodeReference<String, String>() {
        override fun resolve(stage: LocalAgentStage) = stage.finish
    }

    class NamedNode<Input, Output>(val name: String) : NodeReference<Input, Output>() {
        override fun resolve(stage: LocalAgentStage): LocalAgentNode<Input, Output> {
            val visited = mutableSetOf<String>()
            fun visit(node: LocalAgentNode<*, *>): LocalAgentNode<Input, Output>? {
                if (node is FinishNode) return null
                if (visited.contains(node.name)) return null
                visited.add(node.name)
                if (node.name == name) return node as? LocalAgentNode<Input, Output>
                return node.edges.firstNotNullOfOrNull { visit(it.toNode) }
            }

            val result = visit(stage.start)
                ?: throw IllegalArgumentException("Node with name '$name' not found in stage ${stage.name}")

            return result
        }
    }
}

@TestOnly
internal data class StageAssertions(
    val name: String,
    val start: NodeReference.Start,
    val finish: NodeReference.Finish,
    val nodes: Map<String, NodeReference<*, *>>,
    val nodeOutputAssertions: List<NodeOutputAssertion<*, *>>,
    val edgeAssertions: List<EdgeAssertion<*, *>>,
    val reachabilityAssertions: List<ReachabilityAssertion>
)

@TestOnly
data class NodeOutputAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: DummyAgentStageContext,
    val input: Input,
    val expectedOutput: Output
)

@TestOnly
data class EdgeAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: LocalAgentStageContext,
    val output: Output,
    val expectedNode: NodeReference<*, *>
)

@TestOnly
data class ReachabilityAssertion(val from: NodeReference<*, *>, val to: NodeReference<*, *>)

@TestOnly
sealed interface AssertionResult {
    class NotEqual(val expected: Any?, val actual: Any?, val message: String) : AssertionResult
    class False(val message: String) : AssertionResult
}

/**
 * Provides functionality for testing graph-related stages in an AI agent pipeline.
 *
 * This feature allows you to configure and validate the relationships between nodes,
 * their outputs, and the overall graph structure within different stages of an agent.
 * It can validate:
 * - Stage order
 * - Node existence and reachability
 * - Node input/output behavior
 * - Edge connections between nodes
 *
 * The Testing feature is designed to be used with the [testGraph] function, which provides
 * a clean API for defining and executing graph tests.
 *
 * Example usage:
 * ```kotlin
 * KotlinAIAgent(
 *     // constructor arguments
 * ) {
 *     testGraph {
 *         // Assert the order of stages
 *         assertStagesOrder("first", "second")
 *
 *         // Test the first stage
 *         stage("first") {
 *             val start = startNode()
 *             val finish = finishNode()
 *
 *             // Assert nodes by name
 *             val askLLM = assertNodeByName<String, Message.Response>("callLLM")
 *             val callTool = assertNodeByName<ToolCall.Signature, ToolCall.Result>("executeTool")
 *
 *             // Assert node reachability
 *             assertReachable(start, askLLM)
 *             assertReachable(askLLM, callTool)
 *
 *             // Test node behavior
 *             assertNodes {
 *                 askLLM withInput "Hello" outputs Message.Assistant("Hello!")
 *             }
 *
 *             // Test edge connections
 *             assertEdges {
 *                 askLLM withOutput Message.Assistant("Hello!") goesTo giveFeedback
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @see testGraph
 * @see Testing.Config
 */
@TestOnly
class Testing {
    /**
     * A mutable list storing `StageAssertions` objects, which define validation
     * criteria for individual stages in the graph testing process.
     *
     * Each `StageAssertions` object contains information about:
     * - The stage name
     * - References to start and finish nodes
     * - Named node references
     * - Node output assertions
     * - Edge assertions
     * - Reachability assertions
     *
     * This list is populated during the configuration phase and used during
     * the validation phase to verify the agent's graph structure.
     */
    private val stages = mutableListOf<StageAssertions>()

    /**
     * Represents an optional list of stage names indicating the desired order in which stages
     * should be processed.
     *
     * When set, this list is used to verify that the agent's stages are defined in the
     * expected order. This is useful for ensuring that the agent's workflow follows
     * a specific sequence of stages.
     *
     * Null indicates that no specific order is defined, allowing stages to be handled
     * without restriction.
     */
    private var stagesOrder: List<String>? = null

    /**
     * Represents a configuration class responsible for managing assertion handlers and stage configurations.
     * It includes methods for registering custom assertion handling, managing stages and their order,
     * and defining stage-specific assertions.
     */
    class Config : FeatureConfig() {
        /**
         * A configuration flag that determines whether graph-related testing features are enabled.
         *
         * When set to `true`, additional testing mechanisms related to graph validation,
         * assertions, or structure evaluation may be activated within the system.
         * Default value is `false`, which disables graph testing functionalities.
         */
        var enableGraphTesting = false

        /**
         * A nullable variable that defines the handler invoked when an assertion result is generated.
         * It is a higher-order function that accepts an `AssertionResult` as its parameter.
         * This handler is used internally in assertion methods to process and handle assertion outcomes such as
         * mismatches between expected and actual values or boolean evaluation failures.
         */
        private var assertionHandler: ((AssertionResult) -> Unit)? = null

        /**
         * A mutable list of `StageAssertions` objects used to collect and manage stage-related assertions.
         *
         * This property is primarily utilized to store the assertions defined for individual stages in the assertion configuration.
         * It allows the sequential addition of stage assertions through various methods in the `Config` class.
         *
         * Each element in the list represents a specific stage with its associated assertions, such as node outputs,
         * edge connections, and reachability between nodes.
         */
        private val stages = mutableListOf<StageAssertions>()

        /**
         * Represents the custom order of stages to be used for assertions or sequence validation.
         * The list can be null, indicating that no specific order has been defined.
         */
        private var stagesOrder: List<String>? = null

        /**
         * Asserts that two values are equal. If the values are not equal, the specified message will be passed
         * to the assertion handler with details about the expected and actual values.
         *
         * @param expected The expected value to compare against.
         * @param actual The actual value to be compared.
         * @param message A custom message to include when the assertion fails.
         */
        internal fun assertEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                assertionHandler?.invoke(AssertionResult.NotEqual(expected, actual, message))
            }
        }

        /**
         * Asserts the truthfulness of the provided boolean value.
         * If the assertion fails (i.e., the value is false), a custom handler is invoked
         * with an `AssertionResult.False` containing the provided message.
         *
         * @param value the boolean value to evaluate. If false, the assertion fails.
         * @param message the message to include in the assertion result if the value is false.
         */
        internal fun assert(value: Boolean, message: String) {
            if (!value) {
                assertionHandler?.invoke(AssertionResult.False(message))
            }
        }


        /**
         * Sets a custom handler for processing assertion results.
         *
         * @param block A lambda which takes an `AssertionResult` as input and processes it.
         *              This allows customization of how assertion results are handled,
         *              such as logging or throwing custom exceptions.
         */
        fun handleAssertion(block: (AssertionResult) -> Unit) {
            assertionHandler = block
        }

        /**
         * Sets the expected order of stages for validation.
         *
         * @param stages A variable number of stage names representing the expected order. Each stage is specified as a separate string argument.
         */
        fun assertStagesOrder(vararg stages: String) {
            stagesOrder = stages.toList()
        }

        /**
         * Defines a new stage within the configuration and allows adding assertions to it.
         *
         * @param name The name of the stage to be created.
         * @param function A lambda with a receiver of type `StageAssertionsBuilder` for defining stage-specific assertions.
         */
        fun stage(name: String, function: StageAssertionsBuilder.() -> Unit) {
            val stageBuilder = StageAssertionsBuilder(name)
            stageBuilder.function()
            stages.add(stageBuilder.build())
        }

        /**
         * Retrieves a list of stage configurations for assertion handling.
         *
         * This method provides the stages configured within the `Config` class, each represented
         * by a `StageAssertions` object containing detailed information about nodes, reachability,
         * and assertions related to a specific stage.
         *
         * @return A list of `StageAssertions` representing the configured stages.
         */
        internal fun getStages(): List<StageAssertions> = stages

        /**
         * Retrieves the list of stage names in the specified order, if defined.
         *
         * @return a list of stage names representing the order of stages, or null if no order is defined.
         */
        internal fun getStagesOrder(): List<String>? = stagesOrder

        /**
         * Builder class for constructing stage-level assertions.
         * This includes setup for nodes, edges, reachability assertions, and context-related mock setups.
         */
        class StageAssertionsBuilder(val name: String) {
            /**
             * Represents the starting node of the stage in the context of assertions.
             * This property holds a reference to the initial `Start` node, which is a singleton object of type `NodeReference.Start`.
             * It is used to identify and interact with the starting point in the graph-based structure of the stage.
             *
             * The `start` property is central to defining assertions and logical operations pertaining to the execution
             * flows that originate from the starting node. It serves as an entry point for other operations
             * like reachability checks or node input/output validations in the context of stage assertions.
             */
            private var start: NodeReference.Start = NodeReference.Start

            /**
             * Represents the finish node of the stage.
             * This variable is of type `NodeReference.Finish`, which defines the ending point of a stage in the node-based structure.
             * It can be used to declare or reference assertions relating to the finish node of the stage.
             */
            private var finish: NodeReference.Finish = NodeReference.Finish

            /**
             * Stores a mapping of node names to their corresponding references.
             *
             * This property serves as the central repository of named nodes within the
             * StageAssertionsBuilder. Nodes are registered within this map when they are
             * asserted by name using functions like `assertNodeByName`. The keys in the map
             * represent the names of the nodes, while the values represent their references.
             *
             * The `nodes` map is later used during the `build()` process to construct
             * `StageAssertions`, ensuring all nodes are properly accounted for and linked
             * according to their usage in the assertions.
             */
            private val nodes = mutableMapOf<String, NodeReference<*, *>>()

            /**
             * A mutable list that collects NodeOutputAssertion instances. These assertions are used to validate
             * the outputs of specific nodes within a stage of the system's execution.
             *
             * Each NodeOutputAssertion in this list represents an expectation on the output behavior of a
             * particular node when provided with a certain input in a predefined context.
             *
             * This property is populated indirectly through the `assertNodes` function within the
             * `StageAssertionsBuilder` context, allowing users to define node-specific assertions
             * and add them to this collection.
             *
             * The collected assertions are later utilized during the construction of the
             * `StageAssertions` object to verify node output behavior.
             */
            private val nodeOutputs = mutableListOf<NodeOutputAssertion<*, *>>()

            /**
             * A mutable list storing all edge-related assertions for a stage.
             *
             * Each edge assertion represents an expected edge between two nodes in the
             * stage, including the originating node, its output, the target node, and the
             * execution context in which the assertion was made.
             *
             * This property is utilized during the edge validation process to ensure the
             * stage conforms to its designed behavior regarding node-to-node transitions.
             * The assertions are collected through the `EdgeAssertionsBuilder` and
             * integrated into the final `StageAssertions` object for the stage.
             */
            private val edgeAssertions = mutableListOf<EdgeAssertion<*, *>>()

            /**
             * A mutable list of reachability assertions that define expected direct connections between nodes
             * in a stage. Each assertion represents a relationship where one node is reachable from another.
             *
             * This field is used to accumulate `ReachabilityAssertion` objects which are added via the
             * `assertReachable` method in the `StageAssertionsBuilder` class. These assertions are validated
             * when the stage's configuration is built using the `build` method.
             */
            private val reachabilityAssertions = mutableListOf<ReachabilityAssertion>()

            /**
             * Provides a mock builder for the local agent stage context used within test environments.
             * This mock can be leveraged to construct or replicate contexts for validating various stage behaviors
             * including node outputs, edge assertions, and reachability assertions.
             * It acts as a centralized resource for contextual test data and stage-related configurations.
             */
            private val context = LocalAgentStageContextMockBuilder()

            /**
             * Retrieves the starting node reference of the stage.
             *
             * @return The starting node reference of type NodeReference.Start, representing the entry point of the stage.
             */
            fun startNode(): NodeReference.Start {
                return start
            }

            /**
             * Retrieves a reference to the finish node of the current stage in the graph.
             *
             * @return a [NodeReference.Finish] representing the terminal node of the stage.
             */
            fun finishNode(): NodeReference.Finish {
                return finish
            }

            /**
             * Asserts the existence of a node by its name within the stage structure and returns a reference to it.
             * If the node with the given name has not been asserted previously, it creates a new `NamedNode` reference
             * and records it in the `nodes` map for tracking.
             *
             * @param name the name of the node to assert or retrieve.
             * @return a `NodeReference` for the node identified by the given name.
             */
            fun <I, O> assertNodeByName(name: String): NodeReference<I, O> {
                val nodeRef = NodeReference.NamedNode<I, O>(name)
                nodes[name] = nodeRef
                return nodeRef
            }

            /**
             * Asserts that there is a reachable path between two specified nodes within a stage.
             *
             * @param from The starting node reference from where the reachability is checked.
             * @param to The target node reference to which the reachability is checked.
             */
            fun assertReachable(from: NodeReference<*, *>, to: NodeReference<*, *>) {
                reachabilityAssertions.add(ReachabilityAssertion(from, to))
            }

            /**
             * Asserts the state of nodes in the stage using a provided block to define the desired assertions.
             * The block operates on a `NodeOutputAssertionsBuilder` to specify expectations for node inputs and outputs.
             *
             * @param block A lambda receiver operating on a `NodeOutputAssertionsBuilder` that defines the assertions
             *              to be applied to the nodes in the stage.
             */
            fun assertNodes(block: NodeOutputAssertionsBuilder.() -> Unit) {
                val builder = NodeOutputAssertionsBuilder(this)
                builder.block()
                nodeOutputs.addAll(builder.assertions)
            }

            /**
             * Asserts the edges in the context of a graph by applying a set of edge assertions built using the provided block.
             *
             * @param block A lambda function that operates on an instance of `EdgeAssertionsBuilder` to define specific edge assertions.
             */
            fun assertEdges(block: EdgeAssertionsBuilder.() -> Unit) {
                val builder = EdgeAssertionsBuilder(this)
                builder.block()
                edgeAssertions.addAll(builder.assertions)
            }

            /**
             * Builds and returns a `StageAssertions` object based on the current state of the `StageAssertionsBuilder`.
             *
             * @return A `StageAssertions` instance containing the name, start and finish node references,
             *         map of nodes, node output assertions, edge assertions, and reachability assertions.
             */
            internal fun build(): StageAssertions {
                return StageAssertions(
                    name,
                    start,
                    finish,
                    nodes,
                    nodeOutputs,
                    edgeAssertions,
                    reachabilityAssertions
                )
            }

            /**
             * A builder class for constructing and managing assertions related to the outputs of nodes within a stage.
             * This class provides functionality to define and evaluate assertions for node outputs in a local agent's stage.
             *
             * @property stageBuilder A reference to the parent StageAssertionsBuilder, which serves as the context for assertions.
             * @property context A mock builder for the local agent stage context, used to manage and copy state during the assertion process.
             */
            class NodeOutputAssertionsBuilder(
                private val stageBuilder: StageAssertionsBuilder,
                private val context: LocalAgentStageContextMockBuilder = stageBuilder.context.copy()
            ) : LocalAgentStageContextMockBuilderBase by context {

                /**
                 * Creates and returns a new copy of the NodeOutputAssertionsBuilder instance.
                 *
                 * @return a new NodeOutputAssertionsBuilder that contains a copy of the current stageBuilder
                 * and a copied context.
                 */
                override fun copy(): NodeOutputAssertionsBuilder =
                    NodeOutputAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * A mutable list that stores assertions representing the expected behavior and output of nodes
                 * in the context of a specific staging environment for testing purposes.
                 *
                 * Each assertion is an instance of [NodeOutputAssertion], which encapsulates information
                 * such as the node reference, the input provided, the expected output, and the context
                 * in which the node operates.
                 *
                 * These assertions are used to verify the correctness of node operations within the
                 * local agent stage context during testing.
                 */
                val assertions = mutableListOf<NodeOutputAssertion<*, *>>()

                /**
                 * Executes the specified block of code within a duplicate context of the current `NodeOutputAssertionsBuilder`.
                 *
                 * @param block The block of code to be executed within the duplicated context of `NodeOutputAssertionsBuilder`.
                 */
                fun withContext(block: NodeOutputAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the provided input with the current node reference, creating a pair that links the node
                 * to its corresponding input.
                 *
                 * @param input The input value to associate with the node reference.
                 * @return A `NodeOutputPair` containing the node reference and the provided input.
                 */
                infix fun <I, O> NodeReference<I, O>.withInput(input: I): NodeOutputPair<I, O> {
                    return NodeOutputPair(this, input)
                }

                /**
                 * Represents a pairing of a specific node reference and its corresponding input.
                 * This is used to define the expected output for a given input within the context of a specific node.
                 *
                 * @param I The type of the input for the node.
                 * @param O The type of the output for the node.
                 * @property node The reference to the specific node.
                 * @property input The input associated with the node.
                 */
                inner class NodeOutputPair<I, O>(val node: NodeReference<I, O>, val input: I) {
                    /**
                     * Asserts that the output of the current node given the specified input matches the expected output.
                     *
                     * @param output The expected output to validate against the current node's actual output.
                     */
                    infix fun outputs(output: O) {
                        assertions.add(NodeOutputAssertion(node, context.build(), input, output))
                    }
                }
            }

            /**
             * A builder class used to facilitate the creation and management of edge assertions in a stage context.
             * Delegates functionality to a local agent stage context mock builder for shared behaviors.
             *
             * @property stageBuilder The parent builder for the stage, used to initialize context and other related components.
             * @property context A local agent stage context mock builder, initialized as a copy of the stage builder's context.
             */
            class EdgeAssertionsBuilder(
                private val stageBuilder: StageAssertionsBuilder,
                private val context: LocalAgentStageContextMockBuilder = stageBuilder.context.copy()
            ) : LocalAgentStageContextMockBuilderBase by context {

                /**
                 * A mutable list that holds all the defined `EdgeAssertion` instances for the current context.
                 *
                 * `EdgeAssertion` represents the relationship between nodes in a graph-like structure, detailing
                 * the output of a source node and its expected connection to a target node. These assertions are
                 * used to validate the behavior and flow of nodes within an agent's stage context.
                 *
                 * This list is populated during the execution of the `EdgeAssertionsBuilder` block in methods
                 * that build or define edge assertions. Each assertion is added via the respective fluent APIs
                 * provided within the builder.
                 */
                val assertions = mutableListOf<EdgeAssertion<*, *>>()

                /**
                 * Creates a deep copy of the current EdgeAssertionsBuilder instance, duplicating its state and context.
                 *
                 * @return A new EdgeAssertionsBuilder instance with the same stageBuilder and a copied context.
                 */
                override fun copy(): EdgeAssertionsBuilder = EdgeAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * Executes a given block of logic within the context of a copied instance of the current `EdgeAssertionsBuilder`.
                 *
                 * @param block The block of code to execute within the context of the copied `EdgeAssertionsBuilder` instance.
                 */
                fun withContext(block: EdgeAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the given output with the current node reference, creating a pair that represents
                 * the node and its corresponding output.
                 *
                 * @param output the output value to associate with the current node reference
                 * @return an instance of EdgeOutputPair containing the current node reference and the associated output
                 */
                infix fun <I, O> NodeReference<I, O>.withOutput(output: O): EdgeOutputPair<I, O> {
                    return EdgeOutputPair(this, output)
                }

                /**
                 * Represents a pair consisting of a node and its corresponding output within an edge assertion context.
                 * This is used to define expected edge behavior in a graph of nodes.
                 *
                 * @param I the type of the input expected by the node.
                 * @param O the type of the output produced by the node.
                 * @property node the reference to the node associated with this edge output.
                 * @property output the output value associated with the node.
                 */
                inner class EdgeOutputPair<I, O>(val node: NodeReference<I, O>, val output: O) {
                    /**
                     * Creates an assertion to verify that a specific output from the current node leads to the given target node.
                     *
                     * @param targetNode The target node that the current node output is expected to connect to.
                     */
                    infix fun goesTo(targetNode: NodeReference<*, *>) {
                        assertions.add(EdgeAssertion(node, context.build(), output, targetNode))
                    }
                }
            }
        }
    }

    /**
     * Companion object that defines the `Testing` feature as a `KotlinAIAgentFeature`.
     * This feature provides testing capabilities for validating graph-based stages, nodes,
     * reachability, outputs, and edges within an AI agent pipeline.
     */
    @TestOnly
    companion object Feature : KotlinAIAgentFeature<Config, Testing> {
        /**
         * A storage key uniquely identifying the `Testing` feature within the local agent's storage.
         * The key is generated using the `createStorageKey` function and associates the
         * `Testing` feature type with its specific storage context.
         */
        override val key: LocalAgentStorageKey<Testing> = createStorageKey("graph-testing-feature")

        /**
         * Creates the initial configuration for the graph testing feature.
         *
         * @return an instance of [Config] containing the initial setup for assertions and stage configuration.
         */
        override fun createInitialConfig(): Config = Config()

        /**
         * Installs the `Testing` feature into the specified `AIAgentPipeline` with the provided configuration.
         * The feature primarily validates stages, nodes, and connectivity of the AI agent pipeline.
         *
         * @param config The `Config` object containing setup and assertions for testing the pipeline.
         * @param pipeline The `AIAgentPipeline` instance to install the feature into.
         */
        override fun install(
            config: Config,
            pipeline: AgentPipeline
        ) {
            val feature = Testing()

            pipeline.interceptEnvironmentCreated(this, feature) { agentEnvironment ->
                MockEnvironment(agent.toolRegistry, agent.promptExecutor, agentEnvironment)
            }

            if (config.enableGraphTesting) {
                feature.stages.addAll(config.getStages())
                feature.stagesOrder = config.getStagesOrder()

                pipeline.interceptAgentCreated(this, feature) {
                    readStages { stages ->
                        // Verify stages order if specified
                        feature.stagesOrder?.let { expectedOrder ->
                            val actualOrder = stages.map { it.name }
                            assertListEquals(
                                expectedOrder,
                                actualOrder,
                                "Stages order does not match expected order. Expected: $expectedOrder, Actual: $actualOrder"
                            )
                        }

                        // Process each stage
                        stages.forEach { stage ->
                            val stageConfig = feature.stages.find { it.name == stage.name }
                            if (stageConfig == null) {
                                // Skip stages that are not configured for testing
                                return@forEach
                            }

                            // Verify nodes exist
                            for ((nodeName, nodeRef) in stageConfig.nodes) {
                                val node = nodeRef.resolve(stage)
                                assertNotNull(node, "Node with name '$nodeName' not found in stage '${stage.name}'")
                            }

                            // Verify reachability
                            for (assertion in stageConfig.reachabilityAssertions) {
                                assertReachable(
                                    assertion.from.resolve(stage),
                                    assertion.to.resolve(stage),
                                    "Node ${assertion.to.resolve(stage).name} is not reachable from ${
                                        assertion.from.resolve(
                                            stage
                                        ).name
                                    } in stage '${stage.name}'"
                                )
                            }

                            // Verify node outputs using DFS
                            for (assertion in stageConfig.nodeOutputAssertions) {
                                val fromNode = assertion.node.resolve(stage)

                                val environment = if (assertion.context.isEnvironmentDefined)
                                    assertion.context.environment
                                else
                                    MockEnvironment(agent.toolRegistry, agent.promptExecutor)

                                val llm = if (assertion.context.isLLMDefined) {
                                    assertion.context.llm
                                } else {
                                    LocalAgentLLMContext(
                                        tools = agent.toolRegistry.stagesToolDescriptors[stage.name] ?: emptyList(),
                                        prompt = agent.agentConfig.prompt,
                                        model = agent.agentConfig.model,
                                        promptExecutor = PromptExecutorProxy(agent.promptExecutor, pipeline),
                                        environment = environment,
                                        config = agent.agentConfig
                                    )
                                }

                                @OptIn(InternalAgentsApi::class)
                                config.assertEquals(
                                    assertion.expectedOutput,
                                    fromNode.executeUnsafe(
                                        assertion.context.copy(llm = llm, environment = environment),
                                        assertion.input
                                    ),
                                    "Unexpected output for node ${fromNode.name} with input ${assertion.input} in stage '${stage.name}'"
                                )
                            }

                            @OptIn(InternalAgentsApi::class)
                            // Verify edges using DFS
                            for (assertion in stageConfig.edgeAssertions) {
                                val fromNode = assertion.node.resolve(stage)
                                val toNode = assertion.expectedNode.resolve(stage)

                                val resolvedEdge = fromNode.resolveEdgeUnsafe(assertion.context, assertion.output)
                                assertNotNull(
                                    resolvedEdge,
                                    "Expected to have at least one matching edge from node ${fromNode.name} with output ${assertion.output} "
                                )

                                config.assertEquals(
                                    toNode,
                                    resolvedEdge!!.edge.toNode,
                                    "Expected `${fromNode.name}` with output ${assertion.output} to go to `${toNode.name}`, " +
                                            "but it goes to ${resolvedEdge.edge.toNode.name} instead"
                                )
                            }
                        }
                    }
                }
            }
        }

        /**
         * Verifies whether there is a path from one node to another within a graph. If the target node
         * is not reachable from the starting node, an assertion error is thrown with the provided message.
         *
         * @param from The starting node in the graph from which to check reachability.
         * @param to The target node to verify reachability to.
         * @param message The error message to include in the assertion error if the target node is not reachable.
         */
        private fun assertReachable(from: LocalAgentNode<*, *>, to: LocalAgentNode<*, *>, message: String) {
            val visited = mutableSetOf<String>()

            fun dfs(node: LocalAgentNode<*, *>): Boolean {
                if (node == to) return true
                if (visited.contains(node.name)) return false

                visited.add(node.name)

                for (edge in node.edges) {
                    if (dfs(edge.toNode)) return true
                }

                return false
            }

            if (!dfs(from)) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the provided value is not null. If the value is null, an AssertionError is thrown with the specified message.
         *
         * @param value The value to check for nullity.
         * @param message The error message to include in the exception if the value is null.
         */
        private fun assertNotNull(value: Any?, message: String) {
            if (value == null) {
                throw AssertionError(message)
            }
        }

        /**
         * Compares two lists and throws an AssertionError if they are not equal, with a specified error message.
         *
         * @param expected The expected list of elements.
         * @param actual The actual list of elements to compare against the expected list.
         * @param message The message to include in the assertion error if the lists are not equal.
         */
        private fun assertListEquals(expected: List<*>, actual: List<*>, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the given two values are equal. If they are not equal, it throws an AssertionError
         * with the provided message.
         *
         * @param expected the expected value to compare
         * @param actual the actual value to compare against the expected value
         * @param message the assertion failure message to include in the exception if the values are not equal
         */
        private fun assertValueEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }
    }
}

/**
 * Creates a `Message.Tool.Call` instance for the given tool and its arguments.
 *
 * This utility function simplifies the creation of tool call messages for testing purposes.
 * It automatically handles the encoding of arguments into the appropriate string format.
 *
 * @param tool The tool to be represented in the call message. The `Tool` instance contains metadata
 *             such as the tool's name and utility methods for encoding/decoding arguments.
 * @param args The arguments to be passed to the tool. Must match the type `Args` defined by the tool.
 * @return A `Message.Tool.Call` object representing a call to the specified tool with the encoded arguments.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool call message for testing
 * val message = toolCallMessage(CreateTool, CreateTool.Args("solve"))
 *
 * // Use in node output assertions
 * assertNodes {
 *     askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
 * }
 * ```
 */
fun <Args : Tool.Args> toolCallMessage(tool: Tool<Args, *>, args: Args): Message.Tool.Call =
    Message.Tool.Call(null, tool.name, tool.encodeArgsToString(args))

/**
 * Generates a signature object for a given tool and its arguments.
 *
 * This utility function creates a tool call signature that can be used for testing
 * tool execution nodes. It's similar to [toolCallMessage] but is used specifically
 * in the context of testing tool execution.
 *
 * @param tool The tool for which the signature is being generated. This parameter must be an instance of Tool with the specified argument type.
 * @param args The arguments to be passed to the tool. These arguments must comply with the requirements specified by the tool.
 * @return A signature that encapsulates the tool's name and its encoded arguments.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool call signature for testing
 * val signature = toolCallSignature(SolveTool, SolveTool.Args("solve"))
 *
 * // Use in node input assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(SolveTool, SolveTool.Args("solve")) outputs toolResult(SolveTool, "solved")
 * }
 * ```
 */
fun <Args : Tool.Args> toolCallSignature(tool: Tool<Args, *>, args: Args): Message.Tool.Call =
    Message.Tool.Call(null, tool.name, tool.encodeArgsToString(args))

/**
 * Converts a tool and its corresponding result into a `ReceivedToolResult` object.
 *
 * This utility function simplifies the creation of tool results for testing purposes.
 * It automatically handles the encoding of the result into the appropriate string format.
 *
 * @param tool The tool whose result is being processed. The tool provides context for the result.
 * @param result The result produced by the tool, which will be encoded into a string representation.
 * @return A `ReceivedToolResult` instance containing the tool's name and the encoded string representation of the result.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool result for testing
 * val result = toolResult(AnalyzeTool, AnalyzeTool.Result("Detailed analysis", 0.95))
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(AnalyzeTool, AnalyzeTool.Args("analyze")) outputs result
 * }
 * ```
 */
fun <Result : ToolResult> toolResult(tool: Tool<*, Result>, result: Result): ReceivedToolResult =
    ReceivedToolResult(null, tool.name, tool.encodeResultToString(result), result)

/**
 * Constructs a `ReceivedToolResult` object using the provided tool and result string.
 *
 * This is a convenience function for simple tools that return text results.
 * It wraps the string result in a `ToolResult.Text` object.
 *
 * @param tool The tool for which the result is being created, of type `SimpleTool`.
 * @param result The result content generated by the tool execution as a string.
 * @return An instance of `ReceivedToolResult` containing the tool's name and the result string.
 *
 * Example usage:
 * ```kotlin
 * // Create a simple text tool result for testing
 * val result = toolResult(SolveTool, "solved")
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(SolveTool, SolveTool.Args("solve")) outputs result
 * }
 * ```
 */
fun toolResult(tool: SimpleTool<*>, result: String): ReceivedToolResult = toolResult(tool, ToolResult.Text(result))

/**
 * Enables and configures the Testing feature for a Kotlin AI Agent instance.
 *
 * This function installs the Testing feature with the specified configuration.
 * It's typically used within the agent constructor block to enable testing capabilities.
 *
 * @param config A lambda function to configure the Testing feature. The default is an empty configuration.
 *
 * Example usage:
 * ```kotlin
 * // Create an agent with testing enabled
 * KotlinAIAgent(
 *     promptExecutor = mockLLMApi,
 *     toolRegistry = toolRegistry,
 *     strategy = strategy,
 *     eventHandler = eventHandler,
 *     agentConfig = agentConfig,
 *     cs = this
 * ) {
 *     // Enable testing with custom configuration
 *     withTesting {
 *         enableGraphTesting = true
 *         handleAssertion { assertionResult ->
 *             // Custom assertion handling
 *         }
 *     }
 * }
 * ```
 *
 * @see Testing
 * @see Testing.Config
 */
suspend fun FeatureContext.withTesting(config: Testing.Config.() -> Unit = {}) {
    install(Testing) {
        config()
    }
}
