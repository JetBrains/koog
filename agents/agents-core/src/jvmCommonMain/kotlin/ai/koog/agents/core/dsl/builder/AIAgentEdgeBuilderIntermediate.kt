@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.dsl.builder

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.Option
import ai.koog.agents.core.utils.runOnStrategyDispatcher

/**
 * Represents an intermediate stage in the construction of a directed edge between two nodes
 * in an AI agent strategy graph. This class provides mechanisms to define conditions
 * and transformations that dictate how data flows and is processed between the nodes.
 *
 * @param IncomingOutput The type of the output data produced by the originating node.
 * @param IntermediateOutput The type of intermediate data produced after transformation or filtering.
 * @param OutgoingInput The type of input data that the destination node expects.
 * @constructor Creates an intermediate edge builder, defining the source and destination nodes
 * along with the transformation logic for the data flow between them.
 *
 * @property fromNode The originating node in the directed edge.
 * @property toNode The destination node in the directed edge.
 * @property forwardOutputComposition A suspending lambda function responsible for transforming
 * the originating node's output into an intermediate representation
 * or filtering the flow based on specific conditions.
 */
@EdgeTransformationDslMarker
public actual class AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> internal actual constructor(
    fromNode: AIAgentNodeBase<*, IncomingOutput>,
    toNode: AIAgentNodeBase<OutgoingInput, *>,
    forwardOutputComposition: suspend (AIAgentGraphContextBase, IncomingOutput) -> Option<IntermediateOutput>
) : AIAgentEdgeBuilderIntermediateBase<IncomingOutput, IntermediateOutput, OutgoingInput>(
    fromNode,
    toNode,
    forwardOutputComposition
) {
    /**
     * Filters the intermediate outputs of the [ai.koog.agents.core.agent.entity.AIAgentNode] based on a specified condition.
     *
     * @param block A lambda function that takes the AI agent's context ([AIAgentGraphContextBase]) and an intermediate output ([IntermediateOutput]) as parameters.
     *              It returns `true` if the given intermediate output satisfies the condition, and `false` otherwise.
     * @return A new instance of `AIAgentEdgeBuilderIntermediate` that includes only the filtered intermediate outputs
     *         satisfying the specified condition.
     */
    @JavaAPI
    @EdgeTransformationDslMarker
    @JvmName("onCondition")
    public fun javaNonSuspendOnCondition(
        block: ContextualCondition<IntermediateOutput>
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> {
        return AIAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                with(forwardOutputComposition(ctx, output)) {
                    ctx.config.runOnStrategyDispatcher {
                        filter { transOutput ->
                            block.invoke(transOutput, ctx)
                        }
                    }
                }
            },
        )
    }

    /**
     * Filters the intermediate outputs of the [AIAgentNode] based on a specified condition.
     *
     * @param block A lambda function that takes an intermediate output as a parameter.
     *              It returns `true` if the given intermediate output satisfies the condition, and `false` otherwise.
     * @return A new instance of [AIAgentEdgeBuilderIntermediate] that includes only the filtered intermediate outputs
     *         satisfying the specified condition.
     */
    @JavaAPI
    @EdgeTransformationDslMarker
    @JvmName("onCondition")
    public fun javaNonSuspendOnCondition(
        block: SimpleCondition<IntermediateOutput>
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, IntermediateOutput, OutgoingInput> =
        javaNonSuspendOnCondition { output, ctx ->
            block.invoke(output)
        }

    /**
     * Transforms the intermediate output of the [ai.koog.agents.core.agent.entity.AIAgentNode] by applying a given transformation block.
     *
     * @param block A lambda that defines the transformation to be applied to the intermediate output.
     *              It takes the AI agent's context and the intermediate output as parameters and returns a new intermediate output.
     * @return A new instance of `AIAgentEdgeBuilderIntermediate` with the transformed intermediate output type.
     */
    @EdgeTransformationDslMarker
    @JvmName("transformed")
    public infix fun <NewIntermediateOutput> javaNonSuspendTransformed(
        block: ContextualTransformation<IntermediateOutput, NewIntermediateOutput>
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, NewIntermediateOutput, OutgoingInput> {
        return AIAgentEdgeBuilderIntermediate(
            fromNode = fromNode,
            toNode = toNode,
            forwardOutputComposition = { ctx, output ->
                with(forwardOutputComposition(ctx, output)) {
                    ctx.config.runOnStrategyDispatcher {
                        map { block.invoke(it, ctx) }
                    }
                }
            }
        )
    }

    /**
     * Applies a transformation to the intermediate output of the [AIAgentNode] without requiring additional context.
     *
     * @param block A functional parameter representing the transformation to be applied.
     *              It takes an intermediate output of type [IntermediateOutput] and returns a transformed output of type [NewIntermediateOutput].
     * @return A new instance of `AIAgentEdgeBuilderIntermediate` with the intermediate output type replaced by [NewIntermediateOutput].
     */
    @EdgeTransformationDslMarker
    @JvmName("transformed")
    public infix fun <NewIntermediateOutput> javaNonSuspendTransformed(
        block: SimpleTransformation<IntermediateOutput, NewIntermediateOutput>
    ): AIAgentEdgeBuilderIntermediate<IncomingOutput, NewIntermediateOutput, OutgoingInput> =
        javaNonSuspendTransformed { output, ctx ->
            block.invoke(output)
        }
}

/**
 * ContextualCondition is a functional interface designed to evaluate a condition based on an output value
 * and the given AI agent graph context.
 *
 * @param Output The type of the output value that the condition evaluates.
 */
@JavaAPI
public fun interface ContextualCondition<Output> {
    /**
     * Invokes the contextual condition with the given output and context to evaluate its result.
     *
     * @param output The output data passed to the condition for evaluation.
     * @param context The contextual information required for evaluating the condition,
     *                provided as an implementation of the `AIAgentGraphContextBase` interface.
     * @return A boolean value indicating the result of the condition evaluation.
     *         Returns `true` if the condition is met, otherwise `false`.
     */
    public fun invoke(output: Output, context: AIAgentGraphContextBase): Boolean
}

/**
 * Represents a simple, stateless functional interface that evaluates a condition on a given output.
 * This is particularly tailored for Java compatibility, enabling usage in contexts where Java code interacts with Kotlin.
 *
 * @param Output The type of the input based on which the condition is evaluated.
 */
@JavaAPI
public fun interface SimpleCondition<Output> {
    /**
     * Invokes the condition with the given output object and evaluates whether it satisfies the condition.
     *
     * @param output The output object to be evaluated against the condition.
     * @return `true` if the given output satisfies the condition, `false` otherwise.
     */
    public fun invoke(output: Output): Boolean
}

/**
 * A functional interface representing a transformation operation that processes an input `output` of type [Output]
 * in the context of [AIAgentGraphContextBase] and produces a transformed result of type [NewOutput].
 *
 * The presence of the [JavaAPI] annotation indicates that this interface is optimized for interoperability with Java.
 */
@JavaAPI
public fun interface ContextualTransformation<Output, NewOutput> {
    /**
     * Transforms the given output using the provided AI agent graph context and returns a new output.
     *
     * @param output The original output to be transformed.
     * @param context The context providing the necessary information and functionality
     *                for transformation within the AI agent graph.
     * @return A new output resulting from the transformation.
     */
    public fun invoke(output: Output, context: AIAgentGraphContextBase): NewOutput
}

/**
 * A functional interface representing a transformation operation from a given input type to a new output type.
 *
 * @param Output The type of the input that the transformation operates on.
 * @param NewOutput The type of the output produced by the transformation.
 */
@JavaAPI
public fun interface SimpleTransformation<Output, NewOutput> {
    /**
     * Transforms the given input of type [Output] into an instance of type [NewOutput].
     *
     * @param output The input to be transformed.
     * @return The transformed output of type [NewOutput].
     */
    public fun invoke(output: Output): NewOutput
}
