package ai.grazie.code.agents.testing.feature

import ai.grazie.code.agents.core.agent.AIAgentBase.FeatureContext

fun Testing.Config.graph(test: Testing.Config.() -> Unit) {
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

suspend fun FeatureContext.testGraph(test: Testing.Config.() -> Unit) = withTesting {
    graph(test)
}
