package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.TestFeature
import ai.koog.agents.core.feature.mock.TestFeatureMessageProcessor
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertFalse

class GraphAIAgentTest {

    @Test
    fun testGraphAgentFeatureProcessorsClosedAfterRun() = runTest {
        val model = OpenAIModels.Chat.GPT4o

        val agentConfig = AIAgentConfig(
            prompt = prompt(id = "test-chat") {},
            model = model,
            maxAgentIterations = 10,
        )

        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

        val testFeatureMessageProcessor = TestFeatureMessageProcessor()

        val agent = GraphAIAgent(
            id = "test-agent",
            inputType = typeOf<String>(),
            outputType = typeOf<String>(),
            promptExecutor = getMockExecutor { },
            agentConfig = agentConfig,
            strategy = strategy,
        ) {
            install(TestFeature) {
                addMessageProcessor(testFeatureMessageProcessor)
            }
        }

        agent.run("Test input")
        assertFalse(
            testFeatureMessageProcessor.isOpen.value,
            "Feature message processors should be closed after the agent run"
        )
    }
}
