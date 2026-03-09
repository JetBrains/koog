package ai.koog.agents.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.ext.agent.CriticResult
import ai.koog.serialization.TypeToken

/**
 * Represents a configuration option for determining the output type in a subtask builder process.
 * This sealed interface allows specifying the output either by its class type or by using a tool
 * that generates the required output.
 */
public sealed interface OutputOption<Output : Any> {
    /**
     * Represents an output option that specifies the desired output type
     * using a `Class` object.
     *
     * This class is a concrete implementation of the `OutputOption` interface
     * and is used to define the expected type of output for a given operation.
     *
     * @param Output The type of the output.
     * @property outputClass The `Class` object representing the desired output type.
     */
    public class ByClass<Output : Any>(public val outputClass: Class<Output>) : OutputOption<Output> {
        /**
         * [TypeToken] corresponding to the output type defined by [outputClass].
         */
        public val outputTypeToken: TypeToken = TypeToken.of(outputClass)
    }

    /**
     * Represents an output option determined by a specific tool that provides the output.
     *
     * @param Output The type of output produced by the associated tool.
     * @property finishTool The tool responsible for producing the output of the specified type.
     */
    public class ByFinishTool<Output : Any>(public val finishTool: Tool<*, Output>) : OutputOption<Output>

    /**
     * Represents a verification process applied to an input and produces a result
     * containing feedback and a success status.
     *
     * This class is a specialization of the `OutputOption` interface designed to
     * encapsulate the process of critiquing or verifying an `Input` and providing
     * a `CriticResult` as output. It can be used in scenarios where input validation
     * or assessment is required as part of a larger workflow.
     *
     * @param Input The type of the input to be verified.
     */
    public class Verification<Input> : OutputOption<CriticResult<Input>>
}
