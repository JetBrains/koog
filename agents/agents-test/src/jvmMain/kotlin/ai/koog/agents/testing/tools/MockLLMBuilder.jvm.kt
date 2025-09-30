package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.testing.tools.MockLLMBuilder.ToolCallReceiver
import kotlin.reflect.KFunction

/**
 * Mocks a tool call for the provided tool function and arguments.
 *
 * @param tool The function representing the tool to be mocked.
 * @param args The arguments to be passed to the tool function.
 * @return A `ToolCallReceiver` instance configured with the tool and its arguments.
 */
public fun MockLLMBuilder.mockLLMToolCall(
    tool: KFunction<*>,
    vararg args: Any?,
    toolCallId: String? = null
): ToolCallReceiver<ToolFromCallable.VarArgs> {
    val params = tool.parameters

    val argsMap = (params zip args).associate { (param, arg) ->
        param to arg
    }

    return ToolCallReceiver(
        tool = tool.asTool(),
        args = ToolFromCallable.VarArgs(argsMap),
        toolCallId = toolCallId,
        builder = this
    )
}

/**
 * A class that facilitates mocking of callable tools by defining their behavior based on the received arguments.
 *
 * @param Result The type of result produced by the callable
 * @property callable The callable function being mocked
 * @property builder The instance of [MockLLMBuilder] used to construct the mock
 */
public class MockToolFromCallableReceiver<Result>(
    internal val callable: KFunction<Result>,
    internal val builder: MockLLMBuilder
) {

    /**
     * A builder class for setting up mock tool responses based on a callable's behavior.
     *
     * @param callable The callable (function or method) that the mock tool corresponds to.
     * @param action The action to execute when the mock tool is invoked.
     * @param builder The [MockLLMBuilder] used to configure and manage the mock tool's behavior.
     */
    public class MockToolFromCallableResponseBuilder<Result>(
        internal val callable: KFunction<Result>,
        private val action: suspend () -> Result,
        private val builder: MockLLMBuilder
    ) {
        /**
         * Configures the mock tool to respond when it is called with the specified arguments.
         *
         * @param args The arguments to match for the callable. These are matched against the callable's parameters
         * and are associated with them to form a mapping of parameter names to argument values.
         */
        public fun onArguments(vararg args: Any?) {
            val argsMap = (callable.parameters zip args).associate { (param, arg) -> param to arg }

            builder.addToolAction(
                callable.asTool(),
                { it == ToolFromCallable.VarArgs(argsMap) }
            ) { action() }
        }

        /**
         * Configures a mock behavior for the associated callable to be triggered when the supplied condition
         * is satisfied based on the provided arguments.
         *
         * @param condition A suspendable condition function that takes an instance of [ToolFromCallable.VarArgs]
         * and evaluates to `true` if the action should be executed for the given arguments.
         */
        public fun onArgumentsMatching(condition: suspend (ToolFromCallable.VarArgs) -> Boolean) {
            builder.addToolAction(callable.asTool(), condition) { action() }
        }
    }

    /**
     * Configures the tool to always return the specified result when called.
     *
     * @param response The result to always return when the tool is invoked.
     */
    public infix fun alwaysReturns(response: Result) {
        builder.addToolAction(callable.asTool()) {
            response
        }
    }

    /**
     * Configures a tool to always execute the provided action whenever it is invoked.
     *
     * @param action A suspendable function that returns a `Result`. The provided action is executed every time the tool is called.
     */
    public infix fun alwaysDoes(action: suspend () -> Result) {
        builder.addToolAction(callable.asTool()) {
            action()
        }
    }

    /**
     * Configures the tool to return the specified result when it is invoked.
     *
     * @param result The result that the tool should return upon invocation.
     * @return A [MockToolFromCallableResponseBuilder] instance for further configuration.
     */
    public infix fun returns(result: Result): MockToolFromCallableResponseBuilder<Result> =
        MockToolFromCallableResponseBuilder(callable, { result }, builder)

    /**
     * Configures a tool to execute the provided suspendable action whenever it is invoked.
     *
     * @param action A suspendable function that returns a `Result`. The action is executed each time the tool is called.
     * @return A [MockToolFromCallableResponseBuilder] instance for additional configuration of the mock tool's behavior.
     */
    public infix fun does(action: suspend () -> Result): MockToolFromCallableResponseBuilder<Result> =
        MockToolFromCallableResponseBuilder(callable, action, builder)
}

/**
 * Associates a tool function with the MockLLMBuilder to create a MockToolFromCallableReceiver instance.
 *
 * @param tool The function to be used as the tool in the mock setup.
 * @return A MockToolFromCallableReceiver instance configured with the provided tool function and the current MockLLMBuilder.
 */
public infix fun <Result> MockLLMBuilder.mockTool(tool: KFunction<Result>): MockToolFromCallableReceiver<Result> {
    return MockToolFromCallableReceiver<Result>(tool, this)
}
