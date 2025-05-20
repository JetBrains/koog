package ai.jetbrains.code.integration.tests

import ai.jetbrains.code.integration.tests.TestUtils.readTestAnthropicKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestGoogleAIKeyFromEnv
import ai.jetbrains.code.integration.tests.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.simpleChatAgent
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.local.features.eventHandler.feature.EventHandler
import ai.koog.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.AfterTest
import kotlin.test.assertTrue

@Disabled("JBAI-13981, JBAI-13980, JBAI-14070, JBAI-14071")
class SimpleAgentIntegrationTest {
    val systemPrompt = """
            You are a helpful assistant. 
            You MUST use tools to communicate to the user.
            You MUST NOT communicate to the user without tools.
        """.trimIndent()

    companion object {
        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Models.openAIModels()
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Models.anthropicModels()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
        }
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCall = { tool, args ->
            println("Tool called: tool ${tool.name}, args $args")
            actualToolCalls.add(tool.name)
        }

        onAgentRunError = { strategyName, throwable ->
            errors.add(throwable)
        }

        onAgentFinished = { strategyName, result ->
            results.add(result)
        }
    }

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<String?>()

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
    }


    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels")
    fun integration_simpleChatAgentShouldCallDefaultTools(model: LLModel) = runBlocking {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleChatAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Please exit.")
        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels")
    fun integration_simpleChatAgentShouldCallCustomTools(model: LLModel) = runBlocking {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry.Companion {
            tool(SayToUser)
        }

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleChatAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            maxIterations = 10,
            toolRegistry = toolRegistry,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Hello, how are you?")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
        assertTrue(
            actualToolCalls.contains("__say_to_user__"),
            "The __say_to_user__ tool was not called for model $model"
        )
    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels")
    fun integration_simpleSingleRunAgentShouldNotCallToolsByDefault(model: LLModel) = runBlocking {
        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleSingleRunAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Repeat what I say: hello, I'm good.")

        // by default, simpleSingleRunAgent has no tools underneath
        assertTrue(actualToolCalls.isEmpty(), "No tools should be called for model $model")

    }

    @ParameterizedTest
    @MethodSource("openAIModels", "anthropicModels")
    fun integration_simpleSingleRunAgentShouldCallCustomTool(model: LLModel) = runBlocking {
        assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry.Companion {
            tool(SayToUser)
        }

        val executor = when (model.provider) {
            is LLMProvider.Anthropic -> simpleAnthropicExecutor(readTestAnthropicKeyFromEnv())
            is LLMProvider.Google -> simpleGoogleAIExecutor(readTestGoogleAIKeyFromEnv())
            else -> simpleOpenAIExecutor(readTestOpenAIKeyFromEnv())
        }

        val agent = simpleSingleRunAgent(
            executor = executor,
            systemPrompt = systemPrompt,
            llmModel = model,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Write a Kotlin function to calculate factorial.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model $model")
        assertTrue(
            actualToolCalls.contains("__say_to_user__"),
            "The __say_to_user__ tool was not called for model $model"
        )
    }
}
