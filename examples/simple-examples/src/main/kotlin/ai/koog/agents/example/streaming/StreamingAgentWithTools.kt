package ai.koog.agents.example.streaming

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResultsStreaming
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.simpleapi.Switch
import ai.koog.agents.example.simpleapi.SwitchTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import kotlinx.coroutines.flow.toList

suspend fun main() {
    val switch = Switch()

    val toolRegistry = ToolRegistry {
        tools(SwitchTools(switch).asTools())
    }

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        val agent = openAiAgent(toolRegistry, executor) {
            handleEvents {
                onToolCallStarting { context ->
                    println("\n🔧 Using ${context.toolName} with ${context.toolArgs}... ")
                }
                onLLMStreamingFrameReceived { context ->
                    when (val frame = context.streamFrame) {
                        is StreamFrame.ReasoningComplete -> println("Reasoning complete:id=${frame.id}\ntext=${frame.content}\nsummary=${frame.summary}")
                        is StreamFrame.TextComplete -> println("Text complete")
                        is StreamFrame.ToolCallComplete -> println("Tool call complete")
                        is StreamFrame.ReasoningDelta -> println("Reasoning delta:id=${frame.id}\ntext=${frame.text}\nsummary=${frame.summary}")
                        is StreamFrame.TextDelta -> println("Text delta:\n${frame.text}")
                        is StreamFrame.ToolCallDelta -> println("Tool call delta:\n${frame.content}")
                        is StreamFrame.End -> println("End")
                    }
                }
                onLLMStreamingFailed {
                    println("❌ Error: ${it.error}")
                }
                onLLMStreamingCompleted {
                    println("")
                }
            }
        }

        println("Streaming chat agent started\nUse /quit to quit\nEnter your message:")
        var input = ""
        while (input != "/quit") {
            input = readln()

            // Example message:
            // Tell me if the switch if on or off. Elaborate on how you will determine that. After that, if it was off, turn it on. Be very verbose in all the steps

            agent.run(input)

            println()
            println("Enter your message:")
        }
    }
}

private fun openAiAgent(
    toolRegistry: ToolRegistry,
    executor: PromptExecutor,
    installFeatures: FeatureContext.() -> Unit = {}
) = AIAgent.builder()
    .graphStrategy { streamingWithToolsStrategy() }
    .promptExecutor(executor)
    .prompt(
        prompt(
            "agent", OpenAIResponsesParams(
                temperature = 1.0,
                reasoning = ReasoningConfig(effort = ReasoningEffort.MEDIUM, summary = ReasoningSummary.AUTO)
            )
        )
        {
            system("You're responsible for running a Switch and perform operations on it by request")
        })
    .llmModel(OpenAIModels.Chat.GPT5_1)
    .toolRegistry(toolRegistry)
    .install(installFeatures)
    .build()

@Suppress("unused")
private fun anthropicAgent(
    toolRegistry: ToolRegistry,
    executor: PromptExecutor,
    installFeatures: FeatureContext.() -> Unit = {}
) = AIAgent(
    promptExecutor = executor,
    strategy = streamingWithToolsStrategy(),
    llmModel = AnthropicModels.Opus_4_6,
    systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
    temperature = 0.0,
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)

fun streamingWithToolsStrategy() = strategy("streaming_loop") {
    // Streaming node: appends the user message to the prompt, issues a streaming LLM call (so the
    // onLLMStreamingFrameReceived / onLLMStreamingFailed / onLLMStreamingCompleted event handlers
    // fire frame-by-frame), then collapses the stream into a Message.Assistant for downstream
    // tool-call / text-part dispatch.
    val nodeCallLLM by nodeLLMRequestStreaming().transform {
        it.toList().toMessageResponse()
    }

    val nodeSendToolResults by nodeLLMSendToolResultsStreaming().transform {
        it.toList().toMessageResponse()
    }

    val executeTools by nodeExecuteTools(parallel = true)

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo executeTools onToolCalls { true })
    edge(executeTools forwardTo nodeSendToolResults)
    edge(nodeSendToolResults forwardTo executeTools onToolCalls { true })
    edge(nodeSendToolResults forwardTo nodeFinish onTextMessage { true })
    edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
}
