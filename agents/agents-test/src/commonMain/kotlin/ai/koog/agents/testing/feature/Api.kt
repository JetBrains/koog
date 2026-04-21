package ai.koog.agents.testing.feature

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext

/**
 * Configures the Testing feature for graph testing.
 *
 * This function enables graph testing and sets up assertion handling to integrate with the standard
 * Kotlin test framework. It allows you to define graph structure tests, node behavior tests, and
 * edge connection tests within the provided [test] block.

 * @param test A lambda with receiver that configures the testing environment with assertions.
 *
 * @see Testing.Config
 */
public fun Testing.Config.graph(test: Testing.Config.() -> Unit) {
    enableGraphTesting = true

    handleAssertion { assertionResult ->
        when (assertionResult) {
            is AssertionResult.False -> kotlin.test.assertTrue(false, assertionResult.message)

            is AssertionResult.NotEqual -> kotlin.test.assertEquals(
                assertionResult.expected,
                assertionResult.actual,
                assertionResult.message
            )
        }
    }

    test()
}

/**
 * Installs the Testing feature with graph testing enabled and executes the provided test block.
 *
 * This is a convenience function that combines [withTesting] and [graph] to provide a clean API
 * for testing agent graph structures. It allows you to define and validate the structure, behavior,
 * and connections of nodes within an agent's graph.
 *
 * @param test A lambda with receiver that configures the testing environment with assertions.
 *
 * @see Testing.Config
 * @see withTesting
 *
 * Example usage:
 * ```kotlin
 * // Create an agent with testing enabled
 * AIAgent(
 *     promptExecutor = mockLLMApi,
 *     toolRegistry = toolRegistry,
 *     strategy = strategy,
 *     eventHandler = eventHandler,
 *     agentConfig = agentConfig,
 * ) {
 *     // Test the agent's graph structure
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
 *                 askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
 *             }
 *
 *             // Test edge connections
 *             assertEdges {
 *                 askLLM withOutput Message.Assistant("Hello!") goesTo giveFeedback
 *                 askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
 *             }
 *         }
 *     }
 * }
 * ```
 */
public fun <Input, Output> FeatureContext.testGraph(
    name: String,
    test: Testing.Config.SubgraphAssertionsBuilder<Input, Output>.() -> Unit
): Unit =
    withTesting {
        graph {
            verifyStrategy(name) {
                test()
            }
        }
    }
