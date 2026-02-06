package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils
import ai.koog.integration.tests.utils.tools.SimpleCalculatorTool
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.seconds

class AIAgentBuilderIntegrationTest : AIAgentTestBase() {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            AIAgentTestBase.setup()
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderBasicUsage(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant. Be brief.")
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello in one sentence.")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderWithToolRegistry(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        Assumptions.assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry.Companion {
            tool(SimpleCalculatorTool)
        }

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant with access to a calculator tool.")
                    .toolRegistry(toolRegistry)
                    .graphStrategy(singleRunStrategy(ToolCalls.SEQUENTIAL))
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("What is 15 times 3?")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                    result shouldContain "45"
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderWithGraphStrategy(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)
        Assumptions.assumeTrue(model.capabilities.contains(LLMCapability.Tools), "Model $model does not support tools")

        val toolRegistry = ToolRegistry.Companion {
            tool(SimpleCalculatorTool)
        }

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant with access to a calculator tool.")
                    .toolRegistry(toolRegistry)
                    .graphStrategy(singleRunStrategy(ToolCalls.SEQUENTIAL))
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("What is 7 multiplied by 8?")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result.shouldNotBeBlank()
                    result shouldContain "56"
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategyWithLambda(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategy = functionalStrategy<String, String>("echo-strategy") { input ->
                    val response = requestLLM(
                        "User says: $input. Respond with: 'Acknowledged: ' and repeat their message."
                    )
                    when (response) {
                        is Message.Assistant -> response.content
                        else -> "No response"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Hello from test")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        lowercase() shouldContain "hello"
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategySimple(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("summarize") { input ->
            when (val response = requestLLM("Summarize in one sentence: $input")) {
                is Message.Assistant -> response.content
                else -> "Unable to summarize"
            }
        }

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant that provides concise summaries.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run(
                    "Kotlin is a modern programming language that combines " +
                        "object-oriented and functional programming features."
                )

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        length shouldBeGreaterThan 10
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategyWithMultipleSteps(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("multi-step") { input ->
            val ideas = when (val ideasResponse = requestLLM("Give me 2 brief ideas about: $input")) {
                is Message.Assistant -> ideasResponse.content
                else -> "No ideas"
            }

            val refinedIdea = when (
                val response = requestLLM(
                    "Pick the best idea from: $ideas. Explain in one sentence why."
                )
            ) {
                is Message.Assistant -> response.content
                else -> "No refinement"
            }

            "Ideas: $ideas\n\nBest choice: $refinedIdea"
        }

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a creative assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("sustainable energy")

                with(state) {
                    errors.shouldBeEmpty()
                    results.shouldNotBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("Ideas:")
                        shouldContain("Best choice:")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allModels")
    fun integration_BuilderMethodChaining(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .temperature(0.8)
                    .numberOfChoices(1)
                    .maxIterations(15)
                    .toolRegistry(ToolRegistry.EMPTY)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say 'configuration test passed' in different words.")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                }

                agent.agentConfig shouldNotBeNull {
                    maxAgentIterations shouldBe 15
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_BuilderWithMultipleFeatures(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            val eventCallbacks = mutableListOf<String>()

            val agent = AIAgent.builder()
                .promptExecutor(getExecutor(model))
                .llmModel(model)
                .systemPrompt("You are a helpful assistant. Be very brief.")
                .temperature(0.7)
                .install(EventHandler.Feature) { config ->
                    config.onAgentStarting {
                        eventCallbacks.add("agent_started")
                    }
                    config.onAgentCompleted {
                        eventCallbacks.add("agent_completed")
                    }
                    config.onLLMCallStarting {
                        eventCallbacks.add("llm_call_started")
                    }
                    config.onLLMCallCompleted {
                        eventCallbacks.add("llm_call_completed")
                    }
                }
                .build()

            val result = agent.run("Reply with just 'OK'")

            result.shouldNotBeBlank()
            eventCallbacks shouldNotBeNull {
                shouldContain("agent_started")
                shouldContain("agent_completed")
                shouldContain("llm_call_started")
                shouldContain("llm_call_completed")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategyErrorHandling(model: LLModel) = runTest(timeout = 180.seconds) {
        Models.assumeAvailable(model.provider)

        val strategy = functionalStrategy<String, String>("error-handling") { input ->
            when (val response = requestLLM("Process: $input")) {
                is Message.Assistant -> "Success: ${response.content}"
                else -> "Fallback: Unexpected response type"
            }
        }

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategy)
                    .temperature(0.7)
                    .maxIterations(10)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("test input")

                with(state) {
                    errors.shouldBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("Success:")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_BuilderWithTemperatureControl(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val deterministicAgent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant. Answer with exactly '42'.")
                    .temperature(0.0)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = deterministicAgent.run("What is the answer to life, the universe, and everything?")

                with(state) {
                    errors.shouldBeEmpty()
                    result shouldNotBeNull {
                        shouldNotBeBlank()
                        shouldContain("42")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_BuilderWithMaxIterations(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .maxIterations(3)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("List 5 numbers from 1 to 5.")

                with(state) {
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategyWithExceptionHandling(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategyWithErrorHandling = functionalStrategy<String, String>("error-handling") { input ->
                    when (val response = requestLLM(input)) {
                        is Message.Assistant -> response.content
                        else -> "Unexpected response type: ${response::class.simpleName}"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategyWithErrorHandling)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello")

                with(state) {
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_BuilderWithNumberOfChoices(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a creative assistant.")
                    .numberOfChoices(1)
                    .temperature(0.7)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say 'test passed' in a creative way.")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getLatestModels")
    fun integration_FunctionalStrategyWithContextAccess(model: LLModel) = runTest(timeout = 120.seconds) {
        Models.assumeAvailable(model.provider)

        RetryUtils.withRetry {
            runWithTracking { eventHandlerConfig, state ->
                val strategyWithContext = functionalStrategy<String, String>("context-aware") { input ->
                    val agentId = agentId
                    when (val response = requestLLM("Agent $agentId processing: $input")) {
                        is Message.Assistant -> "Processed by $agentId: ${response.content}"
                        else -> "Unexpected response"
                    }
                }

                val agent = AIAgent.builder()
                    .promptExecutor(getExecutor(model))
                    .llmModel(model)
                    .systemPrompt("You are a helpful assistant.")
                    .functionalStrategy(strategyWithContext)
                    .install(EventHandler.Feature, eventHandlerConfig)
                    .build()

                val result = agent.run("Say hello")

                with(state) {
                    errors.shouldBeEmpty()
                    result.shouldNotBeBlank()
                    result shouldContain "Processed by"
                }
            }
        }
    }
}
