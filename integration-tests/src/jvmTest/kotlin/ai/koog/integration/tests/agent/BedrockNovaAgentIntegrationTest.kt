package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.TestUtils.CalculatorTool
import ai.koog.integration.tests.utils.TestUtils.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readAwsSessionTokenFromEnv
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class BedrockNovaAgentIntegrationTest {
    private val systemPrompt = "You are a helpful assistant."
    private val actualToolCalls = mutableListOf<String>()
    private val errors = mutableListOf<Throwable>()
    private val results = mutableListOf<Any?>()

    private val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onAgentCompleted { eventContext ->
            results.add(eventContext.result)
        }
        onAgentExecutionFailed { eventContext ->
            errors.add(eventContext.throwable)
        }
        onToolCallFailed { eventContext ->
            actualToolCalls.add(eventContext.tool.name)
        }
    }

    private fun getExecutor(model: LLModel): SingleLLMPromptExecutor = when (model.provider) {
        is LLMProvider.Bedrock -> simpleBedrockExecutor(
            readAwsAccessKeyIdFromEnv(),
            readAwsSecretAccessKeyFromEnv(),
            readAwsSessionTokenFromEnv()
        )

        else -> throw IllegalArgumentException("Only Bedrock models supported in this test")
    }

    @Test
    fun integration_BedrockNovaAgentShouldCallTools() = runTest {
        val model = BedrockModels.AmazonNovaLite

        Models.assumeAvailable(model.provider)
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry {
            tool(CalculatorTool)
        }

        withRetry {
            val executor = getExecutor(model)

            val agent = AIAgent(
                promptExecutor = executor,
                systemPrompt = systemPrompt + "You MUST use tools.",
                llmModel = model,
                strategy = singleRunStrategy(ToolCalls.PARALLEL),
                temperature = 1.0,
                toolRegistry = toolRegistry,
                maxIterations = 10,
                installFeatures = { install(EventHandler.Feature, eventHandlerConfig) },
            )

            agent.run("How much is 3 times 5?")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for Bedrock Nova model $model")
            assertTrue(
                actualToolCalls.contains(CalculatorTool.name),
                "The ${CalculatorTool.name} tool was not called for Bedrock Nova model $model"
            )
        }
    }
}
